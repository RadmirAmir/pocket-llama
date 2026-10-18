package android.content;
import java.io.File;
public class Context {
 private final File root;public final android.app.ActivityManager manager=new android.app.ActivityManager();
 public Context(File root){this.root=root;root.mkdirs();}public Context getApplicationContext(){return this;}public File getNoBackupFilesDir(){return root;}public String getPackageName(){return "dev.pocketllama.memoryfix";}
 public <T>T getSystemService(Class<T> type){return type.cast(manager);}
}
