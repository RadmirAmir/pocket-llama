package dev.pocketllama.photo;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.*;
import dev.pocketllama.*;
import dev.pocketllama.voice.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One foreground document queue, private OCR process, shared heavyweight-model lease. */
public final class OcrService extends Service {
    public static final String START="dev.pocketllama.photo.OCR",CANCEL="dev.pocketllama.photo.CANCEL";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();private Future<?> task;private volatile Thread runningThread;private volatile boolean cancelled;
    private volatile OcrEngine engine;private volatile SuryaPipeline surya;private volatile SuryaAssets assets;private String id,pageId;private PhotoStore store;private DocumentStore documents;private PowerManager.WakeLock wake;private long started,pageStarted,lastProgress;
    private String currentPhase="";private long phaseStartedAt;
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!dev.pocketllama.security.Vault.ready(this)){stopSelf();return START_NOT_STICKY;}
        if(intent==null||CANCEL.equals(intent.getAction())){cancelled=true;if(engine!=null)engine.cancel();if(surya!=null)surya.cancel();if(assets!=null)assets.cancel();Thread t=runningThread;if(t!=null)t.interrupt();if(task==null)stopSelf();return START_NOT_STICKY;}
        if(task!=null)return START_NOT_STICKY;id=intent.getStringExtra("id");started=SystemClock.elapsedRealtime();
        if(Build.VERSION.SDK_INT>=34)startForeground(7,notification("Подготовка локального OCR"),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(7,notification("Подготовка локального OCR"));
        task=executor.submit(this::run);return START_NOT_STICKY;
    }
    private void run(){runningThread=Thread.currentThread();try{
        store=new PhotoStore(this);documents=new DocumentStore(this);JSONObject document=documents.get(id);if(!document.optString("state").equals("processing"))throw new IOException("Задание OCR не подготовлено");JSONArray documentPages=document.getJSONArray("pages");boolean details=document.optString("task").equals("details");JSONArray pages=details?new JSONArray().put(document.getString("taskPage")):documentPages;pageId=pages.getString(0);
        try(VoiceStore voices=new VoiceStore(this)){if(voices.anyBusy())throw new IOException("Сначала завершите запись или обработку речи");}
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":ocr");wake.acquire(2*60*60*1000L+60000);
        startService(new Intent(this,InferenceService.class).setAction(InferenceService.STOP));
        try(ModelLease lease=awaitLease();SuryaPipeline pipeline=new SuryaPipeline(this,store,this::check)){
            String chosen=details?"surya":document.optString("engine","surya");File model=null,paddle=null;
            if(chosen.equals("surya")){surya=pipeline;assets=new SuryaAssets(this);model=assets.prepare((done,total,stage)->{check();status(stage+" · "+(done/1048576)+" / "+(total/1048576)+" МБ",0);});assets=null;}else paddle=OcrAssets.prepare(this);
            int finished=0,errors=0;String failure="";
            for(int i=0;i<pages.length();i++){check();pageStarted=SystemClock.elapsedRealtime();pageId=pages.getString(i);JSONObject page=store.get(pageId);if(!details&&page.optString("state").equals("done")&&page.optString("engine","paddle").equals(chosen)){finished++;continue;}
                store.beginInService(pageId);final int index=i,total=pages.length();
                try{
                    if(chosen.equals("surya")){
                        final String prefix=(details?"Уточнение рамок":"Страница "+(index+1)+" / "+total)+" · ";
                        SuryaPipeline.Progress progress=new SuryaPipeline.Progress(){
                            public void update(String message,double fraction)throws Exception {status(prefix+message,(index+fraction)/total);}
                            public void phase(String phase,String message,double fraction,long since)throws Exception {status(prefix+message,(index+fraction)/total,"page:"+index+":"+phase,since);}
                        };
                        if(details)pipeline.details(pageId,model,progress);else pipeline.page(pageId,model,progress);
                    }
                    else {Bitmap bitmap=ScanImages.render(store.image(pageId,false),page,4096);OcrRaster raster;try{raster=ScanImages.raster(bitmap);}finally{bitmap.recycle();}engine=new OcrEngine(paddle);List<OcrEngine.Line> lines=engine.recognize(raster,(done,count,partial)->{check();long now=SystemClock.elapsedRealtime();if(done==0||done==count||now-lastProgress>1000){lastProgress=now;String message="Страница "+(index+1)+" / "+total+" · строки "+done+" / "+count;store.progress(pageId,"processing",message,count==0?0:(double)done/count,partial,true);status(message,(index+(count==0?0:(double)done/count))/total);}});check();store.progress(pageId,"done","Paddle: текст готов. Разметка и структура таблиц доступны в Surya.",1,lines,true);engine=null;}
                    finished++;
                }catch(InterruptedException e){throw e;}catch(Exception e){check();errors++;failure=message(e);store.progress(pageId,"interrupted","Страница прервана. "+failure,0,Collections.emptyList(),false);if(e instanceof IOException&&!(e instanceof SuryaRuntime.Incomplete))break;}finally{engine=null;}
            }
            check();
            if(details&&finished==pages.length()){boolean allDone=true;for(int i=0;i<documentPages.length();i++)allDone&=store.get(documentPages.getString(i)).optString("state").equals("done");documents.progress(id,allDone?"done":"ready","Сетки таблиц уточнены. Текст и ручные исправления сохранены.",allDone?1:0);}
            else documents.progress(id,finished==pages.length()?"done":"interrupted",finished==pages.length()?"Документ распознан · "+finished+" стр. Проверьте важные цифры.":"Готово страниц: "+finished+" / "+pages.length()+". Можно продолжить. "+failure,(double)finished/pages.length());
        }
    }catch(Throwable e){Thread.interrupted();try{if(store!=null&&pageId!=null)store.progress(pageId,"interrupted",cancelled?"Остановлено. Готовые области сохранены.":message(e),0,Collections.emptyList(),false);if(documents!=null)documents.progress(id,"interrupted",cancelled?"Остановлено. Готовые страницы и области сохранены; можно продолжить.":"Обработка прервана. "+message(e),0);}catch(Exception ignored){}}
    finally{try{if(store!=null)store.finishJob();}catch(Exception ignored){}runningThread=null;engine=null;surya=null;assets=null;if(wake!=null&&wake.isHeld())wake.release();new Handler(Looper.getMainLooper()).post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}}
    private void status(String message,double fraction)throws Exception {status(message,fraction,"status:"+message,System.currentTimeMillis());}
    private void status(String message,double fraction,String phase,long since)throws Exception {check();if(!phase.equals(currentPhase)){currentPhase=phase;phaseStartedAt=since;}documents.progress(id,"processing",message,fraction,phase,phaseStartedAt);getSystemService(NotificationManager.class).notify(7,notification(message));}
    private ModelLease awaitLease()throws Exception {long until=SystemClock.elapsedRealtime()+30000;while(true){check();try{return new ModelLease(getFilesDir());}catch(IOException e){if(SystemClock.elapsedRealtime()>until)throw e;Thread.sleep(100);}}}
    private void check()throws Exception {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException();long now=SystemClock.elapsedRealtime();if(now-started>2*60*60*1000L||pageStarted>0&&now-pageStarted>30*60*1000L)throw new IOException("Лимит времени обработки. Готовые области сохранены; продолжите после паузы");if(getSystemService(PowerManager.class).getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE)throw new IOException("Дайте телефону остыть и продолжите OCR");ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();getSystemService(ActivityManager.class).getMemoryInfo(memory);if(memory.lowMemory||memory.availMem<384L*1024*1024)throw new IOException("Недостаточно свободной памяти. Закройте другие приложения и продолжите");}
    private static String message(Throwable e){if(e instanceof InterruptedException)return "Можно продолжить распознавание.";if(e instanceof OutOfMemoryError)return "Недостаточно памяти для страницы.";if(e instanceof IOException||e instanceof IllegalArgumentException)return e.getMessage()==null?"Повторите распознавание.":e.getMessage();return "Не удалось обработать страницу. Готовые результаты сохранены.";}
    private Notification notification(String text){NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("photos",dev.pocketllama.I18n.t("Локальный сканер документов"),NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(this,72,new Intent(this,PhotoActivity.class).putExtra("id",id),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);PendingIntent stop=PendingIntent.getService(this,73,new Intent(this,OcrService.class).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"photos").setSmallIcon(R.drawable.ic_llama).setContentTitle(dev.pocketllama.I18n.t("Pocket · сканер")).setContentText(dev.pocketllama.I18n.status(text)).setWhen(phaseStartedAt>0?phaseStartedAt:System.currentTimeMillis()).setShowWhen(true).setUsesChronometer(true).setContentIntent(open).setVisibility(Notification.VISIBILITY_SECRET).setOnlyAlertOnce(true).setOngoing(true).addAction(new Notification.Action.Builder(null,dev.pocketllama.I18n.t("Остановить"),stop).build()).build();}
    @Override public void onDestroy(){cancelled=true;if(engine!=null)engine.cancel();if(surya!=null)surya.cancel();if(assets!=null)assets.cancel();executor.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid());}
    @Override public IBinder onBind(Intent intent){return null;}
}
