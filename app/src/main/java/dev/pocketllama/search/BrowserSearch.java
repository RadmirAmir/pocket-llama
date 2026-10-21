package dev.pocketllama.search;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** A visible, real Android browser. No UA/TLS impersonation or CAPTCHA automation. */
public final class BrowserSearch implements AutoCloseable {
    private final Activity activity;
    private final WebPrompt.Check access;
    private final Runnable onCancel;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final CountDownLatch done=new CountDownLatch(1);
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile JSONObject result;
    private volatile boolean cancelled;
    private Dialog dialog;
    private volatile WebView view;
    private TextView status;
    private String language,query,script;
    private int provider,navigation,pollingNavigation=-1;
    private boolean evaluating;

    public BrowserSearch(Activity activity,WebPrompt.Check access,Runnable onCancel){this.activity=activity;this.access=access;this.onCancel=onCancel;}
    private String t(String ru,String en){return "en".equals(language)?en:ru;}
    public JSONObject search(String raw,String language)throws Exception {
        query=WebQuery.validate(raw);this.language=language;
        try(InputStream in=activity.getAssets().open("search/browser-extract.js")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Invalid browser search program");out.write(b,0,n);}script=out.toString("UTF-8");}
        access.run();ui.post(this::open);
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
            while(!done.await(150,TimeUnit.MILLISECONDS)){access.run();if(System.nanoTime()>deadline)throw new IOException(t("Поисковая страница не вернула результаты. Повторите запрос или выберите другой поисковик.","The search page returned no results. Retry or choose another search engine."));}
            access.run();if(cancelled)throw new InterruptedException(t("Ответ остановлен","Stopped"));
            if(result==null)throw new IOException(t("Не удалось открыть поисковую страницу","Could not open the search page"));
            return result;
        }finally{close();}
    }
    private void open(){
        if(closed.get()||activity.isFinishing()){done.countDown();return;}
        try{
            dialog=new Dialog(activity);LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(Color.WHITE);
            int padding=(int)(12*activity.getResources().getDisplayMetrics().density);layout.setPadding(padding,padding,padding,padding);layout.setFitsSystemWindows(true);
            TextView title=new TextView(activity);title.setText(t("Поиск на вашем телефоне","Search on your phone"));title.setTextColor(Color.BLACK);title.setTextSize(18);layout.addView(title);
            status=new TextView(activity);status.setTextColor(Color.DKGRAY);status.setText(t("Загружается обычная поисковая страница…","Loading the search page…"));layout.addView(status);
            LinearLayout controls=new LinearLayout(activity);
            Button next=new Button(activity);next.setText(t("Другой поисковик","Change engine"));next.setOnClickListener(v->{provider=(provider+1)%3;load();});controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));
            Button cancel=new Button(activity);cancel.setText(t("Отмена","Cancel"));cancel.setOnClickListener(v->cancel());controls.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));layout.addView(controls);
            view=new WebView(activity);WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setSaveFormData(false);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
            view.setDownloadListener((url,ua,disposition,mime,size)->{});
            view.setWebChromeClient(new WebChromeClient(){
                @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
                @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){callback.invoke(origin,false,false);}
            });
            // No native JavascriptInterface is exposed to these untrusted pages.
            view.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return r.isForMainFrame()&&!allowedNavigation(r.getUrl().toString());}
                @Override public void onPageStarted(WebView w,String url,android.graphics.Bitmap icon){navigation++;evaluating=false;status.setText(t("Загружается поисковая страница…","Loading search page…"));beginScan(navigation);}
                @Override public void onPageFinished(WebView w,String url){beginScan(navigation);}
                @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())status.setText(t("Не удалось загрузить страницу. Проверьте сеть или выберите другой поисковик.","Could not load the page. Check the connection or choose another engine."));}
            });
            layout.addView(view,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(layout);dialog.setOnCancelListener(v->cancel());dialog.show();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getWindow().setLayout(-1,-1);load();
        }catch(Exception e){done.countDown();}
    }
    private static boolean allowedNavigation(String raw){
        try{android.net.Uri uri=android.net.Uri.parse(raw);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null)return false;
            return host.equals("www.google.com")||host.equals("google.com")||host.equals("consent.google.com")||host.equals("accounts.google.com")||host.equals("search.brave.com")||host.equals("duckduckgo.com")||host.equals("www.duckduckgo.com");
        }catch(Exception e){return false;}
    }
    private void load(){
        if(closed.get()||view==null)return;
        try{
            String q=URLEncoder.encode(query,StandardCharsets.UTF_8.name());String url=provider==0?"https://www.google.com/search?q="+q+"&hl="+("en".equals(language)?"en":"ru"):provider==1?"https://search.brave.com/search?q="+q:"https://duckduckgo.com/?q="+q;
            status.setText(t("Загружается поисковая страница…","Loading search page…"));view.loadUrl(url);
        }catch(Exception e){done.countDown();}
    }
    private void beginScan(int current){if(current!=pollingNavigation){pollingNavigation=current;ui.postDelayed(()->extract(current),700);}}
    private void extract(int current){
        if(closed.get()||view==null||evaluating||current!=navigation)return;
        if(!allowedNavigation(view.getUrl())){ui.postDelayed(()->extract(current),700);return;}evaluating=true;
        view.evaluateJavascript(script,raw->{
            evaluating=false;if(closed.get()||current!=navigation)return;
            try{
                if(raw==null||raw.length()>128*1024)throw new JSONException("Invalid page result");Object parsed=new JSONTokener(raw).nextValue();JSONObject page=parsed instanceof String?new JSONObject((String)parsed):(JSONObject)parsed;
                JSONArray found=page.getJSONArray("sources"),accepted=new JSONArray();
                for(int i=0;i<Math.min(6,found.length());i++){JSONObject source=found.getJSONObject(i);if(!WebQuery.publicUrl(source.optString("url")))continue;
                    source.put("title",bound(source.optString("title"),240)).put("text",bound(source.optString("text"),1200));if(source.optString("text").isEmpty())continue;source.put("citation","W"+(accepted.length()+1));accepted.put(source);}
                if(accepted.length()>0){result=new JSONObject().put("query",query).put("sources",accepted).put("engine","Android WebView").put("mode","browser").put("retryable",false).put("failedEngines",new JSONArray());done.countDown();return;}
                status.setText(page.optBoolean("challenge")?t("Подтвердите запрос на странице поисковика. После подтверждения поиск продолжится.","Complete the search engine's verification on this page. Search will continue afterward."):t("Ожидаю выдачу. Если нужно, подтвердите запрос или выберите другой поисковик.","Waiting for results. Complete any verification or choose another engine."));
            }catch(Exception e){status.setText(t("Пока не удалось прочитать выдачу. Можно выбрать другой поисковик.","Could not read results yet. You can choose another engine."));}
            ui.postDelayed(()->extract(current),1500);
        });
    }
    private static String bound(String value,int max){return value.length()>max?value.substring(0,max):value;}
    public void cancel(){cancelled=true;onCancel.run();done.countDown();close();}
    @Override public void close(){if(!closed.compareAndSet(false,true))return;ui.post(()->{if(view!=null){view.stopLoading();view.clearHistory();view.clearCache(true);view.destroy();view=null;}if(dialog!=null){dialog.setOnCancelListener(null);dialog.dismiss();dialog=null;}});}
    /** Search cookies are confined to the unlocked chat session, separate from Chrome. */
    public static void clearSession(){new Handler(Looper.getMainLooper()).post(()->{try{CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();for(String origin:new String[]{"https://www.google.com","https://google.com","https://consent.google.com","https://accounts.google.com","https://search.brave.com","https://duckduckgo.com","https://www.duckduckgo.com"})WebStorage.getInstance().deleteOrigin(origin);}catch(Exception ignored){}});}
}
