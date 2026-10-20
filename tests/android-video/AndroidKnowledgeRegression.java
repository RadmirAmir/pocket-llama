import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import dev.pocketllama.notes.NoteStore;
import dev.pocketllama.photo.*;
import dev.pocketllama.voice.VoiceStore;
import dev.pocketllama.rag.*;
import dev.pocketllama.security.Vault;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Real SQLCipher/FTS5 and production source readers, with deterministic synthetic embeddings. */
public final class AndroidKnowledgeRegression {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("OK "+label);}
    private static final class Embeddings implements RagStore.Embedder {
        int documents,queries,interruptAfter=-1;
        private float[] value(String text){float[] v=new float[768];v[0]=0.1f;v[text.contains("773311")?1:text.contains("EDITED_TRANSCRIPT")?2:text.contains("STALE_PRIVATE")?3:4]=1;return RagText.normalize(v);}
        public float[] document(String title,String text)throws Exception{if(interruptAfter>=0&&documents>=interruptAfter)throw new InterruptedException("Synthetic cancellation");documents++;return value(title+" "+text);}
        public float[] query(String text){queries++;return value(text);}
    }
    public static void run(Context parent)throws Exception {
        File data=new File(parent.getNoBackupFilesDir(),"knowledge-fixture");data.mkdirs();
        Context c=new ContextWrapper(parent){@Override public Context getApplicationContext(){return this;}@Override public File getNoBackupFilesDir(){return data;}};
        String noteId,meetingId,pageId,titleId;Embeddings embed=new Embeddings();RagSources reader=new RagSources(c,()->{});
        JSONObject locA=new JSONObject().put("document","doc").put("page",2).put("pageId","page"),locB=new JSONObject().put("pageId","page").put("page",2).put("document","doc");
        check(RagSources.text("ocr","page","","Title","Same text",locA).fingerprint().equals(RagSources.text("ocr","page","","Title","Same text",locB).fingerprint()),"source fingerprint is independent of JSON field order");
        try(NoteStore notes=new NoteStore(c);VoiceStore voice=new VoiceStore(c)) {
            for(int i=0;i<130;i++){JSONObject n=notes.create();notes.save(n.getString("id"),n.getLong("revision"),"Note "+i,"Material "+i+" café Ёжик 😀");}
            JSONObject n=notes.create();noteId=n.getString("id");notes.save(noteId,n.getLong("revision"),"Private note","STALE_PRIVATE text never leave device");
            n=notes.create();titleId=n.getString("id");notes.save(titleId,n.getLong("revision"),"Call Serena 742999","");
            meetingId=voice.create("Project meeting",false,1);
            for(int i=1;i<=240;i++)voice.getWritableDatabase().execSQL("INSERT INTO turns(mid,idx,start,end,speaker,text,text_fold) VALUES(?,?,?,?,?,?,?)",new Object[]{meetingId,i,i*1000L,i*1000L+900,1,"Original turn "+i,"original turn "+i});
            voice.getWritableDatabase().execSQL("INSERT INTO edits(mid,idx,speaker,text,text_fold) VALUES(?,?,?,?,?)",new Object[]{meetingId,210,1,"EDITED_TRANSCRIPT deadline Friday","edited_transcript deadline friday"});
            voice.name(meetingId,1,"Alice");
            PhotoStore photos=new PhotoStore(c);pageId=photos.create(new byte[]{1,2,3},new byte[]{4,5},100,100);photos.edit(pageId,"Invoice 773311: pay before 28 November. Сумма 12000 рублей.");
            DocumentStore docs=new DocumentStore(c,"paddle");String document=docs.create();docs.rename(document,"Invoice archive");docs.add(document,pageId);
            String legacy=photos.create(new byte[]{6},new byte[]{7},100,100);photos.edit(legacy,"Legacy OCR document is also indexed");
            try(RagStore index=new RagStore(c)) {
                index.sync(reader,embed,(a,b,d)->{},()->{});int first=embed.documents;
                check(index.status().getInt("sources")>=135,"knowledge reads all notes beyond UI page limits, transcripts and both OCR formats");
                index.sync(reader,embed,(a,b,d)->{},()->{});check(embed.documents==first,"unchanged corpus is not embedded again");
                float[] q=index.query("Invoice 773311",embed,()->{});index.query("Invoice 773311",embed,()->{});check(embed.queries==1,"repeated queries reuse the encrypted embedding cache");
                JSONArray headings=index.search("742999",index.query("742999",embed,()->{}),6,"note",reader,()->{}).getJSONArray("sources");check(headings.length()>0&&headings.getJSONObject(0).getString("sourceId").equals(titleId),"a note containing only a title is searchable and revalidates");
                JSONArray hits=index.search("Invoice 773311",q,6,"all",reader,()->{}).getJSONArray("sources");check(hits.length()>0&&hits.getJSONObject(0).getString("sourceId").equals(pageId),"hybrid FTS and vector search retrieves the exact invoice");
                JSONObject location=hits.getJSONObject(0).getJSONObject("location");check(location.getInt("page")==1&&location.getString("document").equals(document),"OCR source keeps document and page anchors");
                hits=index.search("EDITED_TRANSCRIPT",index.query("EDITED_TRANSCRIPT",embed,()->{}),6,"voice",reader,()->{}).getJSONArray("sources");
                check(hits.length()>0&&hits.toString().contains("EDITED_TRANSCRIPT")&&!hits.toString().contains("Original turn 210"),"transcript search includes edits after UI turn 100");
                check(hits.toString().contains("Alice")&&hits.getJSONObject(0).getJSONObject("location").has("start"),"transcript results preserve speaker names and timestamps");
                notes.trash(noteId,true);
                hits=index.search("STALE_PRIVATE",index.query("STALE_PRIVATE",embed,()->{}),6,"note",reader,()->{}).getJSONArray("sources");boolean stale=false;for(int i=0;i<hits.length();i++)stale|=hits.getJSONObject(i).getString("sourceId").equals(noteId);check(!stale,"trash is rejected even before stale index cleanup");
                index.sync(reader,embed,(a,b,d)->{},()->{});try(Cursor count=index.getReadableDatabase().rawQuery("SELECT count(*) FROM sources WHERE owner=?",new String[]{noteId})){count.moveToFirst();check(count.getInt(0)==0,"sync removes deleted source chunks and FTS postings");}
                photos.edit(pageId,"Invoice 773311: UPDATED amount 13000 рублей.");first=embed.documents;index.sync(reader,embed,(a,b,d)->{},()->{});check(embed.documents==first+1,"only a changed OCR passage is re-embedded");
                hits=index.search("773311",q,6,"ocr",reader,()->{}).getJSONArray("sources");check(hits.toString().contains("13000")&&!hits.toString().contains("12000"),"search exposes the edited OCR value");
                int oldCount=index.status().getInt("passages");StringBuilder large=new StringBuilder();for(int i=0;i<120;i++)large.append("Resume section ").append(i).append(' ').append("text ".repeat(30)).append('\n');
                n=notes.create();notes.save(n.getString("id"),n.getLong("revision"),"Resume",large.toString());embed.interruptAfter=embed.documents+2;boolean cancelled=false;
                try{index.sync(reader,embed,(a,b,d)->{},()->{});}catch(InterruptedException e){cancelled=true;}
                check(cancelled&&index.status().getInt("passages")==oldCount,"cancelled source update never publishes partial passages");
                int cached;try(Cursor count=index.getReadableDatabase().rawQuery("SELECT count(*) FROM vectors WHERE NOT EXISTS(SELECT 1 FROM chunks WHERE chunks.vector=vectors.id)",new String[0])){count.moveToFirst();cached=count.getInt(0);}check(cached==2,"completed embedding checkpoints survive cancellation");
                embed.interruptAfter=-1;index.sync(reader,embed,(a,b,d)->{},()->{});check(index.status().getInt("passages")>oldCount,"index resumes and completes after cancellation");
                byte[] bytes=Files.readAllBytes(new File(Vault.root(c),"knowledge.db").toPath());check(!new String(bytes,StandardCharsets.ISO_8859_1).contains("Invoice"),"on-disk knowledge database is encrypted");
            }
            try(RagStore reopened=new RagStore(c)){check(reopened.status().getInt("passages")>130,"encrypted index and FTS reopen");reopened.clear();check(reopened.status().getInt("passages")==0,"clear removes passages and embeddings");}
            check(notes.list("",false,0,100).getJSONArray("items").length()==100&&photos.get(pageId).optString("text").contains("13000"),"clearing search index preserves original notes and OCR");
        }
        System.out.println("PASS: "+checks+" Android SQLCipher knowledge checks");
    }
}
