package dev.pocketllama.searchtest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import dev.pocketllama.search.WebSearch;
import dev.pocketllama.search.BrowserSearch;
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
            Process child=(Process)field(task,"process");check(child.isAlive(),"search child remains available for the turn");
            check(task.search("Second native parser fixture","en").getJSONArray("sources").length()==1,"second lookup succeeds without restarting Python");
            check(field(task,"process")==child,"same CPython child serves both lookups");task.close();
            check(((Process)field(task,"process")).waitFor(2,TimeUnit.SECONDS),"child retires after lookup");
            File base=new File(getNoBackupFilesDir(),"search-runtime-v1");
            for(File file:base.listFiles())check(!file.getName().endsWith(".db")&&!file.getName().endsWith(".db-wal"),"no disk cache");
            WebSearch badTls=new WebSearch(this,()->{});boolean rejected=false;
            try{JSONObject failure=badTls.search("certificate validation fixture","en");rejected=failure.getJSONArray("sources").length()==0&&!failure.getBoolean("retryable")&&failure.optString("message").contains("certificate");}finally{badTls.close();}
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
            browserChecks();
            Log.i("PocketSearchTest","PASS: native CPython, DNS/TCP/verified HTTPS, certificate rejection, lxml, msgspec, real SearXNG and lock cancellation");
        }catch(Throwable e){Log.e("PocketSearchTest","FAIL",e);}
    },"pocket-search-test").start();}

    private Thread browserThread(BrowserSearch browser,String query,AtomicReference<Object> result){Thread t=new Thread(()->{try{result.set(browser.search(query,"en"));}catch(Throwable e){result.set(e);}},"browser-probe");t.start();return t;}
    private void browserChecks()throws Exception {
        AtomicReference<Object> result=new AtomicReference<>();BrowserSearch first=new BrowserSearch(this,()->{},()->{});Thread worker=browserThread(first,"browser fixture",result);worker.join(15000);
        check(!worker.isAlive()&&result.get() instanceof JSONObject,"real WebView returns parsed source: "+result.get());JSONObject hit=((JSONObject)result.get()).getJSONArray("sources").getJSONObject(0);check(hit.getString("text").contains("No native bridge"),"untrusted page has no native bridge");check(hit.getString("url").equals("https://example.org/browser"),"browser source URL");
        BrowserSearch challenge=new BrowserSearch(this,()->{},()->{});result.set(null);worker=browserThread(challenge,"challenge fixture",result);Thread.sleep(2500);check(worker.isAlive()&&result.get()==null,"challenge waits for user, never auto-solves");
        runOnUiThread(()->{try{((android.webkit.WebView)field(challenge,"view")).evaluateJavascript("document.getElementById('continue').click()",null);}catch(Exception e){throw new RuntimeException(e);}});worker.join(10000);check(!worker.isAlive()&&result.get() instanceof JSONObject,"manual fixture confirmation resumes browser search");
        AtomicBoolean allowed=new AtomicBoolean(true);BrowserSearch cancel=new BrowserSearch(this,()->{if(!allowed.get())throw new InterruptedException("locked");},()->{});result.set(null);worker=browserThread(cancel,"challenge cancel fixture",result);Thread.sleep(1500);allowed.set(false);worker.join(3000);check(!worker.isAlive()&&result.get() instanceof InterruptedException,"vault lock retires browser promptly");BrowserSearch.clearSession();
        Log.i("PocketSearchTest","PASS: real Android WebView/JavaScript, no native bridge, manual challenge handoff and browser cancellation (synthetic pages)");
    }
}
