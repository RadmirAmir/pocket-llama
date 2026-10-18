package dev.pocketllama.voice;
import android.database.Cursor;
/** SQLite adapter keeps transcript and intermediate notes out of the Java heap. */
public final class VoiceSummarySource implements SummarySource {
    private final VoiceStore store;private final String id;
    public VoiceSummarySource(VoiceStore store,String id){this.store=store;this.id=id;}
    public Reader turns(){Cursor c=store.turns(id);return new Reader(){public String next(){return c.moveToNext()?"[#"+c.getInt(0)+"] "+VoiceStore.time(c.getLong(1))+" "+store.speaker(id,c.getInt(3))+": "+c.getString(4)+"\n":null;}public void close(){c.close();}};}
    public Reader notes(int pass){Cursor c=store.notes(id,pass);return new Reader(){public String next(){return c.moveToNext()?c.getString(1):null;}public void close(){c.close();}};}
    public String note(int pass,int index){return store.note(id,pass,index);}
    public void note(int pass,int index,String text){store.note(id,pass,index,text);}
    public boolean containsTurn(int index){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT 1 FROM turns WHERE mid=? AND idx=?",new String[]{id,""+index})){return c.moveToFirst();}}
}
