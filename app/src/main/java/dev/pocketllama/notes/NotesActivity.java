package dev.pocketllama.notes;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import dev.pocketllama.I18n;
import dev.pocketllama.UiHost;
import dev.pocketllama.security.GateSession;
import dev.pocketllama.security.SecureActivity;
import org.json.JSONObject;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Local-only editor. Every input is staged natively; pause/back drain accepted edits. */
public final class NotesActivity extends SecureActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();
    private final Object draftLock = new Object();
    private WebView web;
    private FrameLayout root;
    private NoteStore store;
    private volatile boolean ready, destroyed;
    private volatile String selection = "";
    private Editing editing;
    private boolean scheduled;
    private static final class Editing {
        final String id, token = UUID.randomUUID().toString();
        final NoteDraft draft;
        long revision;
        Editing(JSONObject note) throws Exception {
            id = note.getString("id"); revision = note.getLong("revision");
            draft = new NoteDraft(note.getString("title"), note.getString("body"));
        }
    }
    @Override protected void onSecureCreate(Bundle state) {
        selection = state == null ? "" : state.getString("note", "");
        root = new FrameLayout(this); web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);
        UiHost.attach(this, root, web); web.addJavascriptInterface(new Bridge(), "Notes");
        web.loadUrl(UiHost.ORIGIN + "notes.html");
    }
    public final class Bridge {
        @JavascriptInterface public String language() { return I18n.language(); }
        @JavascriptInterface public String translations() { return I18n.dictionary(); }
        @JavascriptInterface public String appearance() { return UiHost.appearance(NotesActivity.this); }
        @JavascriptInterface public void ready() {
            if (destroyed || !GateSession.isOpen()) return;
            ready = true;
            io.execute(() -> { try {
                if (store == null) store = new NoteStore(NotesActivity.this);
                store.getWritableDatabase(); emit("boot", object("selection", selection));
            } catch (Exception e) { emit("error", object("message", useful(e))); } });
        }
        // This receives each edit immediately; it never relies on paused JavaScript timers.
        @JavascriptInterface public String stage(String token, long sequence, String title, String body) {
            if (destroyed || !GateSession.isOpen()) return I18n.t("Введите код входа");
            try {
                synchronized (draftLock) {
                    if (editing == null || !editing.token.equals(token)) throw new IOException("Редактор уже закрыт");
                    editing.draft.stage(sequence, title, body);
                    if (!scheduled) { scheduled = true; io.schedule(() -> {
                        synchronized (draftLock) { scheduled = false; }
                        try { flushDraft(); } catch (Exception e) { String activeToken; synchronized (draftLock) { activeToken = editing == null ? "" : editing.token; } emit("saveError", object("token", activeToken, "message", useful(e))); }
                    }, 200, TimeUnit.MILLISECONDS); }
                }
                return "";
            } catch (Exception e) { return useful(e); }
        }
        @JavascriptInterface public void call(String request, String action, String payload) {
            if (destroyed) return;
            if (request == null || request.length() > 80 || payload == null || payload.length() > 1600000) return;
            io.execute(() -> { try {
                GateSession.requireOpen();
                if (store == null) throw new IOException("Хранилище ещё открывается");
                reply(request, act(action, new JSONObject(payload)));
            } catch (Exception e) { emit("reply", object("request", request, "error", useful(e))); } });
        }
    }
    private JSONObject act(String action, JSONObject args) throws Exception {
        switch (action) {
            case "list": flushDraft(); return store.list(args.optString("query"), args.optBoolean("trash"), args.optInt("offset"), args.optInt("limit", 50));
            case "create": flushDraft(); return edit(store.create());
            case "open": {
                flushDraft(); JSONObject note = store.get(args.getString("id"));
                if (note.optLong("deleted") > 0) return note;
                return edit(note);
            }
            case "flush": flushDraft(); return object("ok", true);
            case "close": flushDraft(); synchronized (draftLock) { editing = null; selection = ""; } return object("ok", true);
            case "pin": flushDraft(); return store.pin(args.getString("id"), args.getBoolean("pinned"));
            case "trash": case "restore": {
                flushDraft(); String id = args.getString("id"); store.trash(id, action.equals("trash"));
                synchronized (draftLock) { if (editing != null && editing.id.equals(id)) { editing = null; selection = ""; } }
                return object("ok", true);
            }
            case "purge": store.purge(args.getString("id")); return object("ok", true);
            case "back": flushDraft(); main.post(this::finish); return object("ok", true);
            default: throw new IOException("Неизвестное действие");
        }
    }
    private JSONObject edit(JSONObject note) throws Exception {
        synchronized (draftLock) { editing = new Editing(note); selection = editing.id; return note.put("token", editing.token); }
    }
    private void flushDraft() throws Exception {
        Editing e; synchronized (draftLock) { e = editing; }
        if (e == null) return;
        NoteDraft.Snapshot snapshot = e.draft.pending(); if (snapshot == null) return;
        JSONObject saved = store.save(e.id, e.revision, snapshot.title, snapshot.body);
        e.revision = saved.getLong("revision"); e.draft.acknowledge(snapshot.sequence);
        emit("saved", object("token", e.token, "sequence", snapshot.sequence, "updated", saved.getLong("updated")));
    }
    private static JSONObject object(Object... pairs) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < pairs.length; i += 2) result.put((String)pairs[i], pairs[i + 1]); }
        catch (Exception e) { throw new IllegalArgumentException(e); }
        return result;
    }
    private static String useful(Exception e) {
        return I18n.t(e instanceof IOException || e instanceof IllegalArgumentException ? e.getMessage() : "Не удалось сохранить заметку. Ваш текст остаётся в редакторе. Повторите сохранение");
    }
    private void reply(String request, JSONObject value) { emit("reply", object("request", request, "value", value)); }
    private void emit(String type, JSONObject data) {
        if (destroyed || !ready) return;
        String event = object("type", type, "data", data).toString();
        main.post(() -> { if (!destroyed && GateSession.isOpen()) web.evaluateJavascript("window.NoteEvents&&window.NoteEvents(" + event + ")", null); });
    }
    @Override protected void onResume() {
        super.onResume(); if (!secureInitialized() || !GateSession.isOpen()) return;
        if (web != null) { web.onResume(); UiHost.colors(this, root, web); UiHost.syncLanguage(web); }
        if (ready) io.execute(() -> { try { flushDraft(); } catch (Exception e) { emit("saveError", object("message", useful(e))); } });
        emit("resume", new JSONObject());
    }
    @Override protected void onPause() {
        if (secureInitialized()) {
            // Capture already accepted bridge input before Android can stop the process.
            try { io.submit(() -> { try { flushDraft(); } catch (Exception e) { emit("saveError", object("message", useful(e))); } }).get(700, TimeUnit.MILLISECONDS); }
            catch (Exception ignored) { /* A slow encrypted write remains queued; no draft is discarded. */ }
            if (web != null) web.onPause();
        }
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("note", selection); }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration); if (web != null) { UiHost.colors(this, root, web); web.getSettings().setTextZoom(Math.round(100 * configuration.fontScale)); }
    }
    @Override public void onBackPressed() {
        if (web == null || !GateSession.isOpen()) { lockNow(); return; }
        web.evaluateJavascript("window.NoteBack&&window.NoteBack()", null);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        io.execute(() -> { try { flushDraft(); } catch (Exception ignored) { } synchronized (draftLock) { editing = null; } if (store != null) store.close(); });
        io.shutdown();
        if (web != null) { web.removeJavascriptInterface("Notes"); web.destroy(); }
        super.onDestroy();
    }
}
