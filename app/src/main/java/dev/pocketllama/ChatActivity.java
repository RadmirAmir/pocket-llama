package dev.pocketllama;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import dev.pocketllama.core.ServerConfig;
import dev.pocketllama.core.TextAttachment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local UI asset with a native HTTP/file bridge; no untrusted page gets bridge access. */
public final class ChatActivity extends dev.pocketllama.security.SecureActivity {
    private static final String PAGE = UiHost.ORIGIN + "chat.html";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final AtomicBoolean generating = new AtomicBoolean(), checking = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private volatile ChatApi request;
    private volatile dev.pocketllama.rag.RagManager rag;
    private volatile dev.pocketllama.search.WebSearch webSearch;
    // Consent is native, bound to this unlocked Activity, and never restored from JS/history.
    private volatile boolean webConnected;
    private volatile long webConsentEpoch=-1;
    private final AtomicBoolean ragOperation=new AtomicBoolean();
    private volatile boolean destroyed, pageReady, vision;
    private WebView web; private FrameLayout root;
    private ConfigStore store;
    private boolean chatBooted;private final java.util.ArrayList<Runnable> waitingResults=new java.util.ArrayList<>();
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshState(); handler.postDelayed(this, 4000); }
    };

    @Override protected void onSecureCreate(Bundle state) {
        
        store = new ConfigStore(this);
        root = new FrameLayout(this);
        web = new WebView(this); root.addView(web,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        UiHost.attach(this,root,web); web.addJavascriptInterface(new Bridge(),"Pocket");
        web.loadUrl(PAGE);
    }

    public final class Bridge {
        @JavascriptInterface public String language() { return I18n.language(); }
        @JavascriptInterface public String translations() { return I18n.dictionary(); }
        @JavascriptInterface public void ready() {if(!dev.pocketllama.security.GateSession.isOpen())return;
            pageReady = true;
            io.execute(() -> {
                try { JSONObject history=new JSONObject(ChatStore.read(getApplicationContext()));runOnUiThread(()->{if(destroyed)return;emit("boot",history);chatBooted=true;java.util.ArrayList<Runnable> pending=new java.util.ArrayList<>(waitingResults);waitingResults.clear();for(Runnable result:pending)result.run();}); }
                catch (Exception e) { fail(e); }
                refreshState();
                ragStatus();
                emit("webState",object("enabled",false));
            });
        }
        @JavascriptInterface public void refresh() {if(!dev.pocketllama.security.GateSession.isOpen())return; refreshState(); }
        @JavascriptInterface public String appearance() { return UiHost.appearance(ChatActivity.this); }
        @JavascriptInterface public void report(String messageId) {if(!dev.pocketllama.security.GateSession.isOpen()||messageId==null||messageId.length()>100)return;runOnUiThread(()->{try{FeedbackReporter.show(ChatActivity.this,io,messageId);}catch(Exception e){fail(e);}});}
        @JavascriptInterface public void back() { runOnUiThread(() -> finish()); }
        @JavascriptInterface public void settings() {if(!dev.pocketllama.security.GateSession.isOpen())return; runOnUiThread(() -> {
            startActivity(new Intent(ChatActivity.this,dev.pocketllama.voice.VoiceActivity.class).putExtra("screen","settings").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish();
        }); }
        @JavascriptInterface public void startModel() {if(!dev.pocketllama.security.GateSession.isOpen())return; runOnUiThread(() -> {
            try { if(dev.pocketllama.rag.RagManager.busy())throw new IOException(I18n.t("База знаний уже обновляется"));if(dev.pocketllama.voice.VoiceState.busy||dev.pocketllama.photo.PhotoStore.busy(ChatActivity.this))throw new IOException(dev.pocketllama.I18n.t("Сначала завершите запись или обработку"));store.load().validate();startForegroundService(new Intent(ChatActivity.this,InferenceService.class).setAction(InferenceService.START));refreshState(); }
            catch(Exception e){fail(e);}
        }); }
        @JavascriptInterface public void pick(boolean imageOnly) {if(!dev.pocketllama.security.GateSession.isOpen())return;
            runOnUiThread(() -> {
                if (generating.get()) return;
                if (imageOnly && !vision) {
                    emit("error", object("message", dev.pocketllama.I18n.t("Изображения пока недоступны. В настройках выберите vision-модель и совместимый mmproj, затем запустите API.")));
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(imageOnly ? "image/*" : "*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, 10);
            });
        }
        @JavascriptInterface public void save(String history) {
            try { ChatStore.save(getApplicationContext(), history); }
            catch (Exception e) { fail(e); }
        }
        @JavascriptInterface public void copy(String text) {if(!dev.pocketllama.security.GateSession.isOpen())return;
            if (text.length() > 1024 * 1024) return;
            runOnUiThread(() -> {
                if(!dev.pocketllama.security.GateSession.isOpen())return;
                ClipData clip=ClipData.newPlainText(dev.pocketllama.I18n.t("Ответ модели"),text);android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);getSystemService(ClipboardManager.class).setPrimaryClip(clip);
            });
        }
        @JavascriptInterface public void newChat() {if(!dev.pocketllama.security.GateSession.isOpen())return;
            runOnUiThread(() -> {
                if (generating.get()) return;
                new AlertDialog.Builder(ChatActivity.this).setTitle(dev.pocketllama.I18n.t("Начать новый диалог?"))
                        .setMessage(dev.pocketllama.I18n.t("Текущая история и вложения будут очищены."))
                        .setNegativeButton(dev.pocketllama.I18n.t("Отмена"), null).setPositiveButton(dev.pocketllama.I18n.t("Новый диалог"), (dialog, which) -> {disconnectWeb();emit("reset", new JSONObject());}).show();
            });
        }
        @JavascriptInterface public void stop() {
            stopRequested.set(true);
            dev.pocketllama.search.WebSearch online=webSearch;if(online!=null)online.cancel();
            dev.pocketllama.rag.RagManager search=rag;if(search!=null)search.cancel();
            ChatApi active = request;
            if (active != null) active.cancel();
        }
        @JavascriptInterface public void setWebSearch(boolean enable) {
            if(!dev.pocketllama.security.GateSession.isOpen()||destroyed)return;
            runOnUiThread(()->{
                if(!enable){disconnectWeb();return;}
                if(generating.get())return;
                long epoch=dev.pocketllama.security.GateSession.epoch();
                new AlertDialog.Builder(ChatActivity.this).setTitle(I18n.t("Включить интернет-поиск?"))
                    .setMessage(I18n.t("Поиск выполняется в фоне. Внешние сайты получают поисковые запросы и ваш IP-адрес. Модель может включить в запрос личные сведения из переписки, вложений и подключённой базы знаний. Включайте поиск только для данных, которые готовы отправить в интернет."))
                    .setNegativeButton(I18n.t("Отмена"),(d,w)->emit("webState",object("enabled",false)))
                    .setOnCancelListener(d->emit("webState",object("enabled",false)))
                    .setPositiveButton(I18n.t("Включить поиск"),(d,w)->{
                        if(destroyed||!dev.pocketllama.security.GateSession.isOpen()||epoch!=dev.pocketllama.security.GateSession.epoch())return;
                        webConsentEpoch=epoch;webConnected=true;emit("webState",object("enabled",true));
                    }).show();
            });
        }
        @JavascriptInterface public void openWebSource(String url) {
            if(!dev.pocketllama.security.GateSession.isOpen()||!dev.pocketllama.search.WebQuery.publicUrl(url))return;
            runOnUiThread(()->{if(!dev.pocketllama.security.GateSession.isOpen()||destroyed)return;
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(Exception e){fail(new IOException(I18n.t("Не удалось открыть ссылку")));}
            });
        }
        @JavascriptInterface public void rag(String action) {
            if(!dev.pocketllama.security.GateSession.isOpen()||destroyed)return;
            if("status".equals(action)){io.execute(ChatActivity.this::ragStatus);return;}
            if("cancel".equals(action)){dev.pocketllama.rag.RagManager active=rag;if(active!=null)active.cancel();return;}
            if(generating.get()||ragOperation.get())return;
            if("download".equals(action))io.execute(()->{try{
                String terms=dev.pocketllama.rag.RagAssets.terms(ChatActivity.this);long epoch=dev.pocketllama.security.GateSession.epoch();
                runOnUiThread(()->{if(destroyed||!dev.pocketllama.security.GateSession.isOpen()||epoch!=dev.pocketllama.security.GateSession.epoch())return;
                    android.widget.TextView text=new android.widget.TextView(ChatActivity.this);text.setText(terms);text.setTextSize(14);int pad=Math.round(20*getResources().getDisplayMetrics().density);text.setPadding(pad,pad,pad,pad);android.widget.ScrollView scroll=new android.widget.ScrollView(ChatActivity.this);scroll.addView(text);
                    new AlertDialog.Builder(ChatActivity.this).setTitle(I18n.t("EmbeddingGemma · условия модели")).setView(scroll).setNegativeButton(I18n.t("Отмена"),null).setPositiveButton(I18n.t("Принять и скачать"),(d,w)->ragWork("download")).show();
                });
            }catch(Exception e){fail(e);}});
            else if("build".equals(action))ragWork("build");
            else if("clear".equals(action))runOnUiThread(()->new AlertDialog.Builder(ChatActivity.this).setTitle(I18n.t("Удалить поисковый индекс?"))
                .setMessage(I18n.t("Исходные заметки, записи и документы сохранятся. При следующем поиске индекс можно построить заново."))
                .setNegativeButton(I18n.t("Отмена"),null).setPositiveButton(I18n.t("Удалить индекс"),(d,w)->ragWork("clear")).show());
        }
        @JavascriptInterface public void openRagSource(String value) {
            if(!dev.pocketllama.security.GateSession.isOpen()||value==null||value.length()>16384||generating.get()||ragOperation.get())return;
            io.execute(()->{try{
                long epoch=dev.pocketllama.security.GateSession.epoch();JSONObject hit=new JSONObject(value);String id=hit.getString("sourceId"),kind=hit.getString("kind");if(!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new IOException("Invalid source");
                Intent intent;
                if(kind.equals("note")){try(dev.pocketllama.notes.NoteStore notes=new dev.pocketllama.notes.NoteStore(ChatActivity.this)){if(notes.get(id).optLong("deleted")!=0)throw new IOException(I18n.t("Заметка не найдена"));}intent=new Intent(ChatActivity.this,dev.pocketllama.notes.NotesActivity.class).putExtra("note",id);}
                else if(kind.equals("voice")){try(dev.pocketllama.voice.VoiceStore voices=new dev.pocketllama.voice.VoiceStore(ChatActivity.this)){voices.get(id);}JSONObject location=hit.optJSONObject("location");intent=new Intent(ChatActivity.this,dev.pocketllama.voice.VoiceActivity.class).putExtra("id",id).putExtra("sourceStart",location==null?0:Math.max(0,location.optLong("start"))).putExtra("sourceSummary",location!=null&&location.optString("section").equals("summary"));}
                else if(kind.equals("ocr")){new dev.pocketllama.photo.PhotoStore(ChatActivity.this).get(id);intent=new Intent(ChatActivity.this,dev.pocketllama.photo.PhotoActivity.class).putExtra("id",id).putExtra("page",id);}
                else throw new IOException("Invalid source type");
                runOnUiThread(()->{if(!destroyed&&dev.pocketllama.security.GateSession.isOpen()&&epoch==dev.pocketllama.security.GateSession.epoch())startActivity(intent);});
            }catch(Exception e){fail(e);}});
        }
        @JavascriptInterface public void send(String id, String json) {if(!dev.pocketllama.security.GateSession.isOpen())return;
            if (id.length() > 100 || json.length() > 12 * 1024 * 1024) {
                emit("failed", object("id", id, "message", dev.pocketllama.I18n.t("Слишком большой запрос"))); return;
            }
            if (ragOperation.get() || !generating.compareAndSet(false, true)) {
                emit("failed", object("id", id, "message", dev.pocketllama.I18n.t("Предыдущий ответ ещё завершается"))); return;
            }
            stopRequested.set(false);
            io.execute(() -> sendMessage(id, json));
        }
    }

    private void disconnectWeb(){webConnected=false;webConsentEpoch=-1;dev.pocketllama.search.WebSearch task=webSearch;if(task!=null)task.cancel();emit("webState",object("enabled",false));}
    private void ragStatus(){try{emit("ragState",dev.pocketllama.rag.RagManager.status(this));}catch(Exception e){fail(e);}}
    private void ragWork(String action) {
        if(!dev.pocketllama.security.GateSession.isOpen()||destroyed||generating.get()||!ragOperation.compareAndSet(false,true))return;
        emit("ragState",object("working",true,"stage",action,"detail",I18n.t("Подготавливаю базу знаний…")));
        io.execute(()->{dev.pocketllama.rag.RagManager task=new dev.pocketllama.rag.RagManager(this,()->{if(destroyed)throw new InterruptedException();},s->emit("ragState",s));rag=task;
            try {if(action.equals("download")){dev.pocketllama.rag.RagAssets.accept(this);task.download();}else if(action.equals("build"))task.build();else task.clear();}
            catch(Exception e){if(dev.pocketllama.security.GateSession.isOpen())emit("ragState",object("working",false,"error",useful(e)));}
            finally{if(rag==task)rag=null;ragOperation.set(false);ragStatus();refreshState();}
        });
    }

    private void refreshState() {
        if (!pageReady || destroyed || !checking.compareAndSet(false, true)) return;
        io.execute(() -> {
            try {
                ServerConfig config = store.load();
                JSONObject state = object("ready", false, "active", RuntimeState.active, "vision", false,
                        "status", RuntimeState.status, "model", config.modelName, "projector", config.projectorName,
                        "context", config.contextPerSlot, "backend", RuntimeState.active && !RuntimeState.effectiveBackend.isEmpty()
                                ? RuntimeState.effectiveBackend : config.backend, "cpu_profile", RuntimeState.cpuProfile);
                if (RuntimeState.ready) {
                    try {
                        JSONObject caps = new ChatApi(config.port, store.apiKey()).capabilities();
                        state.put("ready", true).put("vision", caps.optBoolean("vision", false))
                                .put("context", caps.optInt("context", config.contextPerSlot));
                    } catch (Exception ignored) { state.put("status", dev.pocketllama.I18n.t("Соединение с API недоступно")); }
                }
                vision = state.optBoolean("vision", false);
                emit("state", state);
            } catch (Exception e) { fail(e); }
            finally { checking.set(false); }
        });
    }

    private void sendMessage(String id, String json) {
        ChatApi client = null;
        final JSONObject[] outcome = new JSONObject[1];
        final String[] outcomeType = new String[]{"complete"};
        try {
            if (dev.pocketllama.voice.VoiceState.busy||dev.pocketllama.photo.PhotoStore.busy(ChatActivity.this)) throw new IOException(dev.pocketllama.I18n.t("Сначала завершите запись или конспект."));
            if (!RuntimeState.ready) throw new IOException(dev.pocketllama.I18n.t("API ещё не запущен. Откройте настройки модели и запустите сервер."));
            JSONObject input = new JSONObject(json);
            final long epoch=dev.pocketllama.security.GateSession.epoch();
            dev.pocketllama.rag.RagSources.Check access=()->{if(destroyed||stopRequested.get()||!dev.pocketllama.security.GateSession.isOpen()||epoch!=dev.pocketllama.security.GateSession.epoch())throw new InterruptedException("Ответ остановлен");};
            boolean online=input.optBoolean("web",false);
            if(online&&(!webConnected||webConsentEpoch!=epoch))throw new IOException(I18n.t("Сначала включите интернет-поиск в инструментах чата"));
            boolean personal=input.optBoolean("rag",false);
            ServerConfig config = store.load();
            client = new ChatApi(config.port, store.apiKey());
            request = client;
            access.run();
            JSONObject caps = client.capabilities();
            JSONObject body = ChatApi.request(input.getJSONArray("messages"), input.optBoolean("thinking", false),
                    input.optInt("max_tokens", 512), caps.optBoolean("vision", false),RuntimeState.chatProfile);
            int context=caps.optInt("context",config.contextPerSlot);
            final boolean news=dev.pocketllama.agent.SearchIntent.from(body).news;
            if(personal||online)body.getJSONArray("messages").getJSONObject(0).put("content",body.getJSONArray("messages").getJSONObject(0).getString("content")+"\nCurrent device date: "+java.time.LocalDate.now()+". Time zone: "+java.time.ZoneId.systemDefault()+". Use this date for today/current searches; do not invent current events.");
            if(personal||online)body=new dev.pocketllama.agent.ChatAgent(client,new dev.pocketllama.agent.ChatAgent.Host(){
                public void check()throws Exception {access.run();if(online&&(!webConnected||webConsentEpoch!=epoch))throw new InterruptedException("Ответ остановлен");}
                public void event(String type,JSONObject data)throws Exception {check();data.put("id",id);emit(type,data);}
                public String language(){return I18n.language();}
                public JSONObject call(String tool,String query)throws Exception {
                    check();
                    if(tool.equals(dev.pocketllama.agent.ChatAgent.KNOWLEDGE)&&personal){
                        dev.pocketllama.search.WebSearch search=webSearch;if(search!=null){webSearch=null;search.close();}
                        dev.pocketllama.rag.RagManager task=new dev.pocketllama.rag.RagManager(ChatActivity.this,this::check,s->{try{s.put("id",id);}catch(Exception ignored){}emit("ragProgress",s);});rag=task;
                        try {
                            JSONObject reply=new dev.pocketllama.rag.RagMcp(task::search).handle(dev.pocketllama.rag.RagMcp.call(query),true);check();
                            if(!RuntimeState.ready)throw new IOException(I18n.t("Модель чата ещё загружается. Повторите сообщение после запуска"));
                            if(reply.has("error"))throw new IOException(reply.getJSONObject("error").optString("message"));JSONObject result=reply.getJSONObject("result");
                            if(result.optBoolean("isError"))throw new IOException(result.getJSONArray("content").getJSONObject(0).optString("text"));return result.getJSONObject("structuredContent");
                        }finally{if(rag==task)rag=null;}
                    }
                    if(tool.equals(dev.pocketllama.agent.ChatAgent.WEB)&&online){
                        dev.pocketllama.search.WebSearch task=webSearch;
                        if(task==null){task=new dev.pocketllama.search.WebSearch(ChatActivity.this,this::check);webSearch=task;}
                        return task.search(query,I18n.language(),news?"news":"general");
                    }
                    throw new IOException("Tool is disabled");
                }
            },personal,online).prepare(body,context);
            dev.pocketllama.search.WebSearch search=webSearch;if(search!=null){webSearch=null;search.close();}
            access.run();
            RuntimeState.log(dev.pocketllama.I18n.t("Чат запрос: профиль=")+RuntimeState.chatProfile.id+" · thinking="+input.optBoolean("thinking",false)
                    +" · min_p="+body.optDouble("min_p")+" · temperature="+body.optDouble("temperature")
                    +" · top_p="+body.optDouble("top_p")+" · top_k="+body.optInt("top_k")
                    +" · reasoning_budget="+body.optInt("reasoning_budget_tokens",-1)+" · max_tokens="+body.optInt("max_tokens"));
            client.stream(body, new ChatApi.Listener() {
                @Override public void chunk(String content, String reasoning) {
                    if(!dev.pocketllama.security.GateSession.isOpen()||epoch!=dev.pocketllama.security.GateSession.epoch()){ChatApi active=request;if(active!=null)active.cancel();return;}
                    if (!content.isEmpty() || !reasoning.isEmpty())
                        emit("chunk", object("id", id, "content", content, "reasoning", reasoning));
                }
                @Override public void complete(String reason, JSONObject usage) {
                    outcome[0] = object("id", id, "reason", reason, "usage", usage);
                }
                @Override public void progress(JSONObject prompt, JSONObject metrics) {
                    emit("progress", object("id", id, "prompt", prompt, "metrics", metrics));
                }
            });
        } catch (Exception e) {
            if (stopRequested.get() || client != null && client.isCancelled()) {
                outcomeType[0] = "complete";
                outcome[0] = object("id", id, "reason", "cancelled", "usage", new JSONObject());
            } else {
            outcomeType[0] = "failed";
            String detail = useful(e);
            if (dev.pocketllama.core.BackendEnvironment.pipelineFailure(detail)) {
                RuntimeState.log(dev.pocketllama.I18n.t("Ошибка Vulkan в чате: ") + detail);
                detail = dev.pocketllama.I18n.t("Драйвер Vulkan не смог скомпилировать вычислительное ядро. ")
                        + dev.pocketllama.I18n.t("Остановите API в настройках. Попробуйте Vulkan с включённым режимом совместимости ")
                        + dev.pocketllama.I18n.t("или выберите CPU, затем запустите API и повторите сообщение.\n") + detail;
            }
            outcome[0] = object("id", id, "message", detail);
            }
        } finally {
            dev.pocketllama.search.WebSearch search=webSearch;if(search!=null){webSearch=null;search.close();}
            if (client != null && outcome[0] != null) {
                JSONObject metrics = client.statistics();
                try { outcome[0].put("metrics", metrics); } catch (Exception ignored) { }
                RuntimeState.log(dev.pocketllama.I18n.t("Чат: ") + outcomeType[0] + " · " + metrics.toString());
            }
            if (request == client) request = null;
            generating.set(false);
            if (outcome[0] != null) emit(outcomeType[0], outcome[0]);
            ragStatus();refreshState();
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);if(deferResult(()->onActivityResult(code,result,data)))return;
        if (code != 10 || result != RESULT_OK || data == null) return;
        if(!chatBooted){waitingResults.add(()->onActivityResult(code,result,data));return;}
        java.util.List<Uri> uris = new java.util.ArrayList<>();
        if (data.getClipData() != null) {
            ClipData clip = data.getClipData();
            if (clip.getItemCount() > 4) { emit("error", object("message", dev.pocketllama.I18n.t("Можно выбрать до четырёх вложений за раз"))); return; }
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        emit("attachmentBusy", object("busy", true));
        io.execute(() -> {
            try {
                for (Uri uri : uris) {
                    try { emit("attachment", attachment(uri)); }
                    catch (Exception e) { fail(e); }
                }
            } finally { emit("attachmentBusy", object("busy", false)); }
        });
    }

    private JSONObject attachment(Uri uri) throws Exception {
        String name = dev.pocketllama.I18n.t("Вложение"), mime = getContentResolver().getType(uri);
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
        }
        if (name.length() > 180) name = name.substring(0, 180);
        String lower = name.toLowerCase(Locale.ROOT);
        boolean image = (mime != null && mime.startsWith("image/")) || lower.matches(".*\\.(png|jpg|jpeg|webp|heic|heif|avif|gif|bmp)$");
        byte[] bytes;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException(dev.pocketllama.I18n.t("Не удалось открыть файл"));
            bytes = TextAttachment.readLimited(input, image ? 20 * 1024 * 1024 : TextAttachment.MAX_BYTES);
        }
        if (bytes.length >= 8) {
            image |= (bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8)
                    || (bytes[0] == (byte)0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')
                    || (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && lower.endsWith(".webp"));
        }
        JSONObject result = object("id", UUID.randomUUID().toString(), "name", name, "bytes", bytes.length);
        if (!image) return result.put("kind", "text").put("text", TextAttachment.decode(bytes));
        if (!vision) throw new IOException(dev.pocketllama.I18n.t("Запущенная модель не принимает картинки. Загрузите совместимый mmproj и перезапустите API."));
        Bitmap decoded = null, flattened = null;
        try {
            decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), (decoder, info, source) -> {
                int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                if (width <= 0 || height <= 0 || (long) width * height > 500_000_000L)
                    throw new IllegalArgumentException(dev.pocketllama.I18n.t("Неподдерживаемый размер изображения"));
                double scale = Math.min(1.0, 1024.0 / Math.max(width, height));
                decoder.setTargetSize(Math.max(1, (int)Math.round(width * scale)), Math.max(1, (int)Math.round(height * scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            flattened = Bitmap.createBitmap(decoded.getWidth(), decoded.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(flattened); canvas.drawColor(Color.WHITE); canvas.drawBitmap(decoded, 0, 0, null);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            if (!flattened.compress(Bitmap.CompressFormat.JPEG, 85, encoded)) throw new IOException(dev.pocketllama.I18n.t("Не удалось подготовить изображение"));
            String url = "data:image/jpeg;base64," + Base64.encodeToString(encoded.toByteArray(), Base64.NO_WRAP);
            return result.put("kind", "image").put("data_url", url).put("width", flattened.getWidth()).put("height", flattened.getHeight());
        } finally {
            if (decoded != null) decoded.recycle();
            if (flattened != null) flattened.recycle();
        }
    }

    private static JSONObject object(Object... pairs) {
        JSONObject value = new JSONObject();
        try { for (int i = 0; i < pairs.length; i += 2) value.put((String)pairs[i], pairs[i + 1]); }
        catch (Exception e) { throw new IllegalArgumentException(e); }
        return value;
    }
    private void emit(String type, JSONObject data) {
        if (destroyed || !pageReady || !dev.pocketllama.security.GateSession.isOpen()) return;
        String event = object("type", type, "data", data).toString();
        runOnUiThread(() -> {
            if (!destroyed && dev.pocketllama.security.GateSession.isOpen()) web.evaluateJavascript("window.PocketEvents(" + event + ");", null);
        });
    }
    private static String useful(Exception e) {
        String message = dev.pocketllama.I18n.status(e.getMessage());
        if (message == null || message.isEmpty()) return dev.pocketllama.I18n.t("Ошибка обработки. Проверьте состояние модели.");
        if (message.contains("ECONNREFUSED") || message.contains("Failed to connect") || message.contains("Connection refused"))
            return dev.pocketllama.I18n.t("Сервер недоступен. Запустите API в настройках модели.");
        return message;
    }
    private void fail(Exception e) { emit("error", object("message", useful(e))); }

    @Override public void onConfigurationChanged(android.content.res.Configuration c) { super.onConfigurationChanged(c);if(!secureInitialized())return;UiHost.colors(this,root,web);web.getSettings().setTextZoom(Math.round(100*c.fontScale)); }
    @Override protected void onResume() { super.onResume();if(!secureInitialized()||!dev.pocketllama.security.GateSession.isOpen())return; if(webConnected&&webConsentEpoch!=dev.pocketllama.security.GateSession.epoch())disconnectWeb();UiHost.syncLanguage(web);handler.post(refresh); }
    @Override protected void onPause() {if(!secureInitialized()){super.onPause();return;} handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onVaultLocked(){if(webConnected||webSearch!=null)disconnectWeb();}
    @Override protected void onDestroy() {
        disconnectWeb();
        destroyed = true; handler.removeCallbacks(refresh);
        ChatApi active = request; if (active != null) active.cancel();
        dev.pocketllama.rag.RagManager search=rag;if(search!=null)search.cancel();
        io.shutdownNow();
        if (web != null) { web.removeJavascriptInterface("Pocket"); web.destroy(); }
        super.onDestroy();
    }
}
