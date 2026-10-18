package dev.pocketllama.voice;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.media.*;
import android.os.*;
import dev.pocketllama.InferenceService;
import java.io.*;
import java.util.concurrent.*;

/** Mic thread only writes PCM; no inference, network, or unbounded audio queues. */
public final class RecorderService extends Service {
    public static final String RECORD="dev.pocketllama.voice.RECORD",STOP="dev.pocketllama.voice.STOP_RECORD",PAUSE="dev.pocketllama.voice.PAUSE";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();private final Object pauseLock=new Object();
    private volatile boolean stop,paused,silenced;private volatile AudioRecord mic;private Future<?> task;private VoiceStore store;private String id;
    private PowerManager.WakeLock wake;
    @Override public void onCreate(){super.onCreate();if(dev.pocketllama.security.Vault.ready(this))store=new VoiceStore(this);else stopSelf();}
    @Override public int onStartCommand(Intent i,int flags,int startId){if(store==null){stopSelf();return START_NOT_STICKY;}
        if(i==null||STOP.equals(i.getAction())){stop=true;unblock();if(task==null)stopSelf();return START_NOT_STICKY;}
        if(PAUSE.equals(i.getAction())){if(task!=null){paused=!paused;VoiceState.paused=paused;AudioRecord m=mic;if(m!=null)try{if(paused)m.stop();else m.startRecording();}catch(Exception e){stop=true;}synchronized(pauseLock){pauseLock.notifyAll();}}return START_NOT_STICKY;}
        if(task!=null)return START_NOT_STICKY;
        if(dev.pocketllama.photo.PhotoStore.busy(this)){stopSelf();return START_NOT_STICKY;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        id=i.getStringExtra("id");VoiceState.samples=0;VoiceState.recording=VoiceState.busy=true;VoiceState.id=id;stop=paused=false;
        if(Build.VERSION.SDK_INT>=34)startForeground(2,VoiceNotifications.build(this,"Запись начинается…",RecorderService.class,STOP),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(2,VoiceNotifications.build(this,"Запись начинается…",RecorderService.class,STOP));
        task=executor.submit(this::record);return START_NOT_STICKY;
    }
    private void record(){boolean began=false;String reason="Запись сохранена. Нажмите «Распознать».";PcmFile file=null;
        try{store.startJob(id,"recording");began=true;startService(new Intent(this,InferenceService.class).setAction(InferenceService.STOP));
            File path=VoiceStore.audio(this,id);if(path.getParentFile().getUsableSpace()<64*1024*1024L)throw new IOException("Недостаточно места для записи");
            wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":recorder");wake.acquire(6*60*60*1000L);
            int buffer=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(buffer<=0)throw new IOException("Микрофон не поддерживает PCM 16 кГц");
            AudioRecord recorder=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(Math.max(buffer*4,16384)).build();
            mic=recorder;if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Не удалось открыть микрофон");
            recorder.registerAudioRecordingCallback(getMainExecutor(),new AudioManager.AudioRecordingCallback(){@Override public void onRecordingConfigChanged(java.util.List<AudioRecordingConfiguration> configs){AudioRecordingConfiguration c=recorder.getActiveRecordingConfiguration();silenced=c!=null&&c.isClientSilenced();}});
            file=new PcmFile(path,true,dev.pocketllama.security.Vault.key(this));recorder.startRecording();short[] block=new short[2048];long checkpoint=0;
            while(!stop&&!Thread.currentThread().isInterrupted()){
                if(paused){file.checkpoint();store.state(id,"paused","Пауза · "+VoiceStore.time(file.samples()/16),0);synchronized(pauseLock){while(paused&&!stop)pauseLock.wait(1000);}continue;}
                if(silenced)throw new IOException("Android заглушил микрофон (например, из-за звонка). Сохранённая запись не потеряна");
                int count=recorder.read(block,0,block.length,AudioRecord.READ_BLOCKING);
                if(count<=0){if(stop||paused)continue;throw new IOException("Чтение микрофона прервано: "+count);}
                file.append(block,count);VoiceState.samples=file.samples();double sum=0;for(int j=0;j<count;j++)sum+=(double)block[j]*block[j];VoiceState.level=(float)Math.min(1,Math.sqrt(sum/count)/12000.0);
                long now=SystemClock.elapsedRealtime();if(now-checkpoint>2000){file.checkpoint();store.samples(id,file.samples());String text="Запись · "+VoiceStore.time(file.samples()/16);store.state(id,"recording",text,0);getSystemService(NotificationManager.class).notify(2,VoiceNotifications.build(this,text,RecorderService.class,STOP));checkpoint=now;if(path.getParentFile().getUsableSpace()<64*1024*1024L)throw new IOException("Мало свободного места: запись остановлена и сохранена");}
                if(file.samples()>=16000L*6*3600)throw new IOException("Достигнут лимит непрерывной записи 6 часов; запись сохранена");
            }
        }catch(Throwable e){reason="Запись завершена: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
        finally{AudioRecord m=mic;mic=null;if(m!=null){try{m.stop();}catch(Exception ignored){}m.release();}
            if(file!=null){try{file.checkpoint();store.samples(id,file.samples());file.close();}catch(Exception e){reason="Проверьте свободное место: "+e.getMessage();}}
            if(began)store.state(id,"ready",reason,0);VoiceState.recording=VoiceState.busy=VoiceState.paused=false;VoiceState.level=0;VoiceState.id="";
            if(wake!=null&&wake.isHeld())wake.release();new Handler(Looper.getMainLooper()).post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}
    }
    private void unblock(){paused=false;synchronized(pauseLock){pauseLock.notifyAll();}AudioRecord m=mic;if(m!=null)try{m.stop();}catch(Exception ignored){}}
    @Override public void onDestroy(){stop=true;unblock();executor.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
