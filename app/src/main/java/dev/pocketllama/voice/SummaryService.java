package dev.pocketllama.voice;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import dev.pocketllama.*;
import dev.pocketllama.core.ServerConfig;
import dev.pocketllama.core.SummaryProfile;
import java.util.concurrent.*;
import java.io.*;
import org.json.JSONObject;

public final class SummaryService extends Service {
    public static final String START="dev.pocketllama.voice.SUMMARIZE",CANCEL="dev.pocketllama.voice.CANCEL_SUMMARY";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();private volatile boolean cancelled;private volatile LocalSummary summary;private Future<?> task;private VoiceStore store;private String id;
    private volatile boolean ownsLlm;private PowerManager.WakeLock wake;
    @Override public void onCreate(){super.onCreate();if(dev.pocketllama.security.Vault.ready(this))store=new VoiceStore(this);else stopSelf();}
    @Override public int onStartCommand(Intent i,int flags,int startId){if(store==null){stopSelf();return START_NOT_STICKY;}
        if(i==null||CANCEL.equals(i.getAction())){cancelled=true;LocalSummary s=summary;if(s!=null)s.cancel();if(ownsLlm)startService(new Intent(this,InferenceService.class).setAction(InferenceService.STOP));if(task==null)stopSelf();return START_NOT_STICKY;}
        if(task!=null)return START_NOT_STICKY;if(dev.pocketllama.photo.PhotoStore.busy(this)){stopSelf();return START_NOT_STICKY;}id=i.getStringExtra("id");VoiceState.busy=true;VoiceState.id=id;
        if(Build.VERSION.SDK_INT>=34)startForeground(4,VoiceNotifications.build(this,"Подготовка конспекта…",SummaryService.class,CANCEL),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(4,VoiceNotifications.build(this,"Подготовка конспекта…",SummaryService.class,CANCEL));
        task=executor.submit(this::run);return START_NOT_STICKY;
    }
    public static boolean speechAlive(Context c){java.util.List<ActivityManager.RunningAppProcessInfo> list=c.getSystemService(ActivityManager.class).getRunningAppProcesses();if(list!=null)for(ActivityManager.RunningAppProcessInfo p:list)if(p.processName.equals(c.getPackageName()+":speech"))return true;return false;}
    private void check()throws Exception{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException("Остановлено");if(getSystemService(PowerManager.class).getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE)throw new IOException("Телефон перегрелся; продолжите после охлаждения");}
    private void progress(String text)throws Exception{check();store.state(id,"summarizing",text,0);getSystemService(NotificationManager.class).notify(4,VoiceNotifications.build(this,text,SummaryService.class,CANCEL));}
    private void run(){boolean began=false;try{
        if(store.get(id).getInt("asr_done")==0)throw new IOException("Сначала завершите распознавание");
        try(android.database.Cursor c=store.turns(id)){if(!c.moveToFirst())throw new IOException("В записи не найдена речь для конспекта");}
        store.startJob(id,"summarizing");began=true;
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":summary");wake.acquire(6*60*60*1000L);
        progress("Освобождение памяти речевых моделей…");long until=SystemClock.elapsedRealtime()+30000;
        while(speechAlive(this)){check();if(SystemClock.elapsedRealtime()>until)throw new IOException("Речевой процесс ещё завершает работу. Повторите через несколько секунд");Thread.sleep(100);}
        if (RuntimeState.active || InferenceService.exists) startService(new Intent(this,InferenceService.class).setAction(InferenceService.STOP));
        until=SystemClock.elapsedRealtime()+30000;while(RuntimeState.active || InferenceService.exists){check();if(SystemClock.elapsedRealtime()>until)throw new IOException("LLM ещё завершает предыдущий запрос");Thread.sleep(100);}
        ServerConfig config=new ConfigStore(this).load();if(config.modelName.isEmpty())throw new IOException("Для конспекта импортируйте GGUF на главном экране");int context=SummaryProfile.context(config.contextPerSlot);
        JSONObject job=store.get(id);boolean video=!job.optString("video_url").isEmpty(),english=dev.pocketllama.I18n.language().equals("en");String signature=LocalSummary.version(video)+"|"+(video?"video|":"meeting|")+config.modelName+"|"+context+(english?"|en":"");if(!signature.equals(job.optString("summary_key"))){store.clearSummary(id);ContentValues v=new ContentValues();v.put("summary_key",signature);store.update(id,v);}
        progress("Загрузка локальной LLM…");ownsLlm=true;
        startForegroundService(new Intent(this,InferenceService.class).setAction(InferenceService.START).putExtra("voice_summary",true));
        until=SystemClock.elapsedRealtime()+10*60*1000;long started=SystemClock.elapsedRealtime();
        while(!RuntimeState.ready){check();if(!RuntimeState.active&&SystemClock.elapsedRealtime()-started>1500)throw new IOException(RuntimeState.status);if(SystemClock.elapsedRealtime()>until)throw new IOException("Не удалось дождаться LLM");Thread.sleep(200);}
        summary=new LocalSummary(new VoiceSummarySource(store,id),config.port,new ConfigStore(this).apiKey(),context,video,english);String result=summary.summarize(this::progress);check();
        ContentValues v=new ContentValues();v.put("summary",result);store.update(id,v);store.state(id,"done","Конспект готов. Ссылки [#] ведут к номерам реплик в расшифровке.",1);
    }catch(Throwable e){if(began){String reason=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();if(!cancelled&&summary!=null&&ownsLlm&&!RuntimeState.active&&!RuntimeState.ready)reason="Локальная LLM остановлена: "+RuntimeState.status+". Завершённые части сохранены [SUMMARY_SERVER_LOST]";store.state(id,"interrupted",cancelled?"Конспект остановлен. Промежуточные заметки сохранены.":"Конспект прерван: "+reason,0);}}
        finally{summary=null;if(ownsLlm)startService(new Intent(this,InferenceService.class).setAction(InferenceService.STOP));VoiceState.busy=false;VoiceState.id="";if(wake!=null&&wake.isHeld())wake.release();if(store!=null)store.close();new Handler(Looper.getMainLooper()).post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}
    }
    @Override public void onDestroy(){cancelled=true;LocalSummary s=summary;if(s!=null)s.cancel();executor.shutdownNow();if(task==null&&store!=null)store.close();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
