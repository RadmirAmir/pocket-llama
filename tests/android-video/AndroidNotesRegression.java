import android.content.Context;
import dev.pocketllama.notes.NoteStore;
import dev.pocketllama.security.Vault;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** Real SQLCipher CRUD/search/reopen checks with the fixture's synthetic vault key. */
public final class AndroidNotesRegression {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("OK "+label);}
    public static void run(Context c) throws Exception {
        String id, second;
        String text="Личный текст <script>alert(1)</script>\n"+"строка ".repeat(80)+"СЕКРЕТНЫЙ ЁЖИК\n100%_literal ' OR 1=1 -- 😀";
        try(NoteStore store=new NoteStore(c)){
            check(store.list("",false,0,50).getJSONArray("items").length()==0,"notes start in their own empty encrypted database");
            JSONObject note=store.create(); id=note.getString("id");
            note=store.save(id,note.getLong("revision"),"Заголовок EN café",text);
            check(note.getString("body").equals(text),"note body preserves markup, Unicode and newlines verbatim");
            check(store.list("секретный ежик",false,0,50).getJSONArray("items").length()==1,"Cyrillic body search folds case and yo");
            check(store.list("CAFÉ",false,0,50).getJSONArray("items").length()==1,"Unicode title search ignores case");
            JSONObject result=store.list("секретный",false,0,50).getJSONArray("items").getJSONObject(0);
            check(result.getString("preview").contains("СЕКРЕТНЫЙ")&&!result.has("body"),"search result is a bounded matching snippet, not a full note payload");
            second=store.create().getString("id");
            check(store.list("%_literal",false,0,50).getJSONArray("items").length()==1,"SQL wildcard characters are literal");
            check(store.list("' OR 1=1 --",false,0,50).getJSONArray("items").length()==1,"SQL punctuation cannot broaden the query");
            store.pin(id,true);
            check(store.list("",false,0,1).getJSONArray("items").getJSONObject(0).getString("id").equals(id),"pinned notes precede newer notes");
            check(store.list("",false,0,1).getBoolean("more")&&store.list("",false,1,1).getJSONArray("items").length()==1,"notes pagination keeps older results reachable");
            long revision=note.getLong("revision");note=store.save(id,revision,"Changed title",text);
            boolean refused=false;try{store.save(id,revision,"Stale title","stale");}catch(Exception e){refused=true;}
            check(refused&&store.get(id).getString("body").equals(text),"stale editor cannot overwrite saved data");
            store.trash(id,true);
            check(store.list("секретный",false,0,50).getJSONArray("items").length()==0,"trash is excluded from active search");
            check(store.list("секретный",true,0,50).getJSONArray("items").length()==1,"trash remains searchable");
            refused=false;try{store.save(id,note.getLong("revision"),"Oops","Oops");}catch(Exception e){refused=true;}
            check(refused,"late draft cannot resurrect a trashed note");
            store.trash(id,false);
            check(store.get(id).getString("body").equals(text)&&store.get(id).getBoolean("pinned"),"restore preserves body and pinned state");
            refused=false;try{store.purge(id);}catch(Exception e){refused=true;}
            check(refused,"permanent deletion requires the note to be in trash");
            store.trash(second,true);store.purge(second);
            check(store.list("",true,0,50).getJSONArray("items").length()==0,"permanent deletion removes a trashed note");
            // Search fields and WAL must never be written as ordinary SQLite/plaintext.
            String secret="SENSITIVE_NOTE_BODY_42";note=store.get(id);store.save(id,note.getLong("revision"),"SENSITIVE_NOTE_TITLE_42",secret);
            for(File file:Vault.root(c).listFiles())if(file.getName().startsWith("notes.db")){
                String bytes=new String(Files.readAllBytes(file.toPath()),StandardCharsets.ISO_8859_1);
                check(!bytes.startsWith("SQLite format 3")&&!bytes.contains(secret)&&!bytes.contains("SENSITIVE_NOTE_TITLE_42"),"no plaintext note text in database/sidecar: "+file.getName());
            }
        }
        try(NoteStore store=new NoteStore(c)){
            check(store.get(id).getString("body").equals("SENSITIVE_NOTE_BODY_42"),"saved note survives store close and process-style reopen");
            check(store.list("sensitive_note_body_42",false,0,50).getJSONArray("items").length()==1,"encrypted search index survives reopen");
        }
        System.out.println("PASS: "+checks+" Android notes persistence/search/encryption checks");
    }
}
