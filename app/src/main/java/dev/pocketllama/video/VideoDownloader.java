package dev.pocketllama.video;

import dev.pocketllama.security.ChunkedFile;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKey;

/** stdout is media bytes, stderr stays bounded in RAM. Never combine the streams. */
public final class VideoDownloader {
    public static final long MAX_BYTES=512L*1024*1024,MAX_SECONDS=6*3600;
    public interface Check{void check(long bytes,long total)throws Exception;}
    private final VideoRuntime runtime;private volatile Process process;private volatile boolean cancelled;
    public VideoDownloader(VideoRuntime runtime){this.runtime=runtime;}
    public synchronized void cancel(){cancelled=true;Process p=process;if(p!=null)terminate(p);}
    private static void terminate(Process p){p.destroy();try{if(!p.waitFor(750,TimeUnit.MILLISECONDS))p.destroyForcibly();}catch(InterruptedException e){p.destroyForcibly();Thread.currentThread().interrupt();}}
    private synchronized Process begin(List<String> args)throws Exception{if(cancelled)throw new InterruptedException();process=runtime.start(args);return process;}
    private void guard(Check check,long bytes,long total)throws Exception{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException();check.check(bytes,total);}
    public JSONObject inspect(String url,Check check)throws Exception {
        Process p=begin(Arrays.asList("--skip-download","--dump-single-json","--",VideoUrl.parse(url)));Errors errors=new Errors(p.getErrorStream());errors.start();
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();ScheduledFuture<?> timeout=timer.schedule(()->terminate(p),150,TimeUnit.SECONDS);
        try(InputStream in=p.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1){guard(check,0,0);if(out.size()+n>16*1024*1024)throw new IOException("Метаданные ролика слишком большие");out.write(bytes,0,n);}
            guard(check,0,0);int exit=p.waitFor();if(exit!=0)throw errors.failure("VIDEO_METADATA",exit);JSONObject info=new JSONObject(out.toString("UTF-8"));validateInfo(info);return info;
        }finally{timeout.cancel(false);timer.shutdownNow();terminate(p);process=null;errors.join(1000);}
    }
    public static void validateInfo(JSONObject info)throws Exception {
        String type=info.optString("_type","video"),live=info.optString("live_status");
        if(!type.equals("video"))throw new IOException("Добавляйте ссылку на один ролик, а не на канал или плейлист");
        if(info.optBoolean("is_live")||live.equals("is_live")||live.equals("is_upcoming")||info.optBoolean("is_upcoming"))throw new IOException("Дождитесь завершения прямой трансляции");
        double seconds=info.optDouble("duration",0);if(seconds>MAX_SECONDS)throw new IOException("Ролик длиннее шести часов");
        if(info.optLong("filesize",0)>MAX_BYTES)throw new IOException("Аудиодорожка больше 512 МБ");
    }
    public void download(String url,File target,SecretKey key,long total,Check check)throws Exception {
        File partial=new File(target.getParentFile(),target.getName()+".part");boolean committed=false;
        Process p=begin(Arrays.asList("--no-progress","--no-part","--max-filesize",String.valueOf(MAX_BYTES),"--match-filter","!is_live & !is_upcoming & duration <=? 21600","--output","-","--",VideoUrl.parse(url)));Errors errors=new Errors(p.getErrorStream());errors.start();
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();ScheduledFuture<?> timeout=timer.schedule(()->terminate(p),2,TimeUnit.HOURS);
        try {
            try(PushbackInputStream in=new PushbackInputStream(p.getInputStream(),512);ChunkedFile encrypted=new ChunkedFile(partial,true,key)){
                rejectHtml(in);
                byte[] buffer=new byte[65536];long bytes=0,checkpoint=0;int n;
                try{while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>MAX_BYTES)throw new IOException("Достигнут предел аудиодорожки: 512 МБ");guard(check,bytes,total);encrypted.append(buffer,0,n);if(bytes-checkpoint>=2*1024*1024){encrypted.checkpoint();checkpoint=bytes;}}}finally{Arrays.fill(buffer,(byte)0);}
                guard(check,bytes,total);int exit=p.waitFor();if(exit!=0)throw errors.failure("VIDEO_AUDIO",exit);if(bytes<1024)throw new IOException("Платформа не отдала аудиодорожку [VIDEO_AUDIO_EMPTY]");encrypted.checkpoint();
            }
            if(!partial.renameTo(target))throw new IOException("Не удалось сохранить зашифрованное аудио");committed=true;
        }finally{timeout.cancel(false);timer.shutdownNow();terminate(p);process=null;errors.join(1000);if(!committed)partial.delete();}
    }
    private static void rejectHtml(PushbackInputStream in)throws IOException {
        byte[] preview=new byte[512];int count=0;
        try {
            while(count<preview.length){int n=in.read(preview,count,preview.length-count);if(n<0)break;count+=n;}
            in.unread(preview,0,count);
            String start=new String(preview,0,count,java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF","").trim().toLowerCase(Locale.ROOT);
            if(start.startsWith("<!doctype html")||start.startsWith("<html"))throw new IOException("Платформа или сеть вернула веб-страницу вместо аудио. Проверьте доступность видео и соединение [VIDEO_AUDIO_HTTP]");
        }finally{Arrays.fill(preview,(byte)0);}
    }
    private static final class Errors extends Thread {
        private final InputStream in;private final StringBuilder text=new StringBuilder();
        Errors(InputStream in){super("video-errors");this.in=in;setDaemon(true);}
        @Override public void run(){try(Reader reader=new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)){char[] chars=new char[2048];int n;while((n=reader.read(chars))!=-1){synchronized(text){text.append(chars,0,n);if(text.length()>16384)text.delete(0,text.length()-16384);}}}catch(IOException ignored){}}
        IOException failure(String stage,int exit)throws InterruptedException{
            // waitFor() can beat the stderr reader; collect its final error first.
            join(1000);String raw;synchronized(text){raw=text.toString();}String value=raw.toLowerCase(Locale.ROOT);
            String message=value.contains("no such option")||value.contains("modulenotfounderror")||value.contains("importerror")||value.contains("fatal python error")?"Не удалось запустить встроенный загрузчик. Обновите Pocket":value.contains("drm")?"Ролик защищён DRM и недоступен для импорта":value.contains("sign in")||value.contains("login")||value.contains("private video")||value.contains("cookies")?"Платформа требует вход или ограничила доступ. Попробуйте другой публичный ролик":value.contains("unsupported url")?"Эту ссылку yt-dlp пока не поддерживает":value.contains("403")||value.contains("429")||value.contains("bot")?"Платформа ограничила загрузку. Повторите позже или попробуйте другую сеть":value.contains("not available")||value.contains("unavailable")||value.contains("404")?"Ролик недоступен или удалён":value.contains("timed out")||value.contains("network")||value.contains("resolve")?"Не удалось связаться с платформой. Проверьте сеть и повторите":value.contains("js runtime")||value.contains("challenge")?"Не удалось разобрать защиту платформы. Может потребоваться обновление Pocket":"Не удалось получить аудио. Проверьте доступность ссылки; загрузчик может требовать обновления";
            String detail=safeDetail(raw);
            return new IOException(message+" ["+stage+", yt-dlp "+exit+"]"+(detail.isEmpty()?"":" · "+detail));
        }
        private static String safeDetail(String raw){
            String detail="";for(String line:raw.split("\\r?\\n"))if(!line.trim().isEmpty())detail=line.trim();
            detail=detail.replaceAll("\\x1B\\[[0-?]*[ -/]*[@-~]","").replaceAll("[\\p{Cntrl}]"," ");
            int error=detail.lastIndexOf("ERROR");if(error>=0)detail=detail.substring(error);
            detail=detail.replaceAll("(?i)https?://\\S+","[ссылка]")
                .replaceAll("(?i)(authorization|cookie|set-cookie)\\s*:.*","$1: [скрыто]")
                .replaceAll("(?i)((?:token|signature|sig|key|auth|password|secret)\\s*[:=]\\s*)[^\\s&]+","$1[скрыто]")
                .replaceAll("(?<![\\p{L}\\p{N}])/(?:[^\\s\"'<>])+","[файл]");
            return detail.length()>240?detail.substring(0,240)+"…":detail;
        }
    }
}
