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
import java.util.concurrent.CountDownLatch;
import org.json.*;

/** Runs the packaged Bionic CPython/extensions and real SearXNG on Android. */
public final class AndroidSearchProbe extends Activity {
    private static final AtomicBoolean started=new AtomicBoolean();
    private final CountDownLatch focused=new CountDownLatch(1);
    private final AtomicBoolean checkingSearch=new AtomicBoolean(),lostFocus=new AtomicBoolean();
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)focused.countDown();else if(checkingSearch.get())lostFocus.set(true);}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        // Give the headless emulator a real, awake window before checking that
        // background search does not steal focus. No search starts before focus.
        setTurnScreenOn(true);setShowWhenLocked(true);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.widget.TextView status=new android.widget.TextView(this);status.setText("Pocket search runtime test");setContentView(status);
        if(!started.compareAndSet(false,true))return;new Thread(()->{
        try{
            check(focused.await(30,TimeUnit.SECONDS),"search probe has window focus");checkingSearch.set(true);
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
            WebSearch live=new WebSearch(this,()->{});
            try{
                JSONObject news=live.search("Нефтекамск новости","ru","news");JSONArray dated=news.getJSONArray("sources");check(dated.length()>0,"live public news returned sources");
                for(int i=0;i<dated.length();i++)check(!dated.getJSONObject(i).optString("published").isEmpty(),"live news has publication dates");
                JSONObject general=live.search("llama.cpp","en","general");check(general.getJSONArray("sources").length()>0,"live public general search returned sources");
                Log.i("PocketSearchTest","Live background search: RU news="+dated.length()+", EN general="+general.getJSONArray("sources").length());
            }finally{live.close();}
            check(!lostFocus.get(),"background search never opens another window");checkingSearch.set(false);
            Log.i("PocketSearchTest","PASS: native CPython, public DNS/verified HTTPS, background RSS/API search without windows, certificate rejection, real SearXNG and lock cancellation");
        }catch(Throwable e){Log.e("PocketSearchTest","FAIL",e);}
    },"pocket-search-test").start();}

}
