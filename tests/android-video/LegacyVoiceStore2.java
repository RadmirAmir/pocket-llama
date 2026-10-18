package dev.pocketllama.voice;

import android.content.*;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.*;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** SQLite WAL is both the cross-process control plane and the durable checkpoint log. */
public final class LegacyVoiceStore2 extends SQLiteOpenHelper {
    public LegacyVoiceStore2(Context c){super(c.getApplicationContext(),dev.pocketllama.security.Vault.database(c).getAbsolutePath(),dev.pocketllama.security.Vault.databasePassword(c),null,2,0,dev.pocketllama.security.Vault.KEEP_DATABASE,dev.pocketllama.security.DatabaseSettings.HOOK,true);setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE meetings(id TEXT PRIMARY KEY,title TEXT NOT NULL,created INTEGER,samples INTEGER DEFAULT 0,state TEXT DEFAULT 'ready',detail TEXT DEFAULT '',progress REAL DEFAULT 0,heartbeat INTEGER DEFAULT 0,summary TEXT DEFAULT '',summary_key TEXT DEFAULT '',source_uri TEXT DEFAULT '',diarize INTEGER DEFAULT 1,expected INTEGER DEFAULT 0,vad_done INTEGER DEFAULT 0,speakers_done INTEGER DEFAULT 0,asr_done INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE regions(mid TEXT REFERENCES meetings(id) ON DELETE CASCADE,idx INTEGER,start INTEGER,end INTEGER,PRIMARY KEY(mid,idx))");
        d.execSQL("CREATE TABLE embeddings(mid TEXT REFERENCES meetings(id) ON DELETE CASCADE,idx INTEGER,start INTEGER,end INTEGER,vector BLOB,speaker INTEGER DEFAULT 0,score REAL DEFAULT 0,PRIMARY KEY(mid,idx))");
        d.execSQL("CREATE INDEX embedding_time ON embeddings(mid,start,end)");
        d.execSQL("CREATE TABLE parts(mid TEXT REFERENCES meetings(id) ON DELETE CASCADE,idx INTEGER,PRIMARY KEY(mid,idx))");
        d.execSQL("CREATE TABLE words(mid TEXT REFERENCES meetings(id) ON DELETE CASCADE,part INTEGER,idx INTEGER,start INTEGER,end INTEGER,speaker INTEGER,text TEXT,PRIMARY KEY(mid,part,idx))");
        d.execSQL("CREATE INDEX word_time ON words(mid,start)");
        d.execSQL("CREATE TABLE turns(mid TEXT REFERENCES meetings(id) ON DELETE CASCADE,idx INTEGER,start INTEGER,end INTEGER,speaker INTEGER,text TEXT,PRIMARY KEY(mid,idx))");
        d.execSQL("CREATE TABLE names(mid TEXT REFERENCES meetings(id) ON DELETE CASCADE,speaker INTEGER,name TEXT,PRIMARY KEY(mid,speaker))");
        d.execSQL("CREATE TABLE edits(mid TEXT REFERENCES meetings(id) ON DELETE CASCADE,idx INTEGER,speaker INTEGER,text TEXT,PRIMARY KEY(mid,idx))");
        d.execSQL("CREATE TABLE notes(mid TEXT REFERENCES meetings(id) ON DELETE CASCADE,pass INTEGER,idx INTEGER,text TEXT,PRIMARY KEY(mid,pass,idx))");folded(d);
    }
    private void folded(SQLiteDatabase d){
        d.execSQL("ALTER TABLE meetings ADD COLUMN title_fold TEXT DEFAULT ''");d.execSQL("ALTER TABLE turns ADD COLUMN text_fold TEXT DEFAULT ''");d.execSQL("ALTER TABLE edits ADD COLUMN text_fold TEXT DEFAULT ''");
        for(String table:new String[]{"meetings","turns","edits"}){String column=table.equals("meetings")?"title":"text";try(Cursor cursor=d.rawQuery("SELECT rowid,"+column+" FROM "+table,new String[0])){while(cursor.moveToNext()){ContentValues v=new ContentValues();v.put(column+"_fold",(cursor.isNull(1)?"":cursor.getString(1)).toLowerCase(Locale.ROOT));d.update(table,v,"rowid=?",new String[]{""+cursor.getLong(0)});}}}
    }
    @Override public void onUpgrade(SQLiteDatabase d,int a,int b){if(a==1&&b==2)folded(d);else throw new IllegalStateException("Unknown voice database version");}
    public static boolean busy(String state){return state.equals("recording")||state.equals("paused")||state.equals("preparing")||state.equals("processing")||state.equals("summarizing");}
    public synchronized String create(String title,boolean diarize,int expected){String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("title",title);v.put("title_fold",title.toLowerCase(Locale.ROOT));v.put("created",System.currentTimeMillis());v.put("diarize",diarize?1:0);v.put("expected",expected);getWritableDatabase().insertOrThrow("meetings",null,v);return id;}
    public static File audio(Context c,String id){return dev.pocketllama.security.Vault.audio(c,id);}
    public JSONObject get(String id)throws JSONException{try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM meetings WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new IllegalArgumentException("Запись не найдена");return row(c);}}
    private static JSONObject row(Cursor c)throws JSONException{JSONObject j=new JSONObject();for(int i=0;i<c.getColumnCount();i++){if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)j.put(c.getColumnName(i),c.getLong(i));else if(c.getType(i)==Cursor.FIELD_TYPE_FLOAT)j.put(c.getColumnName(i),c.getDouble(i));else j.put(c.getColumnName(i),c.getString(i));}return j;}
    public JSONArray list()throws JSONException{JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM meetings ORDER BY created DESC",null)){while(c.moveToNext())a.put(row(c));}return a;}
    public boolean anyBusy(){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM meetings WHERE state IN ('recording','paused','preparing','processing','summarizing') LIMIT 1",null)){return c.moveToFirst();}}
    public void startJob(String id,String state)throws Exception{
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            if(anyBusy())throw new IOException("Сначала завершите текущую запись или обработку");
            state(id,state,"Подготовка…",0);d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    public void state(String id,String state,String detail,double progress){ContentValues v=new ContentValues();v.put("state",state);v.put("detail",detail);v.put("progress",progress);v.put("heartbeat",System.currentTimeMillis());update(id,v);}
    public void update(String id,ContentValues values){getWritableDatabase().update("meetings",values,"id=?",new String[]{id});}
    public void flag(String id,String field,boolean value){if(!Arrays.asList("vad_done","speakers_done","asr_done").contains(field))throw new IllegalArgumentException();ContentValues v=new ContentValues();v.put(field,value?1:0);update(id,v);}
    public void samples(String id,long samples){ContentValues v=new ContentValues();v.put("samples",samples);v.put("heartbeat",System.currentTimeMillis());update(id,v);}
    public void rename(String id,String title){ContentValues v=new ContentValues();v.put("title",title.substring(0,Math.min(120,title.length())));v.put("title_fold",title.substring(0,Math.min(120,title.length())).toLowerCase(Locale.ROOT));update(id,v);}
    public void name(String id,int speaker,String name){ContentValues v=new ContentValues();v.put("mid",id);v.put("speaker",speaker);v.put("name",name.substring(0,Math.min(80,name.length())));getWritableDatabase().insertWithOnConflict("names",null,v,SQLiteDatabase.CONFLICT_REPLACE);clearSummary(id);}
    public String speaker(String id,int n){try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM names WHERE mid=? AND speaker=?",new String[]{id,""+n})){if(c.moveToFirst()&&!c.getString(0).isEmpty())return c.getString(0);}return n==0?"Спикер не определён":"Спикер "+n;}
    public void clearSummary(String id){getWritableDatabase().delete("notes","mid=?",new String[]{id});ContentValues v=new ContentValues();v.put("summary","");update(id,v);}
    public void configure(String id,boolean diarize,int expected)throws Exception{
        JSONObject j=get(id);if(busy(j.getString("state")))throw new IOException("Задача уже выполняется");
        if(j.getInt("diarize")== (diarize?1:0)&&j.getInt("expected")==expected)return;
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            for(String t:new String[]{"embeddings","words","turns","parts","notes","names","edits"})d.delete(t,"mid=?",new String[]{id});
            ContentValues v=new ContentValues();v.put("diarize",diarize?1:0);v.put("expected",expected);v.put("speakers_done",0);v.put("asr_done",0);v.put("summary","");update(id,v);d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    /** Explicit user-requested restart; leaves the original PCM and import URI intact. */
    public void restartRecognition(String id)throws Exception {
        if(anyBusy())throw new IOException("Сначала завершите текущую задачу");
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try {
            for(String table:new String[]{"regions","embeddings","words","turns","parts","notes","names","edits"})d.delete(table,"mid=?",new String[]{id});
            ContentValues v=new ContentValues();v.put("vad_done",0);v.put("speakers_done",0);v.put("asr_done",0);v.put("summary","");v.put("summary_key","");v.put("progress",0);update(id,v);d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    public Cursor regions(String id){return getReadableDatabase().rawQuery("SELECT idx,start,end FROM regions WHERE mid=? ORDER BY idx",new String[]{id});}
    public void region(String id,int idx,long start,long end){ContentValues v=new ContentValues();v.put("mid",id);v.put("idx",idx);v.put("start",start);v.put("end",end);getWritableDatabase().insertWithOnConflict("regions",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public void embedding(String id,int idx,long start,long end,float[] vector){ByteBuffer b=ByteBuffer.allocate(vector.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float x:vector)b.putFloat(x);ContentValues v=new ContentValues();v.put("mid",id);v.put("idx",idx);v.put("start",start);v.put("end",end);v.put("vector",b.array());getWritableDatabase().insertWithOnConflict("embeddings",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    public static float[] vector(byte[] b){FloatBuffer f=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();float[] v=new float[f.remaining()];f.get(v);return v;}
    public int speakerAt(String id,long time){
        double[] votes=new double[13];try(Cursor c=getReadableDatabase().rawQuery("SELECT start,end,speaker,score FROM embeddings WHERE mid=? AND start<=? AND end>?",new String[]{id,""+time,""+time})){
            while(c.moveToNext()){int n=c.getInt(2);if(n>0&&n<votes.length){double half=(c.getLong(1)-c.getLong(0))/2.0,center=c.getLong(0)+half;votes[n]+=Math.max(0.1,1-Math.abs(time-center)/half)*Math.max(0,c.getDouble(3));}}
        }int best=0;double v=0;for(int i=1;i<votes.length;i++)if(votes[i]>v){v=votes[i];best=i;}return best;
    }
    public boolean partDone(String id,int idx){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM parts WHERE mid=? AND idx=?",new String[]{id,""+idx})){return c.moveToFirst();}}
    public void part(String id,int idx,List<SpeechModels.Word> words,boolean diarize,long coreStart,long coreEnd,boolean last)throws Exception{
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            d.delete("words","mid=? AND part=?",new String[]{id,""+idx});int n=0;
            String lastText="";long lastEnd=-10000;
            try(Cursor c=d.rawQuery("SELECT text,end FROM words WHERE mid=? AND part<? ORDER BY part DESC,idx DESC LIMIT 1",new String[]{id,""+idx})){if(c.moveToFirst()){lastText=c.getString(0);lastEnd=c.getLong(1);}}
            for(SpeechModels.Word w:words){long midpoint=(w.startMs+w.endMs)/2;if(midpoint<coreStart||midpoint>=coreEnd)continue;
                if(n==0&&w.text.equalsIgnoreCase(lastText)&&w.startMs<=lastEnd)continue;
                ContentValues v=new ContentValues();v.put("mid",id);v.put("part",idx);v.put("idx",n++);v.put("start",w.startMs);v.put("end",w.endMs);v.put("speaker",diarize?speakerAt(id,midpoint):1);v.put("text",w.text);d.insertOrThrow("words",null,v);lastText=w.text;lastEnd=w.endMs;
            }
            ContentValues v=new ContentValues();v.put("mid",id);v.put("idx",idx);d.insertWithOnConflict("parts",null,v,SQLiteDatabase.CONFLICT_IGNORE);d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    public void rebuildTurns(String id){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            d.delete("turns","mid=?",new String[]{id});int idx=1,speaker=-1;long start=0,end=0;StringBuilder text=new StringBuilder();
            try(Cursor c=d.rawQuery("SELECT start,end,speaker,text FROM words WHERE mid=? ORDER BY part,idx",new String[]{id})){
                while(c.moveToNext()){long s=c.getLong(0),e=c.getLong(1);int sp=c.getInt(2);String t=c.getString(3);
                    if(text.length()>0&&(sp!=speaker||s-end>1800||e-start>20000)){turn(d,id,idx++,start,end,speaker,text.toString());text.setLength(0);}
                    if(text.length()==0){start=s;speaker=sp;}else if(!t.matches("^[.,!?;:]+$"))text.append(' ');text.append(t);end=e;
                }
            }if(text.length()>0)turn(d,id,idx,start,end,speaker,text.toString());d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    private void turn(SQLiteDatabase d,String id,int idx,long start,long end,int speaker,String text){ContentValues v=new ContentValues();v.put("mid",id);v.put("idx",idx);v.put("start",start);v.put("end",end);v.put("speaker",speaker);v.put("text",text);v.put("text_fold",text.toLowerCase(Locale.ROOT));d.insertOrThrow("turns",null,v);}
    public Cursor turns(String id){return getReadableDatabase().rawQuery("SELECT t.idx,t.start,t.end,COALESCE(e.speaker,t.speaker),COALESCE(e.text,t.text) FROM turns t LEFT JOIN edits e ON e.mid=t.mid AND e.idx=t.idx WHERE t.mid=? ORDER BY t.idx",new String[]{id});}
    public void editTurn(String id,int idx,int speaker,String text){ContentValues v=new ContentValues();v.put("mid",id);v.put("idx",idx);v.put("speaker",speaker);v.put("text",text.substring(0,Math.min(8000,text.length())));v.put("text_fold",text.substring(0,Math.min(8000,text.length())).toLowerCase(Locale.ROOT));getWritableDatabase().insertWithOnConflict("edits",null,v,SQLiteDatabase.CONFLICT_REPLACE);clearSummary(id);}
    public String note(String id,int pass,int idx){try(Cursor c=getReadableDatabase().rawQuery("SELECT text FROM notes WHERE mid=? AND pass=? AND idx=?",new String[]{id,""+pass,""+idx})){return c.moveToFirst()?c.getString(0):null;}}
    public void note(String id,int pass,int idx,String text){ContentValues v=new ContentValues();v.put("mid",id);v.put("pass",pass);v.put("idx",idx);v.put("text",text);getWritableDatabase().insertWithOnConflict("notes",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public Cursor notes(String id,int pass){return getReadableDatabase().rawQuery("SELECT idx,text FROM notes WHERE mid=? AND pass=? ORDER BY idx",new String[]{id,""+pass});}
    public void delete(Context c,String id)throws Exception{if(busy(get(id).getString("state")))throw new IOException("Сначала остановите задачу");getWritableDatabase().delete("meetings","id=?",new String[]{id});audio(c,id).delete();}
    public void recover(Context c,boolean workerAlive)throws Exception{
        JSONArray all=list();for(int i=0;i<all.length();i++){JSONObject j=all.getJSONObject(i);String state=j.getString("state"),id=j.getString("id");
            boolean recording=state.equals("recording")||state.equals("paused");
            if(busy(state)&&!VoiceState.busy&&!(recording?VoiceState.recording:state.equals("summarizing")?VoiceState.busy:workerAlive)){
                File f=audio(c,id);if(f.exists())try{samples(id,PcmFile.repair(f,dev.pocketllama.security.Vault.key(c)));}catch(IOException ignored){}
                rebuildTurns(id);state(id,"interrupted","Задача прервана. Запись и контрольные точки сохранены; можно продолжить.",j.optDouble("progress"));
            }
        }
    }
    public static String time(long ms){long seconds=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
