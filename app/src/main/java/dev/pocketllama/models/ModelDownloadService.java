package dev.pocketllama.models;

import android.app.*;
import android.content.*;
import android.os.*;
import dev.pocketllama.*;
import dev.pocketllama.core.*;
import dev.pocketllama.voice.VoiceActivity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** User-started network transfer only; no recordings, prompts or identifiers leave the device. */
public final class ModelDownloadService extends Service {
    public static final String START="model.download",PAUSE="model.pause";
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private static volatile String id="",stage="",state="idle",error="";
    private static volatile long done,total;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile VerifiedDownload transfer;private volatile boolean paused;
    private PowerManager.WakeLock wake;
    private static File stateFile(Context c){File d=new File(c.getFilesDir(),"model-downloads");d.mkdirs();return new File(d,"job.json");}
    public static JSONObject snapshot(Context c)throws Exception {
        if(RUNNING.get())return json(true);
        File f=stateFile(c);if(!f.isFile())return new JSONObject().put("active",false).put("state","idle");
        try(InputStream in=new FileInputStream(f)){JSONObject j=new JSONObject(new String(ModelCatalog.read(in,16384),StandardCharsets.UTF_8));j.put("active",false);if(j.optString("state").equals("downloading")||j.optString("state").equals("verifying"))j.put("state","paused").put("stage","Можно продолжить загрузку");return j;}catch(Exception e){return new JSONObject().put("active",false).put("state","idle");}
    }
    private static JSONObject json(boolean active)throws JSONException{return new JSONObject().put("active",active).put("id",id).put("state",state).put("stage",stage).put("done",done).put("total",total).put("error",error);}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(PAUSE.equals(intent.getAction())){paused=true;VerifiedDownload d=transfer;if(d!=null)d.cancel();if(!RUNNING.get())stopSelf();return START_NOT_STICKY;}
        if(!START.equals(intent.getAction())||!dev.pocketllama.security.GateSession.isOpen()){stopSelf();return START_NOT_STICKY;}
        if(!RUNNING.compareAndSet(false,true))return START_NOT_STICKY;
        id=intent.getStringExtra("id");done=0;total=0;error="";stage="Подготавливаем загрузку";state="downloading";paused=false;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("models","Загрузка моделей",NotificationManager.IMPORTANCE_LOW));
        startForeground(480,notification());
        executor.execute(()->download());return START_NOT_STICKY;
    }
    private void download(){long deadline=SystemClock.elapsedRealtime()+2L*60*60*1000;
        try {
            CatalogRepository repo=new CatalogRepository(this);ModelCatalog.Entry entry=repo.load().find(id);if(entry.minAppCode>DistributionConfig.code(this))throw new IOException("Обновите Pocket для этой модели");
            total=entry.bytes;done=repo.partial(entry).length();
            wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Pocket:ModelDownload");wake.acquire(2L*60*60*1000);
            transfer=new VerifiedDownload();
            File file=transfer.transfer(entry.url,entry.bytes,entry.sha256,repo.partial(entry),repo.target(entry),(n,t,s)->{done=n;total=t;stage=s;state=s.equals("Проверяем файл")?"verifying":"downloading";persist();getSystemService(NotificationManager.class).notify(480,notification());},()->{if(paused)throw new InterruptedException();if(SystemClock.elapsedRealtime()>deadline)throw new IOException("Загрузка приостановлена по времени. Можно продолжить");});
            if(!GgufReader.read(file).architecture().equals(entry.architecture)){file.delete();throw new IOException("Неподдерживаемая архитектура модели");}
            repo.markInstalled(entry);done=total;stage="Модель готова. Выберите её в настройках";state="done";
        }catch(Exception e){state=paused||e instanceof InterruptedException?"paused":"error";stage=state.equals("paused")?"Можно продолжить загрузку":"Загрузка остановлена";error=paused?"":e.getMessage()==null?"Проверьте сеть и повторите загрузку":e.getMessage();}
        finally{try{persist();}catch(Exception ignored){}transfer=null;if(wake!=null&&wake.isHeld())wake.release();RUNNING.set(false);new Handler(Looper.getMainLooper()).post(()->{if(!RUNNING.get()){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}});}
    }
    private void persist()throws Exception{CatalogRepository.write(stateFile(this),json(true).toString().getBytes(StandardCharsets.UTF_8));}
    private Notification notification(){
        Intent open=new Intent(this,VoiceActivity.class).putExtra("screen","settings");PendingIntent content=PendingIntent.getActivity(this,480,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause=PendingIntent.getService(this,481,new Intent(this,ModelDownloadService.class).setAction(PAUSE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"models").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Pocket · модель").setContentText(stage+(total>0?" · "+done/1048576+" / "+total/1048576+" МБ":"")).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,total>0?(int)Math.min(100,done*100/total):0,total<=0||state.equals("verifying")).addAction(new Notification.Action.Builder(null,"Приостановить",pause).build()).build();
    }
    @Override public void onDestroy(){paused=true;VerifiedDownload d=transfer;if(d!=null)d.cancel();executor.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public void onTimeout(int startId,int fgsType){paused=true;VerifiedDownload d=transfer;if(d!=null)d.cancel();stopSelf();}
}
