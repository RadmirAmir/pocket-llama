package dev.pocketllama.security;
import android.content.*;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Resumable migration: verify a committed encrypted copy before deleting each legacy source. */
public final class VaultMigration {
    public interface Progress{void update(String text)throws Exception;}
    private VaultMigration(){}
    public static void run(Context c,Progress p)throws Exception {
        if(Vault.ready(c)){
            Vault.read(c,"ready.bin",4096);byte[] password=Vault.databasePassword(c);
            try{File existing=new File(Vault.root(c),"voice.db");if(existing.exists())try(SQLiteDatabase db=SQLiteDatabase.openDatabase(existing.getAbsolutePath(),password,null,SQLiteDatabase.OPEN_READONLY,Vault.KEEP_DATABASE,null)){db.getVersion();}}finally{Arrays.fill(password,(byte)0);}return;
        }
        try(RandomAccessFile lockFile=new RandomAccessFile(new File(Vault.root(c),"migration.lock"),"rw");FileLock lock=lockFile.getChannel().lock()){
            if(Vault.ready(c))return;
            p.update("Защищаем расшифровки и конспекты…");byte[] databaseKey=Vault.databasePassword(c);Arrays.fill(databaseKey,(byte)0);database(c);
            File oldDir=new File(c.getFilesDir(),"recordings");File[] audio=oldDir.listFiles(File::isFile);if(audio!=null){Arrays.sort(audio,Comparator.comparing(File::getName));int done=0;for(File source:audio){p.update("Защищаем аудио · "+(++done)+" / "+audio.length);boolean wav=source.getName().matches("[a-f0-9-]{36}\\.wav");File target=wav?Vault.audio(c,source.getName().substring(0,36)):orphan(c,source.getName());migrateStream(source,target,Vault.key(c),wav);delete(source);}}
            p.update("Защищаем историю чата и настройки…");chat(c);preferences(c);
            for(String name:new String[]{"chat.json.part","server.key"}){File f=new File(c.getFilesDir(),name);if(f.exists()){if(name.endsWith(".part"))migrateStream(f,orphan(c,name),Vault.key(c),false);delete(f);}}
            // All referenced user content is now encrypted. The existing photo vault already uses this Keystore alias.
            Vault.write(c,"ready.bin","Pocket vault v1; migration verified".getBytes(StandardCharsets.UTF_8));
        }
    }
    private static File orphan(Context c,String name)throws Exception {File dir=new File(Vault.root(c),"recovered");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Нет места для переноса");StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));return new File(dir,hash+".pkb");}
    private static void delete(File f)throws IOException{if(f.exists()&&!f.delete())throw new IOException("Защищённая копия проверена, но старый файл пока не удалён. Повторите перенос");}
    public static void migrateStream(File source,File target,javax.crypto.SecretKey key,boolean wav)throws Exception {
        if(!target.isFile()){
            if(target.getParentFile().getUsableSpace()<source.length()+32*1024*1024L)throw new IOException("Для безопасного переноса нужно свободное место размером с одну запись и запас 32 МБ");
            File part=new File(target.getParentFile(),target.getName()+".pending");
            try(ChunkedFile encrypted=new ChunkedFile(part,true,key)){
                byte[] b=new byte[65536];if(wav){try(LegacyWav legacy=new LegacyWav(source)){int n;while((n=legacy.read(b))!=-1)encrypted.append(b,0,n);}}else try(InputStream in=new FileInputStream(source)){int n;while((n=in.read(b))!=-1)encrypted.append(b,0,n);}encrypted.checkpoint();Arrays.fill(b,(byte)0);
            }
            compareStream(source,part,key,wav);Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
        compareStream(source,target,key,wav);
    }
    private static void compareStream(File source,File target,javax.crypto.SecretKey key,boolean wav)throws Exception {
        MessageDigest plain=MessageDigest.getInstance("SHA-256"),secure=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];long bytes=0;
        if(wav){try(LegacyWav legacy=new LegacyWav(source)){int n;while((n=legacy.read(b))!=-1){plain.update(b,0,n);bytes+=n;}}}else try(InputStream in=new FileInputStream(source)){int n;while((n=in.read(b))!=-1){plain.update(b,0,n);bytes+=n;}}
        try(ChunkedFile encrypted=new ChunkedFile(target,false,key)){if(encrypted.length()!=bytes)throw new IOException("Длина перенесённой записи отличается. Оригинал сохранён");long pos=0;int n;while((n=encrypted.read(pos,b,0,b.length))>0){secure.update(b,0,n);pos+=n;}}
        Arrays.fill(b,(byte)0);if(!MessageDigest.isEqual(plain.digest(),secure.digest()))throw new IOException("Проверка переноса не прошла. Оригинал сохранён");
    }
    private static void database(Context c)throws Exception {
        File old=c.getDatabasePath("voice.db"),target=new File(Vault.root(c),"voice.db");if(!old.isFile())return;
        byte[] password=Vault.databasePassword(c);
        try(SQLiteDatabase source=SQLiteDatabase.openDatabase(old.getAbsolutePath(),new byte[0],null,SQLiteDatabase.OPEN_READWRITE,Vault.KEEP_DATABASE,null)){
            try(Cursor checkpoint=source.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",new String[0])){while(checkpoint.moveToNext())if(checkpoint.getInt(0)!=0)throw new IOException("Старая база ещё занята. Перезапустите Pocket");}
            if(!target.exists()){
                File part=new File(Vault.root(c),"voice.db.pending");if(part.exists())delete(part);
                source.execSQL("PRAGMA temp_store=MEMORY");source.execSQL("ATTACH DATABASE ? AS protected KEY ?",new Object[]{part.getAbsolutePath(),new String(password,StandardCharsets.US_ASCII)});
                try {source.rawExecSQL("SELECT sqlcipher_export('protected')");source.execSQL("PRAGMA protected.user_version="+source.getVersion());}finally{source.execSQL("DETACH DATABASE protected");}
                try(SQLiteDatabase encrypted=SQLiteDatabase.openDatabase(part.getAbsolutePath(),password,null,SQLiteDatabase.OPEN_READWRITE,Vault.KEEP_DATABASE,null)){verifyDatabase(source,encrypted);}
                Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }
            try(SQLiteDatabase encrypted=SQLiteDatabase.openDatabase(target.getAbsolutePath(),password,null,SQLiteDatabase.OPEN_READWRITE,Vault.KEEP_DATABASE,null)){verifyDatabase(source,encrypted);}
        }finally{Arrays.fill(password,(byte)0);}
        for(String suffix:new String[]{"-wal","-shm","-journal",""})delete(new File(old.getAbsolutePath()+suffix));
    }
    private static void verifyDatabase(SQLiteDatabase a,SQLiteDatabase b)throws Exception {
        try(Cursor cursor=b.rawQuery("PRAGMA cipher_integrity_check",new String[0])){while(cursor.moveToNext())if(!cursor.getString(0).equalsIgnoreCase("ok"))throw new IOException("Проверка шифрования базы не прошла");}
        try(Cursor cursor=b.rawQuery("PRAGMA integrity_check",new String[0])){if(!cursor.moveToFirst()||!cursor.getString(0).equals("ok"))throw new IOException("Проверка базы не прошла");}
        if(a.getVersion()!=b.getVersion()||!MessageDigest.isEqual(digestDb(a),digestDb(b)))throw new IOException("Содержимое перенесённой базы отличается. Оригинал сохранён");
    }
    private static byte[] digestDb(SQLiteDatabase db)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(Cursor tables=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",new String[0])){while(tables.moveToNext()){String name=tables.getString(0);digest.update(name.getBytes(StandardCharsets.UTF_8));try(Cursor rows=db.rawQuery("SELECT * FROM \""+name.replace("\"","\"\"")+"\" ORDER BY rowid",new String[0])){while(rows.moveToNext()){digest.update((byte)127);for(int i=0;i<rows.getColumnCount();i++){int type=rows.getType(i);byte[] data=type==Cursor.FIELD_TYPE_BLOB?rows.getBlob(i):type==Cursor.FIELD_TYPE_NULL?new byte[0]:rows.getString(i).getBytes(StandardCharsets.UTF_8);digest.update((byte)type);digest.update(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());digest.update(data);}}}}}return digest.digest();
    }
    private static void chat(Context c)throws Exception {File source=new File(c.getFilesDir(),"chat.json");if(!source.exists())return;if(source.length()>12*1024*1024)throw new IOException("Старая история чата слишком велика. Оригинал сохранён");byte[] raw=Files.readAllBytes(source.toPath());new JSONObject(new String(raw,StandardCharsets.UTF_8));if(!new File(Vault.root(c),"chat.bin").exists())Vault.write(c,"chat.bin",raw);if(!MessageDigest.isEqual(raw,Vault.read(c,"chat.bin",12*1024*1024)))throw new IOException("Проверка истории чата не прошла");delete(source);}
    private static void preferences(Context c)throws Exception {SharedPreferences prefs=c.getSharedPreferences("server",Context.MODE_PRIVATE);JSONObject j=new JSONObject(prefs.getAll());if(!new File(Vault.root(c),"server.bin").exists()){if(!j.has("apiKey")){byte[] random=new byte[32];new SecureRandom().nextBytes(random);j.put("apiKey",android.util.Base64.encodeToString(random,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING));}Vault.write(c,"server.bin",j.toString().getBytes(StandardCharsets.UTF_8));}
        JSONObject sealed=new JSONObject(new String(Vault.read(c,"server.bin",65536),StandardCharsets.UTF_8));for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(!String.valueOf(entry.getValue()).equals(String.valueOf(sealed.get(entry.getKey()))))throw new IOException("Проверка настроек не прошла");if(!prefs.edit().clear().commit())throw new IOException("Не удалось очистить прежние настройки");File dir=new File(c.getDataDir(),"shared_prefs");delete(new File(dir,"server.xml"));delete(new File(dir,"server.xml.bak"));
    }
}
