package android.content;
import java.io.File;
import android.content.pm.ApplicationInfo;
public final class Context {
 private final File files,cache;private final ApplicationInfo info=new ApplicationInfo();
 public Context(File root,String libraries){files=new File(root,"files");cache=new File(root,"cache");files.mkdirs();cache.mkdirs();info.nativeLibraryDir=libraries;}
 public ApplicationInfo getApplicationInfo(){return info;}public File getFilesDir(){return files;}public File getCacheDir(){return cache;}
}
