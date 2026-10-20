package dev.pocketllama.searchtest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import dev.pocketllama.search.WebSearch;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Runs the packaged Bionic CPython/extensions and real SearXNG on Android. */
public final class AndroidSearchProbe extends Activity {
    private static final AtomicBoolean started=new AtomicBoolean();
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!started.compareAndSet(false,true))return;new Thread(()->{
        try{
            WebSearch task=new WebSearch(this,()->{});
            JSONObject result=task.search("Pocket native parser fixture","en");
            JSONArray sources=result.getJSONArray("sources");
            check(sources.length()==1,"native parser result");
            check(sources.getJSONObject(0).getString("url").equals("https://example.org/native"),"native source URL");
            check(sources.getJSONObject(0).getString("text").equals("Android SearXNG runtime works."),"native lxml parser and msgspec aggregation");
            check(((Process)field(task,"process")).waitFor(2,TimeUnit.SECONDS),"child retires after lookup");
            File base=new File(getNoBackupFilesDir(),"search-runtime-v1");
            for(File file:base.listFiles())check(!file.getName().endsWith(".db")&&!file.getName().endsWith(".db-wal"),"no disk cache");
            WebSearch badTls=new WebSearch(this,()->{});boolean rejected=false;
            try{badTls.search("certificate validation fixture","en");}catch(java.io.IOException error){rejected=error.getMessage().contains("сертификата");}
            check(rejected,"untrusted TLS certificate is rejected and diagnosed");
            AtomicBoolean allowed=new AtomicBoolean(true);AtomicReference<Throwable> outcome=new AtomicReference<>();
            WebSearch slow=new WebSearch(this,()->{if(!allowed.get())throw new InterruptedException("locked");});
            Thread query=new Thread(()->{try{slow.search("slow cancellation fixture","en");outcome.set(new AssertionError("cancelled lookup returned"));}catch(Exception e){outcome.set(e);}});query.start();
            long until=System.currentTimeMillis()+25000;while(field(slow,"connection")==null&&query.isAlive()&&System.currentTimeMillis()<until)Thread.sleep(25);
            check(field(slow,"connection")!=null,"slow lookup reached local API");
            long cancelled=System.currentTimeMillis();allowed.set(false);query.join(3000);
            check(!query.isAlive()&&outcome.get() instanceof Exception,"lock cancels lookup");
            check(System.currentTimeMillis()-cancelled<3000,"bounded cancellation");
            check(((Process)field(slow,"process")).waitFor(2,TimeUnit.SECONDS),"lock kills Python child");
            Log.i("PocketSearchTest","PASS: native CPython, DNS/TCP/verified HTTPS, certificate rejection, lxml, msgspec, real SearXNG and lock cancellation");
        }catch(Throwable e){Log.e("PocketSearchTest","FAIL",e);}
    },"pocket-search-test").start();}
}
