package dev.pocketllama.photo;
import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Pinned public model weights. Private images are never sent to the download host. */
public final class SuryaAssets {
    public static final String REVISION="6a3a4c30e5e74446d4f8b6afd05b2f2da970f470";
    public static final String MODEL_SHA="1f18abe17b1ed8b4e47ee9b1ad0e274c93daf5efbb6b29a04ff1712e37051e05";
    private static final String PROJECTOR_SHA="98c0563673b1657ff6d021d1e5f04af06cbf61bb40c63ac613e8bb71b42fb2c0";
    public static final long TOTAL=1266400864L+204986688L;
    private final Context c;private volatile boolean cancelled;private volatile HttpURLConnection active;
    public interface Progress {void update(long bytes,long total,String stage)throws Exception;}
    public SuryaAssets(Context c){this.c=c.getApplicationContext();}
    public File root(){return new File(c.getFilesDir(),"surya-2");}
    public File model(){return new File(root(),"models/surya-2.gguf");}
    public File projector(){return new File(root(),"projectors/surya-2-mmproj.gguf");}
    public JSONObject status()throws Exception {boolean allowed=dev.pocketllama.DistributionConfig.surya(c);long n=Math.min(1266400864L,bytes(model()))+Math.min(204986688L,bytes(projector()));return new JSONObject().put("available",allowed).put("bundled",allowed&&bundled()).put("ready",allowed&&verified(model(),1266400864L,MODEL_SHA)&&verified(projector(),204986688L,PROJECTOR_SHA)).put("bytes",n).put("total",TOTAL).put("revision",REVISION);}
    private boolean bundled(){try{String[] files=c.getAssets().list("surya/bundled");return files!=null&&Arrays.asList(files).contains("surya-2.gguf")&&Arrays.asList(files).contains("surya-2-mmproj.gguf");}catch(Exception e){return false;}}
    private long bytes(File f){return f.isFile()?f.length():new File(f.getPath()+".part").length();}
    private boolean verified(File f,long size,String hash){File stamp=new File(f.getPath()+".verified");if(!f.isFile()||f.length()!=size||!stamp.isFile())return false;try{return new String(Files.readAllBytes(stamp.toPath()),java.nio.charset.StandardCharsets.US_ASCII).equals(hash+":"+f.lastModified());}catch(Exception e){return false;}}
    public File prepare(Progress p)throws Exception {if(!dev.pocketllama.DistributionConfig.surya(c))throw new IOException("Расширенный OCR недоступен в этой сборке");root().mkdirs();if(!root().isDirectory())throw new IOException("Нет места для Surya");long remaining=Math.max(0,TOTAL-Math.min(1266400864L,bytes(model()))-Math.min(204986688L,bytes(projector())));if(root().getUsableSpace()<remaining+512L*1024*1024)throw new IOException("Для подготовки Surya нужно ещё "+((remaining+512L*1024*1024)/1048576)+" МБ свободного места");download(model(),1266400864L,MODEL_SHA,0,p);download(projector(),204986688L,PROJECTOR_SHA,1266400864L,p);return model();}
    public void cancel(){cancelled=true;HttpURLConnection h=active;if(h!=null)h.disconnect();}
    private void check()throws InterruptedException {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private void download(File target,long size,String hash,long base,Progress progress)throws Exception {
        check();if(verified(target,size,hash))return;target.getParentFile().mkdirs();File partial=new File(target.getPath()+".part");
        if(target.isFile()&&target.length()==size){progress.update(base+size,TOTAL,"Проверяем контрольную сумму");if(digest(target).equals(hash)){stamp(target,hash);return;}if(!target.delete())throw new IOException("Не удалось заменить неверный пакет Surya");}
        if(bundled()){
            progress.update(base,TOTAL,"Подготавливаем встроенный OCR");
            try(InputStream in=c.getAssets().open("surya/bundled/"+target.getName());FileOutputStream out=new FileOutputStream(partial)){
                byte[] buffer=new byte[262144];long copied=0,last=0;int n;while((n=in.read(buffer))!=-1){check();if(copied+n>size)throw new IOException("Неверный размер встроенной Surya");out.write(buffer,0,n);copied+=n;long now=android.os.SystemClock.elapsedRealtime();if(now-last>700){progress.update(base+copied,TOTAL,"Подготавливаем встроенный OCR");last=now;}}out.getFD().sync();
            }
            check();if(partial.length()!=size||!digest(partial).equals(hash)){partial.delete();throw new IOException("Встроенный пакет Surya повреждён. Установите обновление поверх приложения");}
            Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);stamp(target,hash);return;
        }
        if(partial.length()>size&&!partial.delete())throw new IOException("Не удалось сбросить незавершённую загрузку");
        if(root().getUsableSpace()<size-partial.length()+256L*1024*1024)throw new IOException("Для пакета Surya нужно ещё "+((size-partial.length()+256L*1024*1024)/1048576)+" МБ свободного места");
        Exception failure=null;
        for(int attempt=0;attempt<4&&partial.length()<size;attempt++){
            check();long offset=partial.length();HttpURLConnection h=(HttpURLConnection)new URL("https://huggingface.co/datalab-to/surya-ocr-2-gguf/resolve/"+REVISION+"/"+target.getName()).openConnection();active=h;
            try{h.setConnectTimeout(20000);h.setReadTimeout(30000);h.setRequestProperty("Accept-Encoding","identity");h.setRequestProperty("User-Agent","PocketLlama/0.8.0");if(offset>0)h.setRequestProperty("Range","bytes="+offset+"-");int code=h.getResponseCode();
                if(code==206){String range=h.getHeaderField("Content-Range");if(range==null||!range.startsWith("bytes "+offset+"-")||!range.endsWith("/"+size))throw new IOException("Сервер вернул неверный диапазон модели");}
                else if(code==200){offset=0;}else throw new IOException("Загрузка Surya: HTTP "+code);
                try(InputStream in=h.getInputStream();FileOutputStream out=new FileOutputStream(partial,offset>0)){byte[] buffer=new byte[256*1024];int n;long done=offset,last=0;while((n=in.read(buffer))!=-1){check();if(done+n>size)throw new IOException("Неверный размер модели Surya");out.write(buffer,0,n);done+=n;long now=android.os.SystemClock.elapsedRealtime();if(now-last>700){progress.update(base+done,TOTAL,"Загружаем Surya OCR 2");last=now;}}out.getFD().sync();if(done!=size)throw new EOFException("Загрузка модели прервалась; повтор продолжит её");}
            }catch(Exception e){failure=e;check();}finally{active=null;h.disconnect();}
        }
        check();if(partial.length()!=size)throw new IOException("Не удалось загрузить Surya. Повтор продолжит загрузку.",failure);
        progress.update(base+size,TOTAL,"Проверяем контрольную сумму");if(!digest(partial).equals(hash)){partial.delete();throw new IOException("Контрольная сумма Surya не совпала. Повторите загрузку");}
        Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);stamp(target,hash);
    }
    private String digest(File f)throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1){check();d.update(b,0,n);}}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private void stamp(File f,String hash)throws Exception {File dest=new File(f.getPath()+".verified"),temp=new File(dest.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write((hash+":"+f.lastModified()).getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.getFD().sync();}Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    public static File detector(Context c)throws Exception {
        JSONObject m;try(InputStream in=c.getAssets().open("surya/manifest.json")){m=new JSONObject(new String(dev.pocketllama.core.TextAttachment.readLimited(in,64000),java.nio.charset.StandardCharsets.UTF_8));}
        File dir=new File(c.getFilesDir(),"surya-2/detector");dir.mkdirs();File dest=new File(dir,m.getString("file"));File stamp=new File(dest.getPath()+".verified");String expected=m.getString("sha256");
        if(dest.isFile()&&dest.length()==m.getLong("bytes")&&stamp.isFile()&&new String(Files.readAllBytes(stamp.toPath()),java.nio.charset.StandardCharsets.US_ASCII).equals(expected+":"+dest.lastModified()))return dest;
        File tmp=new File(dest.getPath()+".part");MessageDigest hash=MessageDigest.getInstance("SHA-256");long size=0;try(InputStream in=c.getAssets().open("surya/"+m.getString("file"));FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();out.write(b,0,n);hash.update(b,0,n);size+=n;}out.getFD().sync();}
        StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));if(size!=m.getLong("bytes")||!actual.toString().equals(expected)){tmp.delete();throw new IOException("Повреждён детектор Surya. Установите APK заново поверх приложения");}Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);new SuryaAssets(c).stamp(dest,expected);return dest;
    }
}
