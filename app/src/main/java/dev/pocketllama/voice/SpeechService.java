package dev.pocketllama.voice;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.os.*;
import dev.pocketllama.InferenceService;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Own process is intentionally retired after each job, releasing ORT native RSS. */
public final class SpeechService extends Service {
    public static final String PROCESS="dev.pocketllama.voice.PROCESS",CANCEL="dev.pocketllama.voice.CANCEL_ASR";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;private volatile SpeechModels.Recognizer activeAsr;
    private Future<?> task;private String id;private VoiceStore store;private PowerManager.WakeLock wake;
    private long lastHeartbeat;private String importUri;
    @Override public void onCreate(){super.onCreate();if(dev.pocketllama.security.Vault.ready(this))store=new VoiceStore(this);else stopSelf();}
    @Override public int onStartCommand(Intent i,int flags,int startId){if(store==null){stopSelf();return START_NOT_STICKY;}
        if(i==null||CANCEL.equals(i.getAction())){cancelled=true;SpeechModels.Recognizer a=activeAsr;if(a!=null)a.cancel();if(task==null)stopSelf();return START_NOT_STICKY;}
        if(task!=null)return START_NOT_STICKY;
        id=i.getStringExtra("id");importUri=i.getStringExtra("uri");cancelled=false;
        if(Build.VERSION.SDK_INT>=34)startForeground(3,VoiceNotifications.build(this,"Подготовка распознавания…",SpeechService.class,CANCEL),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(3,VoiceNotifications.build(this,"Подготовка распознавания…",SpeechService.class,CANCEL));
        task=executor.submit(this::run);return START_NOT_STICKY;
    }
    private void run(){boolean began=false,automaticSummary=false;try{
        if(dev.pocketllama.photo.PhotoStore.busy(this))throw new IOException("Сначала завершите OCR фото");
        store.startJob(id,"processing");began=true;
        PowerManager pm=getSystemService(PowerManager.class);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":speech");wake.acquire(6*60*60*1000L);
        startService(new Intent(this,InferenceService.class).setAction(InferenceService.STOP));
        try(ModelLease lease=awaitLease()){
            guard();
            if(importUri==null&&!VoiceStore.audio(this,id).isFile()){String saved=store.get(id).optString("source_uri");if(!saved.isEmpty())importUri=saved;}
            if(importUri!=null){
                long samples=AudioImporter.decode(this,android.net.Uri.parse(importUri),VoiceStore.audio(this,id),n->progress("Импорт аудио · "+VoiceStore.time(n/16),0));
                store.samples(id,samples);importUri=null;
            }
            progress("Проверка встроенных речевых моделей…",0);
            JSONObject job=store.get(id);String language=job.optString("asr_language","ru");
            File models=SpeechAssets.prepare(this,!language.equals("en"));boolean diarize=job.getInt("diarize")!=0&&job.getInt("expected")!=1;
            try(PcmFile audio=new PcmFile(VoiceStore.audio(this,id),false,dev.pocketllama.security.Vault.key(this))){
                if(audio.samples()<8000)throw new IOException("Запись короче половины секунды");
                if(job.getInt("vad_done")==0)vad(audio,models);
                guard();
                if(job.getInt("speakers_done")==0){if(diarize)speakers(audio,models,job.getInt("expected"));store.flag(id,"speakers_done",true);}
                guard();
                if(job.getInt("asr_done")==0)transcribe(audio,models,diarize,language);
                store.rebuildTurns(id);store.flag(id,"asr_done",true);store.state(id,"done","Расшифровка готова. Можно слушать реплики и создать конспект.",1);automaticSummary=job.optInt("auto_summary")==1&&!job.optString("video_url").isEmpty();
            }
        }
    }catch(Throwable e){if(began){try{store.rebuildTurns(id);store.state(id,"interrupted",cancelled?"Обработка остановлена. Можно продолжить с контрольной точки.":"Обработка прервана: "+message(e),0);}catch(Throwable ignored){}}}
        finally{SpeechModels.Recognizer a=activeAsr;activeAsr=null;if(a!=null)try{a.close();}catch(Exception ignored){}if(wake!=null&&wake.isHeld())wake.release();if(automaticSummary&&!cancelled)try{startForegroundService(new Intent(this,SummaryService.class).setAction(SummaryService.START).putExtra("id",id));}catch(Exception e){store.state(id,"done","Расшифровка готова. Нажмите «Создать конспект», чтобы продолжить.",1);}new Handler(Looper.getMainLooper()).post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}
    }
    private ModelLease awaitLease()throws Exception{long until=SystemClock.elapsedRealtime()+30000;while(true){guard();try{return new ModelLease(getFilesDir());}catch(IOException e){if(SystemClock.elapsedRealtime()>until)throw e;Thread.sleep(100);}}}
    private void guard()throws Exception{
        if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException("Остановлено");
        PowerManager pm=getSystemService(PowerManager.class);if(pm.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE)throw new IOException("Телефон перегрелся. Дайте ему остыть и продолжите обработку");
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();getSystemService(ActivityManager.class).getMemoryInfo(mi);
        if(mi.lowMemory||mi.availMem<768L*1024*1024)throw new IOException("Недостаточно свободной RAM. Закройте тяжёлые приложения и продолжите");
    }
    private void progress(String text,double p)throws Exception{
        guard();long now=SystemClock.elapsedRealtime();if(now-lastHeartbeat<1000)return;lastHeartbeat=now;store.state(id,"processing",text,p);getSystemService(NotificationManager.class).notify(3,VoiceNotifications.build(this,text,SpeechService.class,CANCEL));
    }
    private void vad(PcmFile audio,File models)throws Exception{
        store.getWritableDatabase().delete("regions","mid=?",new String[]{id});long total=audio.samples(),start=-1,lastVoice=0,rawStart=0;int idx=0;final long padding=3840,silence=10400,minSpeech=4000;
        try(SpeechModels.Vad vad=new SpeechModels.Vad(models)){
            for(long pos=0;pos<total;pos+=512){float[] block=audio.read(pos,512);float p=vad.probability(block,block.length);
                if(p>=0.5){if(start<0){start=Math.max(0,pos-padding);rawStart=pos;}lastVoice=pos+block.length;}
                else if(start>=0&&p>=0.35)lastVoice=pos+block.length;
                if(start>=0&&pos-lastVoice>=silence){if(lastVoice-rawStart>=minSpeech)store.region(id,idx++,start,Math.min(total,lastVoice+padding));start=-1;}
                if(pos%16384==0)progress("Поиск речи · "+VoiceStore.time(pos/16)+" / "+VoiceStore.time(total/16),0.08*pos/total);
            }
            if(start>=0&&lastVoice-rawStart>=minSpeech)store.region(id,idx,start,Math.min(total,lastVoice+padding));
        }store.flag(id,"vad_done",true);
    }
    private void speakers(PcmFile audio,File models,int expected)throws Exception{
        SpeakerClusters clusters=new SpeakerClusters(expected);int existing=0;
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT idx,vector FROM embeddings WHERE mid=? ORDER BY idx",new String[]{id})){while(c.moveToNext()){clusters.add(VoiceStore.vector(c.getBlob(1)));existing=Math.max(existing,c.getInt(0)+1);}}
        try(SpeechModels.Speaker speaker=new SpeechModels.Speaker(models,2);Cursor r=store.regions(id)){
            int idx=0;while(r.moveToNext()){long begin=r.getLong(1),end=r.getLong(2);
                for(long pos=begin;pos<end;pos+=24000){int length=(int)Math.min(48000,end-pos);if(length<12800)break;
                    if(idx>=existing){guard();float[] v=speaker.embed(audio.read(pos,length));store.embedding(id,idx,pos/16,(pos+length)/16,v);clusters.add(v);}
                    idx++;progress("Спикеры · "+VoiceStore.time(pos/16)+" / "+VoiceStore.time(audio.samples()/16),0.08+0.24*pos/audio.samples());
                    if(pos+length>=end)break;
                }
            }
        }
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT idx,vector FROM embeddings WHERE mid=? ORDER BY idx",new String[]{id})){
            while(c.moveToNext()){guard();SpeakerClusters.Match m=clusters.match(VoiceStore.vector(c.getBlob(1)));ContentValues v=new ContentValues();v.put("speaker",m.speaker);v.put("score",m.similarity);store.getWritableDatabase().update("embeddings",v,"mid=? AND idx=?",new String[]{id,""+c.getInt(0)});}
        }
    }
    private void transcribe(PcmFile audio,File models,boolean diarize,String language)throws Exception{
        progress(language.equals("en")?"Loading Moonshine Streaming Medium…":"Загрузка GigaAM v3…",0.32);
        try(SpeechModels.Recognizer asr=language.equals("en")?new MoonshineAsr(MoonshineAssets.prepare(this)):new SpeechModels.Asr(models,2);Cursor regions=store.regions(id)){
            activeAsr=asr;int idx=0;
            while(regions.moveToNext()){long start=regions.getLong(1),end=regions.getLong(2);
                for(long core=start;core<end;core+=192000){long coreEnd=Math.min(end,core+192000);
                    if(!store.partDone(id,idx)){guard();long left=Math.max(0,core-3840),right=Math.min(audio.samples(),coreEnd+3840);List<SpeechModels.Word> words=asr.recognize(audio.read(left,(int)(right-left)),left/16);guard();store.part(id,idx,words,diarize,core/16,coreEnd/16,coreEnd==end);}
                    idx++;progress("Распознавание · "+VoiceStore.time(coreEnd/16)+" / "+VoiceStore.time(audio.samples()/16),0.32+0.68*coreEnd/audio.samples());
                }
            }
            activeAsr=null;
        }
    }
    private static String message(Throwable e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}
    @Override public void onDestroy(){cancelled=true;SpeechModels.Recognizer a=activeAsr;if(a!=null)a.cancel();executor.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();store.close();super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid());}
    @Override public IBinder onBind(Intent i){return null;}
}
