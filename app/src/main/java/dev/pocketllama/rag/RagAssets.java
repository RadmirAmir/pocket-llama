package dev.pocketllama.rag;

import android.content.Context;
import dev.pocketllama.models.VerifiedDownload;
import dev.pocketllama.security.Vault;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Optional, pinned download. Never uses the chat-model catalog or sends user text to Hugging Face. */
public final class RagAssets {
    private final Context context;
    public final JSONObject model;
    private static volatile String checked="";
    public RagAssets(Context c)throws Exception {context=c.getApplicationContext();try(InputStream in=c.getAssets().open("rag/model.json")){model=new JSONObject(new String(dev.pocketllama.core.TextAttachment.readLimited(in,16384),StandardCharsets.UTF_8));}if(!RagText.VERSION.equals(model.getString("id")))throw new IOException("Unknown embedding model");}
    public File file()throws Exception {File dir=new File(context.getNoBackupFilesDir(),"embedding-models");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Нет места для модели поиска");return new File(dir,model.getString("name"));}
    public boolean ready()throws Exception {File f=file();return f.isFile()&&f.length()==model.getLong("bytes")&&new File(Vault.root(context),"rag-model-receipt.bin").isFile();}
    public File verified(RagSources.Check check)throws Exception {
        if(!ready())throw new IOException("Сначала скачайте модель базы знаний в инструментах чата");
        String receipt=new String(Vault.read(context,"rag-model-receipt.bin",1024),StandardCharsets.UTF_8);if(!receipt.equals(model.getString("sha256")))throw new IOException("Повторите загрузку модели поиска");
        File f=file();String stamp=f.getAbsolutePath()+":"+f.length()+":"+f.lastModified();
        if(!checked.equals(stamp)){if(!new VerifiedDownload().sha256(f,check::run).equals(receipt))throw new IOException("Модель поиска повреждена. Повторите загрузку");checked=stamp;}
        return f;
    }
    public void download(VerifiedDownload download,VerifiedDownload.Progress progress,RagSources.Check check)throws Exception {
        if(!new File(Vault.root(context),"rag-terms-v1.bin").isFile())throw new IOException("Примите условия Gemma перед загрузкой");
        File target=file();String url="https://huggingface.co/"+model.getString("repository")+"/resolve/"+model.getString("revision")+"/"+model.getString("name");
        download.transfer(url,model.getLong("bytes"),model.getString("sha256"),new File(target.getParentFile(),target.getName()+".part"),target,progress,check::run);
        check.run();Vault.write(context,"rag-model-receipt.bin",model.getString("sha256").getBytes(StandardCharsets.US_ASCII));checked="";
    }
    public static void accept(Context c)throws Exception {Vault.write(c,"rag-terms-v1.bin",("Gemma Terms of Use / Gemma Prohibited Use Policy\nAccepted: "+System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));}
    public static String terms(Context c)throws Exception {StringBuilder out=new StringBuilder();for(String name:new String[]{"Gemma.txt","Gemma-terms.txt","Gemma-prohibited-uses.txt"})try(InputStream in=c.getAssets().open("notices/"+name)){out.append(new String(dev.pocketllama.core.TextAttachment.readLimited(in,65536),StandardCharsets.UTF_8)).append("\n\n");}return out.toString();}
}
