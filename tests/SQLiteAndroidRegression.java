import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import dev.pocketllama.security.DatabaseSettings;
import net.zetetic.database.DatabaseErrorHandler;
import net.zetetic.database.sqlcipher.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

/** Run on Android ART via app_process with the pinned SQLCipher Java/JNI, without the UI. */
public final class SQLiteAndroidRegression {
    private static int checks;
    private static final byte[] KEY="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final DatabaseErrorHandler KEEP=(db,error)->{throw new IllegalStateException("Test DB preserved",error);};
    private static final String TITLE="Сохранённая запись · пятница";
    private static final java.util.concurrent.atomic.AtomicInteger connections=new java.util.concurrent.atomic.AtomicInteger();
    private static final SQLiteDatabaseHook CHECKED_HOOK=new SQLiteDatabaseHook(){
        @Override public void preKey(SQLiteConnection c){DatabaseSettings.HOOK.preKey(c);}
        @Override public void postKey(SQLiteConnection c){
            DatabaseSettings.HOOK.postKey(c);
            check(c.executeForLong("PRAGMA temp_store",null,null)==2,"connection temp_store applied");
            check(c.executeForLong("PRAGMA secure_delete",null,null)==1,"connection secure_delete applied");
            check(c.executeForLong("PRAGMA cipher_memory_security",null,null)==1,"connection cipher memory security applied");
            connections.incrementAndGet();
        }
    };
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;System.out.println("OK "+text);}
    private static long number(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,new String[0])){if(!c.moveToFirst())throw new AssertionError(sql+" returned no row");return c.getLong(0);}}
    private static String string(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,new String[0])){if(!c.moveToFirst())throw new AssertionError(sql+" returned no row");return c.getString(0);}}
    private static SQLiteDatabase open(File path,byte[] key){return SQLiteDatabase.openDatabase(path.getAbsolutePath(),key,null,SQLiteDatabase.CREATE_IF_NECESSARY,KEEP,null);}
    private static void fixture(SQLiteDatabase db){db.execSQL("CREATE TABLE meetings(id INTEGER PRIMARY KEY,title TEXT)");db.execSQL("INSERT INTO meetings VALUES(1,?)",new Object[]{TITLE});db.setVersion(1);}
    private static SQLiteOpenHelper helper(File path){
        Context context=new ContextWrapper(null){@Override public Context getApplicationContext(){return this;}@Override public File getDatabasePath(String name){return new File(name);}};
        return new SQLiteOpenHelper(context,path.getAbsolutePath(),KEY,null,2,0,KEEP,CHECKED_HOOK,true){
            @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
            @Override public void onCreate(SQLiteDatabase db){fixture(db);onUpgrade(db,1,2);}
            @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){db.execSQL("ALTER TABLE meetings ADD COLUMN title_fold TEXT");db.execSQL("UPDATE meetings SET title_fold=?",new Object[]{TITLE.toLowerCase(Locale.ROOT)});}
        };
    }
    private static void settings(SQLiteDatabase db,String label){
        check(number(db,"PRAGMA temp_store")==2,label+" temp tables use memory");
        check(number(db,"PRAGMA secure_delete")==1,label+" secure delete enabled");
        check(number(db,"PRAGMA cipher_memory_security")==1,label+" cipher memory protection enabled");
    }
    public static void main(String[] args)throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),true,"UTF-8"));
        try { run(args); } catch(Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }
    private static void run(String[] args)throws Exception {
        System.load(args[1]);net.zetetic.database.Logger.setTarget(new net.zetetic.database.NoopTarget());File root=new File(args[0]);if(!root.isDirectory()&&!root.mkdirs())throw new IllegalStateException("Test directory");
        File existing=new File(root,"existing.db");
        try(SQLiteDatabase db=open(existing,KEY)){
            String core=string(db,"PRAGMA cipher_version");System.out.println("Android SQLCipher core: "+core);
            check(core.startsWith("4."),"pinned SQLCipher Android 4.19.1 AAR loaded (core version reported above)");fixture(db);
            boolean reproduced=false;try{db.execSQL("PRAGMA secure_delete=ON");}catch(android.database.sqlite.SQLiteException error){System.out.println("REPRODUCED "+error.getMessage());reproduced=error.getMessage().contains("Queries can be performed using SQLiteDatabase query or rawQuery methods only");}
            check(reproduced,"0.5.0 error reproduced with original execSQL call");
            check(string(db,"SELECT title FROM meetings WHERE id=1").equals(TITLE),"failed configure preserves existing content");
        }
        try(SQLiteOpenHelper h=helper(existing)){
            SQLiteDatabase db=h.getWritableDatabase();check(db.getVersion()==2,"encrypted v1 upgrades after fixed configure");check(string(db,"SELECT title FROM meetings WHERE id=1").equals(TITLE),"existing Unicode record retained");check(string(db,"SELECT title_fold FROM meetings WHERE id=1").equals(TITLE.toLowerCase(Locale.ROOT)),"schema upgrade retains searchable text");settings(db,"primary");check(db.isWriteAheadLoggingEnabled(),"WAL enabled");
            // PRAGMA queries can request the primary connection; use a SELECT while holding a WAL writer.
            // CHECKED_HOOK verifies settings directly on every secondary connection as it opens.
            ExecutorService worker=Executors.newSingleThreadExecutor();db.beginTransactionNonExclusive();try{worker.submit(()->check(string(db,"SELECT title FROM meetings WHERE id=1").equals(TITLE),"secondary WAL reader retains content")).get(60,TimeUnit.SECONDS);check(connections.get()>=2,"secondary WAL connection configured by hook");}finally{db.endTransaction();worker.shutdownNow();}
        }
        try(SQLiteOpenHelper h=helper(existing)){SQLiteDatabase db=h.getWritableDatabase();settings(db,"reopened");check(db.getVersion()==2&&string(db,"SELECT title FROM meetings WHERE id=1").equals(TITLE),"existing encrypted v2 reopens");}
        File fresh=new File(root,"fresh.db");try(SQLiteOpenHelper h=helper(fresh)){SQLiteDatabase db=h.getWritableDatabase();settings(db,"fresh install");check(db.getVersion()==2,"fresh encrypted database created");}
        File plain=new File(root,"legacy.db"),encrypted=new File(root,"migrated.db");
        try(SQLiteDatabase db=open(plain,new byte[0])){fixture(db);db.enableWriteAheadLogging();db.execSQL("UPDATE meetings SET title=? WHERE id=1",new Object[]{TITLE});try(Cursor c=db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",new String[0])){check(c.moveToFirst()&&c.getInt(0)==0,"legacy WAL checkpoint succeeds");}db.execSQL("PRAGMA temp_store=MEMORY");db.execSQL("ATTACH DATABASE ? AS protected KEY ?",new Object[]{encrypted.getAbsolutePath(),new String(KEY,StandardCharsets.US_ASCII)});db.rawExecSQL("SELECT sqlcipher_export('protected')");db.execSQL("PRAGMA protected.user_version=1");db.execSQL("DETACH DATABASE protected");}
        try(SQLiteOpenHelper h=helper(encrypted)){SQLiteDatabase db=h.getWritableDatabase();settings(db,"migrated");check(db.getVersion()==2&&string(db,"SELECT title FROM meetings WHERE id=1").equals(TITLE),"native plaintext export and upgraded encrypted reopen");check(string(db,"PRAGMA integrity_check").equals("ok"),"encrypted database integrity");}
        System.out.println("PASS: "+checks+" Android SQLite regression checks");
    }
}
