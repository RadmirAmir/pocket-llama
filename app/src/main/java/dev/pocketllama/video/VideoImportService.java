package dev.pocketllama.video;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import dev.pocketllama.InferenceService;
import dev.pocketllama.security.*;
import dev.pocketllama.voice.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;

/** Foreground pipeline: network -> encrypted container -> encrypted PCM -> ASR. */
public final class VideoImportService extends Service {
 public static final String START="dev.pocketllama.video.IMPORT",CANCEL="dev.pocketllama.video.CANCEL";
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private volatile boolean cancelled;private volatile VideoDownloader downloader;private Future<?> task;
 private VoiceStore store;private String id;private PowerManager.WakeLock wake;private long lastProgress;
 @Override public void onCreate(){super.onCreate();if(Vault.ready(this))store=new VoiceStore(this);else stopSelf();}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(store==null){stopSelf();return START_NOT_STICKY;}
  if(intent==null||CANCEL.equals(intent.getAction())){cancelled=true;VideoDownloader d=downloader;if(d!=null)d.cancel();if(task==null)stopSelf();return START_NOT_STICKY;}
  if(task!=null)return START_NOT_STICKY;id=intent.getStringExtra("id");
  Notification notice=VoiceNotifications.build(this,"Подготовка импорта видео…",VideoImportService.class,CANCEL);
  if(Build.VERSION.SDK_INT>=34)startForeground(6,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(6,notice);
  task=executor.submit(this::run);return START_NOT_STICKY;
 }
 public static File media(Context c,String id){File audio=VoiceStore.audio(c,id);return new File(audio.getParentFile(),id+".media");}
 public static boolean alive(Context c){java.util.List<ActivityManager.RunningAppProcessInfo> list=c.getSystemService(ActivityManager.class).getRunningAppProcesses();if(list!=null)for(ActivityManager.RunningAppProcessInfo p:list)if(p.processName.equals(c.getPackageName()+":video"))return true;return false;}
 private void guard()throws Exception {
  if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException();
  if(getSystemService(PowerManager.class).getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE)throw new IOException("Телефон перегрелся. Продолжите после охлаждения");
  if(Vault.root(this).getUsableSpace()<64L*1024*1024)throw new IOException("На телефоне осталось слишком мало места. Освободите память и повторите");
 }
 private void progress(String detail,double fraction)throws Exception{guard();long now=SystemClock.elapsedRealtime();if(now-lastProgress<800)return;lastProgress=now;store.state(id,"downloading",detail,fraction);getSystemService(NotificationManager.class).notify(6,VoiceNotifications.build(this,detail,VideoImportService.class,CANCEL));}
 private ModelLease lease()throws Exception{long until=SystemClock.elapsedRealtime()+30000;while(true){guard();try{return new ModelLease(getFilesDir());}catch(IOException e){if(SystemClock.elapsedRealtime()>until)throw e;Thread.sleep(100);}}}
 private void run(){boolean began=false,recognize=false;try {
  if(dev.pocketllama.photo.PhotoStore.busy(this))throw new IOException("Сначала завершите OCR фото");
  JSONObject job=store.get(id);String url=VideoUrl.parse(job.optString("video_url"));store.startJob(id,"downloading");began=true;
  wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":video");wake.acquire(6*60*60*1000L);
  startService(new Intent(this,InferenceService.class).setAction(InferenceService.STOP));
  try(ModelLease lease=lease()){
   guard();File original=media(this,id),audio=VoiceStore.audio(this,id);
   if(!audio.isFile()){
    if(!original.isFile()||!job.optString("video_stage").equals("downloaded")){
     original.delete();progress("Готовим загрузчик видео…",0);
     VideoRuntime runtime=VideoRuntime.prepare(this);guard();downloader=new VideoDownloader(runtime);
     progress("Получаем название и аудиодорожку…",0);
     JSONObject info=downloader.inspect(url,(done,total)->guard());
     String title=info.optString("title","Видео").replaceAll("[\\p{Cntrl}]", " ").trim();if(title.isEmpty())title="Видео";store.rename(id,title);
     ContentValues meta=new ContentValues();meta.put("video_platform",info.optString("extractor_key",VideoUrl.host(url)));meta.put("video_duration",Math.round(info.optDouble("duration",0)*1000));store.update(id,meta);
     long total=info.optLong("filesize",info.optLong("filesize_approx",0));
     downloader.download(url,original,Vault.key(this),total,(done,size)->progress("Загрузка аудио · "+String.format(java.util.Locale.ROOT,"%.1f МБ",done/1048576.0)+(size>0?" из "+String.format(java.util.Locale.ROOT,"%.1f МБ",size/1048576.0):""),size>0?0.7*Math.min(1,(double)done/size):0));
     downloader=null;ContentValues stage=new ContentValues();stage.put("video_stage","downloaded");store.update(id,stage);guard();
    }
    lastProgress=0;progress("Подготовка звука для распознавания…",0.7);
    try(EncryptedMediaSource source=new EncryptedMediaSource(original,Vault.key(this))){long duration=store.get(id).optLong("video_duration");long samples=AudioImporter.decode(this,source,audio,n->progress("Подготовка аудио · "+VoiceStore.time(n/16),duration>0?0.7+0.3*Math.min(1,(double)(n/16)/duration):0.7));store.samples(id,samples);}
   }else{try(PcmFile pcm=new PcmFile(audio,false,Vault.key(this))){if(pcm.samples()<8000)throw new IOException("В ролике слишком мало звука для распознавания");store.samples(id,pcm.samples());}}
   guard();original.delete();new File(original.getParentFile(),original.getName()+".part").delete();ContentValues stage=new ContentValues();stage.put("video_stage","ready");store.update(id,stage);
   store.state(id,"ready","Аудио сохранено. Переходим к распознаванию речи.",0);recognize=true;
  }
 }catch(Throwable e){if(began)try{store.state(id,"interrupted",cancelled?"Загрузка остановлена. Можно повторить; завершённое аудио сохранено.":"Импорт видео прерван: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),0);}catch(Throwable ignored){}}
 finally{
  VideoDownloader d=downloader;downloader=null;if(d!=null)d.cancel();if(wake!=null&&wake.isHeld())wake.release();
  if(recognize&&!cancelled)try{startForegroundService(new Intent(this,SpeechService.class).setAction(SpeechService.PROCESS).putExtra("id",id));}catch(Exception e){store.state(id,"interrupted","Аудио сохранено. Нажмите «Продолжить распознавание».",0);}
  new Handler(Looper.getMainLooper()).post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});
 }}
 @Override public void onDestroy(){cancelled=true;VideoDownloader d=downloader;if(d!=null)d.cancel();executor.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();if(store!=null)store.close();super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid());}
 @Override public IBinder onBind(Intent intent){return null;}
}
