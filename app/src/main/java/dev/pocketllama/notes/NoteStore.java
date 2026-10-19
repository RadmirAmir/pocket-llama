package dev.pocketllama.notes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import dev.pocketllama.security.DatabaseSettings;
import dev.pocketllama.security.Vault;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Notes and their search fields stay inside SQLCipher, outside the recording schema. */
public final class NoteStore extends SQLiteOpenHelper {
    public NoteStore(Context context) {
        super(context.getApplicationContext(), new File(Vault.root(context), "notes.db").getAbsolutePath(),
            Vault.databasePassword(context), null, 1, 0, Vault.KEEP_DATABASE, DatabaseSettings.HOOK, true);
        setWriteAheadLoggingEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE personal_notes(id TEXT PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL,title_fold TEXT NOT NULL,body_fold TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,revision INTEGER NOT NULL DEFAULT 1,pinned INTEGER NOT NULL DEFAULT 0,deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX note_order ON personal_notes(deleted,pinned DESC,updated DESC,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unknown notes database version; data preserved");
    }
    public JSONObject create() throws Exception {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put("id", id); values.put("title", ""); values.put("body", "");
        values.put("title_fold", ""); values.put("body_fold", "");
        values.put("created", now); values.put("updated", now);
        getWritableDatabase().insertOrThrow("personal_notes", null, values);
        return get(id);
    }
    public JSONObject get(String id) throws Exception {
        checkId(id);
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,body,created,updated,revision,pinned,deleted FROM personal_notes WHERE id=?", new String[]{id})) {
            if (!c.moveToFirst()) throw new IOException("Заметка не найдена");
            return row(c, true, "");
        }
    }
    public JSONObject save(String id, long revision, String title, String body) throws Exception {
        checkId(id); NoteText.validate(title, body);
        ContentValues v = new ContentValues();
        v.put("title", title); v.put("body", body); v.put("title_fold", NoteText.fold(title)); v.put("body_fold", NoteText.fold(body));
        v.put("updated", System.currentTimeMillis()); v.put("revision", revision + 1);
        if (getWritableDatabase().update("personal_notes", v, "id=? AND revision=? AND deleted=0", new String[]{id, String.valueOf(revision)}) != 1)
            throw new IOException("Заметка изменилась в другом окне. Ваш текст остаётся в редакторе");
        return get(id);
    }
    public JSONObject pin(String id, boolean pinned) throws Exception {
        checkId(id); ContentValues v = new ContentValues(); v.put("pinned", pinned ? 1 : 0);
        if (getWritableDatabase().update("personal_notes", v, "id=? AND deleted=0", new String[]{id}) != 1) throw new IOException("Заметка не найдена");
        return get(id);
    }
    public void trash(String id, boolean deleted) throws Exception {
        checkId(id); ContentValues v = new ContentValues(); v.put("deleted", deleted ? System.currentTimeMillis() : 0);
        if (!deleted) v.put("updated", System.currentTimeMillis());
        if (getWritableDatabase().update("personal_notes", v, "id=?", new String[]{id}) != 1) throw new IOException("Заметка не найдена");
    }
    public void purge(String id) throws Exception {
        checkId(id);
        if (getWritableDatabase().delete("personal_notes", "id=? AND deleted>0", new String[]{id}) != 1) throw new IOException("Сначала переместите заметку в корзину");
    }
    public JSONObject list(String query, boolean trash, int offset, int limit) throws Exception {
        String folded = NoteText.query(query); limit = Math.max(1, Math.min(100, limit)); offset = Math.max(0, Math.min(1000000, offset));
        String where = (trash ? "deleted>0" : "deleted=0") + (folded.isEmpty() ? "" : " AND (instr(title_fold,?)>0 OR instr(body_fold,?)>0)");
        String[] args = folded.isEmpty() ? new String[0] : new String[]{folded, folded};
        JSONArray items = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,body,created,updated,revision,pinned,deleted FROM personal_notes WHERE " + where + " ORDER BY " + (trash ? "deleted DESC,id" : "pinned DESC,updated DESC,id") + " LIMIT " + (limit + 1) + " OFFSET " + offset, args)) {
            while (c.moveToNext()) items.put(row(c, false, folded));
        }
        boolean more = items.length() > limit; if (more) items.remove(limit);
        JSONObject result = new JSONObject().put("items", items).put("more", more).put("offset", offset).put("query", query).put("trash", trash);
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(CASE WHEN deleted>0 THEN 1 ELSE 0 END),0) FROM personal_notes", new String[0])) {
            c.moveToFirst(); result.put("activeCount", c.getLong(0) - c.getLong(1)).put("trashCount", c.getLong(1));
        }
        return result;
    }
    private static JSONObject row(Cursor c, boolean full, String query) throws Exception {
        JSONObject j = new JSONObject().put("id", c.getString(0)).put("title", c.getString(1))
            .put("created", c.getLong(3)).put("updated", c.getLong(4)).put("revision", c.getLong(5))
            .put("pinned", c.getInt(6) != 0).put("deleted", c.getLong(7));
        return full ? j.put("body", c.getString(2)) : j.put("preview", NoteText.preview(c.getString(2), query));
    }
    private static void checkId(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Некорректная заметка");
    }
}
