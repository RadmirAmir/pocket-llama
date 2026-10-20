package dev.pocketllama.rag;

import android.content.Context;
import android.database.Cursor;
import dev.pocketllama.notes.NoteStore;
import dev.pocketllama.photo.DocumentStore;
import dev.pocketllama.photo.PhotoStore;
import dev.pocketllama.voice.VoiceStore;
import java.util.*;
import org.json.*;

/** Reads authoritative edited text, never UI pages, snippets, audio or image pixels. */
public final class RagSources {
    public interface Check extends RagText.Check {}
    public interface Visitor {void visit(Source source)throws Exception;}
    public static final class Piece {
        public final String text;
        public final JSONObject location;
        public Piece(String text,JSONObject location){this.text=text;this.location=location;}
    }
    public static final class Source {
        public final String key,kind,id,title;
        public final List<Piece> pieces;
        public Source(String kind,String id,String suffix,String title,List<Piece> pieces){this.kind=kind;this.id=id;this.key=kind+":"+id+suffix;this.title=title;this.pieces=pieces;}
        public String fingerprint(){StringBuilder s=new StringBuilder(RagText.VERSION).append('\n').append(title);for(Piece p:pieces)s.append('\n').append(p.location).append('\n').append(p.text);return RagText.hash(s.toString());}
    }
    private final Context context;
    private final Check check;
    public RagSources(Context context,Check check){this.context=context;this.check=check;}
    public void visit(Visitor visitor)throws Exception {
        try(NoteStore notes=new NoteStore(context);Cursor c=notes.getReadableDatabase().rawQuery("SELECT id,title,body FROM personal_notes WHERE deleted=0 ORDER BY id",new String[0])) {
            while(c.moveToNext()){check.run();visitor.visit(text("note",c.getString(0),"",c.getString(1),c.getString(2),new JSONObject().put("section","note")));}
        }
        try(VoiceStore voice=new VoiceStore(context);Cursor c=voice.getReadableDatabase().rawQuery("SELECT id,title,summary FROM meetings ORDER BY id",new String[0])) {
            while(c.moveToNext()) {
                check.run();String id=c.getString(0),title=c.getString(1);
                visitor.visit(transcript(voice,id,title));
                visitor.visit(text("voice",id,":summary",title,c.getString(2),new JSONObject().put("section","summary")));
            }
        }
        PhotoStore photos=new PhotoStore(context);Set<String> seen=new HashSet<>();
        JSONArray docs=new DocumentStore(context).forLocalIndex();
        for(int i=0;i<docs.length();i++) {
            JSONObject doc=docs.getJSONObject(i);JSONArray pages=doc.getJSONArray("pages");
            for(int p=0;p<pages.length();p++) {
                check.run();String id=pages.getString(p);if(!seen.add(id)||!photos.exists(id))continue;
                visitor.visit(photo(photos.get(id),doc.optString("title"),new JSONObject().put("document",doc.getString("id")).put("page",p+1).put("pageId",id)));
            }
        }
        for(String id:photos.idsForLocalIndex())if(seen.add(id)){check.run();JSONObject photo=photos.get(id);visitor.visit(photo(photo,photo.optString("title"),new JSONObject().put("page",1).put("pageId",id)));}
    }
    public static Source text(String kind,String id,String suffix,String title,String body,JSONObject extra)throws Exception {
        List<Piece> pieces=new ArrayList<>();
        for(RagText.Passage p:RagText.split(body==null?"":body))pieces.add(new Piece(p.text,new JSONObject(extra.toString()).put("offset",p.start).put("endOffset",p.end)));
        return new Source(kind,id,suffix,title==null?"":title,pieces);
    }
    private Source photo(JSONObject photo,String title,JSONObject location)throws Exception {
        // Completed checkpoints of interrupted OCR are useful too; active OCR is excluded by the coordinator.
        return text("ocr",photo.getString("id"),"",title,photo.optString("text"),location);
    }
    private Source transcript(VoiceStore voice,String id,String title)throws Exception {
        List<Piece> pieces=new ArrayList<>();Map<Integer,String> names=new HashMap<>();
        try(Cursor c=voice.getReadableDatabase().rawQuery("SELECT speaker,name FROM names WHERE mid=?",new String[]{id})){while(c.moveToNext())names.put(c.getInt(0),c.getString(1));}
        StringBuilder group=new StringBuilder();long start=0,end=0;String overlap="";long overlapStart=0;
        try(Cursor c=voice.turns(id)) {
            while(c.moveToNext()) {
                check.run();String body=c.getString(4);if(body==null||body.trim().isEmpty())continue;
                int speaker=c.getInt(3);String name=names.get(speaker);if(name==null||name.isEmpty())name="Speaker "+speaker;
                for(RagText.Passage part:RagText.split(body)) {
                    String turn="["+VoiceStore.time(c.getLong(1))+"] "+name+"\n"+part.text;
                    if(group.length()>0&&group.length()+turn.length()>RagText.CHARS) {
                        transcriptPieces(pieces,group.toString(),start,end);
                        group.setLength(0);if(!overlap.isEmpty()&&overlap.length()+turn.length()<=RagText.CHARS){group.append(overlap).append('\n');start=overlapStart;}
                    }
                    if(group.length()==0)start=c.getLong(1);else group.append('\n');
                    group.append(turn);end=c.getLong(2);overlap=turn.length()<=RagText.OVERLAP?turn:"";overlapStart=c.getLong(1);
                }
            }
        }
        if(group.length()>0)transcriptPieces(pieces,group.toString(),start,end);
        return new Source("voice",id,"",title,pieces);
    }
    private static void transcriptPieces(List<Piece> pieces,String text,long start,long end)throws Exception {for(RagText.Passage p:RagText.split(text))pieces.add(new Piece(p.text,new JSONObject().put("section","transcript").put("start",start).put("end",end).put("offset",p.start).put("endOffset",p.end)));}
    /** Re-read each selected source before exposing it; deleted/edited material cannot leak from a stale index. */
    public boolean current(JSONObject hit)throws Exception {
        check.run();String kind=hit.getString("kind"),id=hit.getString("sourceId");JSONObject loc=hit.getJSONObject("location");Source source;
        try {
            if(kind.equals("note"))try(NoteStore notes=new NoteStore(context)){JSONObject note=notes.get(id);if(note.optLong("deleted")!=0)return false;source=text("note",id,"",note.optString("title"),note.optString("body"),new JSONObject().put("section","note"));}
            else if(kind.equals("voice"))try(VoiceStore voice=new VoiceStore(context)){JSONObject meeting=voice.get(id);source=loc.optString("section").equals("summary")?text("voice",id,":summary",meeting.optString("title"),meeting.optString("summary"),new JSONObject().put("section","summary")):transcript(voice,id,meeting.optString("title"));}
            else {
                JSONObject page=new PhotoStore(context).get(id);String title=page.optString("title");JSONObject extra=new JSONObject().put("page",1).put("pageId",id);
                if(loc.has("document")){JSONObject doc=new DocumentStore(context).get(loc.getString("document"));JSONArray pages=doc.getJSONArray("pages");int number=0;for(int i=0;i<pages.length();i++)if(pages.getString(i).equals(id))number=i+1;if(number==0)return false;title=doc.optString("title");extra.put("document",loc.getString("document")).put("page",number);}
                source=photo(page,title,extra);
            }
            return source.fingerprint().equals(hit.getString("fingerprint"));
        }catch(java.io.FileNotFoundException|IllegalArgumentException e){return false;}
    }
}
