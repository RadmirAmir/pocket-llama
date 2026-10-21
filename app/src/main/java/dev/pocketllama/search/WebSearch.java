package dev.pocketllama.search;

import android.content.Context;
import dev.pocketllama.video.VideoRuntime;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.json.*;

/** Real bundled SearXNG in a killable CPython child; listener and caches live only during lookup. */
public final class WebSearch implements AutoCloseable {
    private final Context context;
    private final WebPrompt.Check access;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile Process process;
    private volatile HttpURLConnection connection;
    private int port;
    private String token;
    private boolean watching;
    private final ScheduledExecutorService guardian=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"pocket-search-guard");t.setDaemon(true);return t;});
    public WebSearch(Context context,WebPrompt.Check access){this.context=context.getApplicationContext();this.access=access;}
    private void check()throws Exception {if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedException("Ответ остановлен");access.run();}
    public JSONObject search(String raw,String language)throws Exception {return search(raw,language,"auto");}
    /** One child and its connection pools per agent turn; close before answer generation. */
    public synchronized JSONObject search(String raw,String language,String mode)throws Exception {
        String query=WebQuery.validate(raw);check();
        if(!watching){watching=true;guardian.scheduleWithFixedDelay(()->{try{check();}catch(Exception e){cancel();}},0,100,TimeUnit.MILLISECONDS);}
        try {
            ensureStarted();check();
            connection=(HttpURLConnection)new URL("http://127.0.0.1:"+port+"/search").openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(1500);connection.setReadTimeout(24000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] body=new JSONObject().put("query",query).put("language","en".equals(language)?"en":"ru").put("mode",mode).toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(body.length);
            check();try(OutputStream out=connection.getOutputStream()){out.write(body);}check();
            int code=connection.getResponseCode();
            if(code!=200){
                JSONObject failure;try(InputStream in=connection.getErrorStream()){failure=new JSONObject(new String(read(in,8192),StandardCharsets.UTF_8));}catch(Exception ignored){failure=new JSONObject();}
                if(code==502){
                    String error="search_internal_error".equals(failure.optString("error"))?"search_internal_error":"engines_unavailable";
                    check();return new JSONObject().put("sources",new JSONArray()).put("error",error).put("noEvidence",true).put("retryable",false)
                        .put("message",SearchErrors.describe(failure,language)).put("diagnostics",SearchErrors.diagnostics(failure));
                }
                throw new IOException(SearchErrors.describe(failure,language));
            }
            ByteArrayOutputStream response=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){check();if(response.size()+n>128*1024)throw new IOException("Слишком большой ответ SearXNG");response.write(b,0,n);}}
            check();JSONObject result=new JSONObject(response.toString("UTF-8"));JSONArray hits=result.getJSONArray("sources");if(hits.length()>6)throw new IOException("Invalid SearXNG result");
            for(int i=0;i<hits.length();i++){JSONObject hit=hits.getJSONObject(i);if(!WebQuery.publicUrl(hit.optString("url"))||hit.optString("text").length()>1200||hit.optString("title").length()>240||hit.optString("published").length()>80||hit.optString("publisher").length()>120)throw new IOException("Invalid SearXNG source");}
            result.put("diagnostics",SearchErrors.diagnostics(result));
            if(hits.length()==0)result.put("message",SearchErrors.describe(result,language));
            return result;
        }catch(Exception e){close();throw e;}
        finally{HttpURLConnection active=connection;connection=null;if(active!=null)active.disconnect();}
    }
    private void ensureStarted()throws Exception {
            if(process!=null&&process.isAlive())return;
            File base=prepare();check();VideoRuntime runtime=VideoRuntime.prepare(context);check();
            ProcessBuilder builder=runtime.pythonProcess(new File(base,"server.py"),Collections.emptyList());
            builder.environment().put("PYTHONPATH",new File(base,"packages").getAbsolutePath());
            builder.directory(base);
            process=builder.start();check();
            // Readiness and all engine output stay in RAM, outside diagnostics and history.
            FutureTask<String> ready=new FutureTask<>(()->{
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line=reader.readLine();return line!=null&&line.length()<4096?line:"";}
            });Thread reader=new Thread(ready,"pocket-search-ready");reader.setDaemon(true);reader.start();
            Thread stderr=new Thread(()->{try(InputStream in=process.getErrorStream()){byte[] b=new byte[4096];while(in.read(b)!=-1){}}catch(IOException ignored){}},"pocket-search-errors");stderr.setDaemon(true);stderr.start();
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder secret=new StringBuilder();for(byte b:bytes)secret.append(String.format(Locale.ROOT,"%02x",b&255));token=secret.toString();
            try(OutputStream stdin=process.getOutputStream()){stdin.write((new JSONObject().put("token",secret.toString()).toString()+"\n").getBytes(StandardCharsets.UTF_8));}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);String line;
            while(true){check();try{line=ready.get(100,TimeUnit.MILLISECONDS);break;}catch(TimeoutException e){if(System.nanoTime()>deadline)throw new IOException("Не удалось запустить встроенный SearXNG. Установите обновление Pocket");}}
            check();JSONObject state;try{state=new JSONObject(line);}catch(JSONException e){throw new IOException("Не удалось запустить встроенный SearXNG. Установите обновление Pocket",e);}port=state.optInt("port");
            if(!state.optBoolean("ready")||port<1024||port>65535)throw new IOException("Не удалось запустить встроенный SearXNG. Установите обновление Pocket");
    }
    private File prepare()throws Exception {
        File base=new File(context.getNoBackupFilesDir(),"search-runtime-v1");if(!base.isDirectory()&&!base.mkdirs())throw new IOException("Нет места для встроенного поиска");
        try(RandomAccessFile lock=new RandomAccessFile(new File(base,"runtime.lock"),"rw");java.nio.channels.FileLock held=lock.getChannel().lock()){
            String stamp;try(InputStream in=context.getAssets().open("search/runtime-id.txt")){stamp=new String(read(in,256),StandardCharsets.UTF_8).trim();}
            if(!stamp.matches("[0-9a-f]{64}"))throw new IOException("Invalid search runtime");File marker=new File(base,"ready.txt"),packages=new File(base,"packages");
            String old=marker.isFile()?new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8):"";
            if(!stamp.equals(old)||!new File(packages,"searx/__init__.py").isFile()){
                MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=context.getAssets().open("search/python.zip")){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){check();hash.update(b,0,n);}}
                StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));if(!stamp.equals(actual.toString()))throw new IOException("Invalid search runtime checksum");
                marker.delete();remove(packages);packages.mkdirs();long total=0;String prefix=packages.getCanonicalPath()+File.separator;
                try(ZipInputStream zip=new ZipInputStream(context.getAssets().open("search/python.zip"))){
                    ZipEntry entry;byte[] buffer=new byte[65536];while((entry=zip.getNextEntry())!=null){check();File target=new File(packages,entry.getName());if(!target.getCanonicalPath().startsWith(prefix))throw new IOException("Invalid search archive");if(entry.isDirectory()){target.mkdirs();continue;}target.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(target)){int n;while((n=zip.read(buffer))!=-1){check();total+=n;if(total>128L*1024*1024)throw new IOException("Oversized search runtime");out.write(buffer,0,n);}}}
                }
                Files.write(marker.toPath(),stamp.getBytes(StandardCharsets.UTF_8));
            }
            for(String name:new String[]{"server.py"})try(InputStream in=context.getAssets().open("search/"+name)){Files.write(new File(base,name).toPath(),read(in,128*1024));}
        }
        return base;
    }
    private static byte[] read(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Oversized search program");out.write(b,0,n);}return out.toByteArray();}
    private static void remove(File file)throws IOException{File[] children=file.listFiles();if(children!=null)for(File c:children)remove(c);if(file.exists()&&!file.delete())throw new IOException("Cannot update search runtime");}
    public void cancel(){cancelled.set(true);Process p=process;if(p!=null)p.destroyForcibly();HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    @Override public void close(){cancel();guardian.shutdownNow();}
}
