package dev.pocketllama.security;
import android.content.Context;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
/** Synthetic test keys only. This class never enters a product APK. */
public final class Vault {
 public static final net.zetetic.database.DatabaseErrorHandler KEEP_DATABASE=(db,error)->{throw new IllegalStateException("Test database preserved",error);};
 public static File root(Context c){File f=new File(c.getNoBackupFilesDir(),"vault");f.mkdirs();return f;}
 public static File database(Context c){return new File(root(c),"voice.db");}
 public static byte[] databasePassword(Context c){return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);}
 public static SecretKey key(Context c){return new SecretKeySpec(new byte[32],"AES");}
 public static File audio(Context c,String id){return new File(root(c),id+".pka");}
}
