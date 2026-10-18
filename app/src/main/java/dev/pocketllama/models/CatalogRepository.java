package dev.pocketllama.models;

import android.content.Context;
import android.os.StatFs;
import dev.pocketllama.*;
import dev.pocketllama.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.json.*;

public final class CatalogRepository {
    private final Context context;
    public CatalogRepository(Context c){context=c.getApplicationContext();}
    public File root(){File d=new File(context.getFilesDir(),"model-downloads");d.mkdirs();return d;}
    public File partial(ModelCatalog.Entry e){return new File(root(),e.sha256+".part");}
    public File target(ModelCatalog.Entry e){File d=new File(context.getFilesDir(),"models");d.mkdirs();return new File(d,e.filename);}
    public ModelCatalog load()throws Exception {
        ModelCatalog bundled=verify(asset("model-catalog/catalog.signed.json"));File cache=new File(root(),"catalog.signed.json");
        if(cache.isFile())try(InputStream in=new FileInputStream(cache)){ModelCatalog downloaded=verify(ModelCatalog.read(in,ModelCatalog.MAX_BYTES));if(downloaded.revision>=bundled.revision)return downloaded;}catch(Exception ignored){}
        return bundled;
    }
    private byte[] asset(String name)throws Exception{try(InputStream in=context.getAssets().open(name)){return ModelCatalog.read(in,ModelCatalog.MAX_BYTES);}}
    private ModelCatalog verify(byte[] data)throws Exception{return ModelCatalog.verify(data,new String(asset("model-catalog/public-key.txt"),StandardCharsets.US_ASCII));}
    public void refresh()throws Exception {
        String address=DistributionConfig.get(context).optString("catalogUrl");if(address.isEmpty())throw new IOException("Каталог обновляется вместе с этой версией Pocket");
        URL url=new URL(address);if(!url.getProtocol().equals("https")||url.getUserInfo()!=null)throw new IOException("Для каталога нужен HTTPS");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept-Encoding","identity");
        try{if(c.getResponseCode()!=200||!c.getURL().getProtocol().equals("https"))throw new IOException("Каталог временно недоступен");byte[] bytes;try(InputStream in=c.getInputStream()){bytes=ModelCatalog.read(in,ModelCatalog.MAX_BYTES);}ModelCatalog next=verify(bytes);if(next.revision<load().revision)throw new IOException("Сервер вернул старую версию каталога");write(new File(root(),"catalog.signed.json"),bytes);}finally{c.disconnect();}
    }
    public boolean installed(ModelCatalog.Entry e){File f=target(e),stamp=new File(root(),e.sha256+".verified");if(!f.isFile()||f.length()!=e.bytes||!stamp.isFile())return false;try{return new String(Files.readAllBytes(stamp.toPath()),StandardCharsets.US_ASCII).equals(e.sha256+":"+f.lastModified());}catch(Exception x){return false;}}
    public void markInstalled(ModelCatalog.Entry e)throws Exception {File f=target(e);if(!f.isFile()||f.length()!=e.bytes)throw new IOException("Модель не установлена");write(new File(root(),e.sha256+".verified"),(e.sha256+":"+f.lastModified()).getBytes(StandardCharsets.US_ASCII));}
    public JSONObject snapshot()throws Exception {
        ModelCatalog catalog=load();MemorySnapshot memory=MemorySnapshot.capture(context);int code=DistributionConfig.code(context);ModelCatalog.Entry recommended=catalog.recommend(memory.totalBytes,memory.availableBytes,code);
        long free=new StatFs(root().getPath()).getAvailableBytes();String selectedName=new ConfigStore(context).load().modelName;JSONArray entries=new JSONArray();
        for(ModelCatalog.Entry e:catalog.entries){boolean installed=installed(e);long partial=Math.min(e.bytes,partial(e).length());String warning=e.minAppCode>code?"Обновите приложение":memory.totalBytes<e.minRamBytes?"Для этой модели нужно больше оперативной памяти":memory.availableBytes<e.workingBytes?"Перед запуском освободите оперативную память":"";
            entries.put(new JSONObject().put("id",e.id).put("title",e.title).put("description",e.description).put("tier",e.tier).put("bytes",e.bytes).put("partial",partial).put("installed",installed).put("selected",selectedName.equals(e.filename)).put("recommended",recommended!=null&&recommended.id.equals(e.id)).put("compatible",code>=e.minAppCode&&memory.totalBytes>=e.minRamBytes).put("hasSpace",installed||free>=e.bytes-partial+256L*1024*1024).put("warning",warning).put("source",e.source).put("license",e.license).put("context",e.context));
        }
        return new JSONObject().put("revision",catalog.revision).put("models",entries).put("download",ModelDownloadService.snapshot(context)).put("freeBytes",free).put("refreshAvailable",!DistributionConfig.get(context).optString("catalogUrl").isEmpty());
    }
    /** Caller must hold the UI's idle-model guard. Selecting never starts inference by itself. */
    public void activate(String id)throws Exception {
        ModelCatalog.Entry e=load().find(id);if(e.minAppCode>DistributionConfig.code(context))throw new IOException("Обновите Pocket для этой модели");
        if(!installed(e))throw new IOException("Сначала завершите загрузку модели");
        File file=target(e);GgufMetadata meta=GgufReader.read(file);if(!meta.architecture().equals(e.architecture))throw new IOException("Архитектура модели не совпала с каталогом");
        MemorySnapshot memory=MemorySnapshot.capture(context);if(memory.totalBytes<e.minRamBytes)throw new IOException("Для этой модели недостаточно оперативной памяти");
        ConfigStore store=new ConfigStore(context);ServerConfig c=store.load();c.modelName=e.filename;c.projectorName="";c.backend="cpu";c.contextPerSlot=e.context;c.slots=1;c.threads=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()));c.cacheK="f16";c.cacheV="f16";c.mmap=true;c.budgetMiB=0;c.systemReserveMiB=1536;c.validate();store.save(c);
    }
    static void write(File target,byte[] bytes)throws Exception {
        File part=new File(target.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(part)){out.write(bytes);out.getFD().sync();}
        Files.move(part.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
