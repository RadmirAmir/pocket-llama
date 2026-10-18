package dev.pocketllama.video;
import android.content.Context;
import java.io.File;
public final class VideoImportService{
 public static File media(Context c,String id){return new File(dev.pocketllama.security.Vault.root(c),id+".media");}
 public static boolean alive(Context c){return false;}
}
