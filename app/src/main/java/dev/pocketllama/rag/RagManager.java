package dev.pocketllama.rag;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import dev.pocketllama.*;
import dev.pocketllama.models.VerifiedDownload;
import dev.pocketllama.security.GateSession;
import dev.pocketllama.security.Vault;
import dev.pocketllama.voice.ModelLease;
import dev.pocketllama.voice.VoiceStore;
import dev.pocketllama.photo.PhotoStore;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** One foreground, cancellable operation. Chat and ASR never share RAM with the embedder. */
public final class RagManager {
    public interface Progress {void update(JSONObject state);}
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private final Context context;
    private final long epoch=GateSession.epoch();
    private final RagSources.Check external;
    private final Progress progress;
    private volatile RagRuntime runtime;
    private volatile VerifiedDownload download;
    private volatile boolean cancelled,closed;
    private ModelLease lease;
    private boolean returnChat;
    private long pressureAt;
    private PowerManager.WakeLock wake;
    private Thread watchdog;
    public RagManager(Context context,RagSources.Check external,Progress progress){this.context=context.getApplicationContext();this.external=external;this.progress=progress;}
    public static boolean busy(){return BUSY.get();}
    private void check()throws Exception {
        if(cancelled||Thread.currentThread().isInterrupted()||!GateSession.isOpen()||epoch!=GateSession.epoch())throw new InterruptedException("Поиск остановлен");external.run();
        if(runtime!=null&&SystemClock.elapsedRealtime()-pressureAt>1000){pressureAt=SystemClock.elapsedRealtime();MemorySnapshot m=MemorySnapshot.capture(context);if(m.lowMemory||m.availableBytes<Math.max(m.thresholdBytes,192L*1024*1024))throw new IOException("Недостаточно свободной памяти для поиска");if(context.getSystemService(PowerManager.class).getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE)throw new IOException("Телефон перегрелся. Дайте ему остыть");}
    }
    private void begin()throws Exception {
        check();if(!BUSY.compareAndSet(false,true))throw new IOException("База знаний уже обновляется");
        try {
            if(PhotoStore.busy(context))throw new IOException("Сначала завершите распознавание документа");
            try(VoiceStore voices=new VoiceStore(context)){if(voices.anyBusy())throw new IOException("Сначала завершите запись или обработку");}
            wake=context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PocketLlama:Knowledge");wake.setReferenceCounted(false);wake.acquire(30*60*1000L);
            watchdog=new Thread(()->{while(!closed){try{Thread.sleep(250);if(!GateSession.isOpen()||GateSession.epoch()!=epoch){cancel();break;}}catch(InterruptedException e){break;}}},"rag-access-watchdog");watchdog.start();
        }catch(Exception e){BUSY.set(false);throw e;}
    }
    private void stage(String name,String detail)throws Exception{check();progress.update(new JSONObject().put("working",true).put("stage",name).put("detail",I18n.t(detail)));}
    private RagRuntime engine()throws Exception {
        check();if(runtime!=null)return runtime;
        stage("prepare","Подготавливаю локальный поиск…");File model=new RagAssets(context).verified(this::check);
        returnChat=InferenceService.exists||RuntimeState.active;
        if(returnChat){context.startService(new Intent(context,InferenceService.class).setAction(InferenceService.STOP));long end=SystemClock.elapsedRealtime()+20000;while(InferenceService.exists||RuntimeState.active){check();if(SystemClock.elapsedRealtime()>end)throw new IOException("Модель чата ещё освобождает память. Повторите поиск");Thread.sleep(100);}}
        check();lease=new ModelLease(context.getFilesDir());
        MemorySnapshot m=MemorySnapshot.capture(context);if(m.lowMemory||m.availableBytes<Math.max(m.thresholdBytes,model.length()+256L*1024*1024))throw new IOException("Недостаточно свободной памяти для EmbeddingGemma");
        runtime=new RagRuntime(context,model,this::check);return runtime;
    }
    private final RagStore.Embedder embed=new RagStore.Embedder(){public float[] document(String title,String text)throws Exception{return engine().embed(RagText.documentPrompt(title,text));}public float[] query(String text)throws Exception{return engine().embed(RagText.queryPrompt(text));}};
    public JSONObject search(String query,int limit,String type)throws Exception {
        begin();try(RagStore index=new RagStore(context)) {
            new RagAssets(context).verified(this::check);RagSources reader=new RagSources(context,this::check);
            sync(index,reader);stage("search","Ищу в личной базе знаний…");
            if(index.status().optInt("passages")==0)return new JSONObject().put("query",query).put("sources",new org.json.JSONArray()).put("indexedPassages",0).put("complete",true);
            float[] vector=index.query(query,embed,this::check);JSONObject result=index.search(query,vector,limit,type,reader,this::check);check();return result;
        }finally{finish();}
    }
    public void build()throws Exception {begin();try(RagStore index=new RagStore(context)){new RagAssets(context).verified(this::check);sync(index,new RagSources(context,this::check));}finally{finish();}}
    private void sync(RagStore index,RagSources reader)throws Exception {
        stage("index","Проверяю изменения в материалах…");final long[] at={0};
        index.sync(reader,embed,(sources,passages,computed)->{check();long now=SystemClock.elapsedRealtime();if(now-at[0]<300)return;at[0]=now;progress.update(new JSONObject().put("working",true).put("stage","index").put("detail",I18n.t("Обновляю базу знаний…")).put("sources",sources).put("passages",passages).put("computed",computed));},this::check);
    }
    public void download()throws Exception {begin();try{download=new VerifiedDownload();new RagAssets(context).download(download,(done,total,stage)->{check();progress.update(new JSONObject().put("working",true).put("stage","download").put("detail",I18n.t(stage)).put("done",done).put("total",total));},this::check);}finally{finish();}}
    public void clear()throws Exception {begin();try(RagStore store=new RagStore(context)){store.clear();}finally{finish();}}
    public void cancel(){cancelled=true;RagRuntime r=runtime;if(r!=null)r.cancel();VerifiedDownload d=download;if(d!=null)d.cancel();}
    private void finish() {
        closed=true;boolean interrupted=Thread.interrupted();
        try {
            RagRuntime r=runtime;runtime=null;if(r!=null)r.close();
            if(lease!=null)try{lease.close();}catch(Exception ignored){}lease=null;
            if(watchdog!=null)watchdog.interrupt();if(wake!=null&&wake.isHeld())wake.release();
            if(returnChat&&GateSession.isOpen()&&GateSession.epoch()==epoch) {
                progress.update(state("return","Возвращаю модель чата…"));
                context.startForegroundService(new Intent(context,InferenceService.class).setAction(InferenceService.START));
                long deadline=SystemClock.elapsedRealtime()+180000;
                while(!RuntimeState.ready&&SystemClock.elapsedRealtime()<deadline){if(cancelled||!GateSession.isOpen()||GateSession.epoch()!=epoch)break;if(!RuntimeState.active&&!InferenceService.exists&&SystemClock.elapsedRealtime()>deadline-175000)break;try{Thread.sleep(150);}catch(InterruptedException e){interrupted=true;break;}}
            }
        }catch(Exception e){RuntimeState.log("Knowledge: chat restart failed ("+e.getClass().getSimpleName()+")");}
        finally{BUSY.set(false);if(interrupted)Thread.currentThread().interrupt();}
    }
    private static JSONObject state(String phase,String text){JSONObject j=new JSONObject();try{j.put("working",true).put("stage",phase).put("detail",I18n.t(text));}catch(Exception ignored){}return j;}
    public static JSONObject status(Context context)throws Exception {
        JSONObject status=new JSONObject().put("working",busy()).put("modelReady",new RagAssets(context).ready()).put("modelBytes",328577056).put("sources",0).put("passages",0);
        if(new File(Vault.root(context),"knowledge.db").isFile())try(RagStore store=new RagStore(context)){JSONObject counts=store.status();status.put("sources",counts.getInt("sources")).put("passages",counts.getInt("passages"));}
        return status;
    }
}
