package dev.pocketllama.security;
import android.content.Context;
import android.security.keystore.*;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.SecretKey;
import dev.pocketllama.photo.EncryptedFiles;

/** Device-bound encryption shared by data stores; app PIN is a separate UI access control. */
public final class Vault {
    private static final String ALIAS="pocket.private.photos.v1";
    public static final net.zetetic.database.DatabaseErrorHandler KEEP_DATABASE=(db,error)->{throw new IllegalStateException("База не открыта. Зашифрованные данные сохранены; автоматического удаления нет");};
    private Vault(){}
    public static File root(Context c){File f=new File(c.getNoBackupFilesDir(),"vault-v1");if(!f.isDirectory()&&!f.mkdirs())throw new IllegalStateException("Не удалось открыть защищённое хранилище");return f;}
    public static synchronized SecretKey key(Context c)throws Exception {
        try(RandomAccessFile f=new RandomAccessFile(new File(root(c),"key.lock"),"rw");FileLock lock=f.getChannel().lock()){
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
            // Never silently replace a lost key for an existing vault.
            File[] photos=new File(c.getNoBackupFilesDir(),"private-photos-v1").listFiles(File::isDirectory);
            if(new File(root(c),"pin.bin").exists()||new File(root(c),"ready.bin").exists()||new File(root(c),"database-key.bin").exists()||(photos!=null&&photos.length>0))throw new IOException("Ключ Android Keystore недоступен. Данные не удалены");
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return generator.generateKey();
        }
    }
    public static void write(Context c,String name,byte[] bytes)throws Exception {EncryptedFiles.write(new File(root(c),name),key(c),"vault/"+name,bytes);}
    public static byte[] read(Context c,String name,int limit)throws Exception {return EncryptedFiles.read(new File(root(c),name),key(c),"vault/"+name,limit);}
    public static boolean ready(Context c){return new File(root(c),"ready.bin").isFile();}
    public static void requireReady(Context c){if(!ready(c))throw new IllegalStateException("Сначала откройте Pocket, задайте код и завершите защиту данных");}
    public static File database(Context c){requireReady(c);return new File(root(c),"voice.db");}
    public static synchronized byte[] databasePassword(Context c){try{File f=new File(root(c),"database-key.bin");if(!f.exists()){if(new File(root(c),"voice.db").exists()||new File(root(c),"voice.db.pending").exists()||new File(root(c),"notes.db").exists()||new File(root(c),"notes.db-wal").exists())throw new IOException("Ключ существующей базы недоступен");byte[] b=new byte[32];new SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));write(c,"database-key.bin",s.toString().getBytes(StandardCharsets.US_ASCII));Arrays.fill(b,(byte)0);}return read(c,"database-key.bin",128);}catch(Exception e){throw new IllegalStateException("Не удалось открыть ключ базы. Данные сохранены",e);}}
    public static File audio(Context c,String id){if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Некорректная запись");File dir=new File(root(c),"audio");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Нет места для аудио");return new File(dir,id+".pka");}
}
