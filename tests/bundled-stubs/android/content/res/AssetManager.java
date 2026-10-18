package android.content.res;
import java.io.*;
public final class AssetManager {
    private final File models;
    public AssetManager(File models){this.models=models;}
    public InputStream open(String name)throws IOException {if(!name.startsWith("surya/bundled/")||name.contains(".."))throw new IOException("Unexpected test asset");return new FileInputStream(new File(models,name.substring("surya/bundled/".length())));}
    public String[] list(String name)throws IOException {if(!name.equals("surya/bundled"))throw new IOException("Unexpected test asset directory");return models.list();}
}
