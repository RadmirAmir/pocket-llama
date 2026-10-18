package android.content;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import java.io.File;
/** Host-only adapter. Does not emulate Android's native linker or media codecs. */
public final class Context {
 private final File base;private final ApplicationInfo info=new ApplicationInfo();private final AssetManager assets;
 public Context(File base,File libs,File assetDir){this.base=base;info.nativeLibraryDir=libs.getAbsolutePath();assets=new AssetManager(assetDir);}
 public File getNoBackupFilesDir(){return base;}
 public ApplicationInfo getApplicationInfo(){return info;}
 public AssetManager getAssets(){return assets;}
}
