package android.content;
import java.io.*;
public final class Context {
    private final File files;private final android.content.res.AssetManager assets;
    public Context(File files,File models){this.files=files;this.assets=new android.content.res.AssetManager(models);}
    public Context getApplicationContext(){return this;}
    public File getFilesDir(){return files;}
    public android.content.res.AssetManager getAssets(){return assets;}
}
