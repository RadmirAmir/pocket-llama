package dev.pocketllama.voice;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.webkit.*;
import android.widget.FrameLayout;
import dev.pocketllama.*;
import dev.pocketllama.core.*;
import dev.pocketllama.video.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Presentation shell only. Audio, inference, persistence and foreground work remain native. */
public final class VoiceActivity extends dev.pocketllama.security.SecureActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(), files = Executors.newSingleThreadExecutor();
    private VoiceStore store; private ConfigStore config; private WebView web; private FrameLayout root;
    private volatile boolean destroyed, ready, resumed, importing; private boolean sqlFold;
    private String selection = "", exportId = "", exportFormat = "md", permissionRequest = "";
    private dev.pocketllama.security.AudioSource playerSource;private MediaPlayer player; private String playerId = ""; private boolean playerReady; private long pendingSeek; private float playbackSpeed=1f;
    private final Runnable pulse = new Runnable() { public void run() {
        if (!resumed || destroyed) return;
        JSONObject j = object("recording", VoiceState.recording, "paused", VoiceState.paused, "id", VoiceState.id,
            "level", VoiceState.level, "samples", VoiceState.samples, "playing", false, "position", 0, "duration", 0);
        if (playerReady && player != null) try { j.put("playing", player.isPlaying()); j.put("position", player.getCurrentPosition()); j.put("duration", player.getDuration()); j.put("playerId", playerId); } catch (Exception ignored) { }
        emit("pulse", j);
        main.postDelayed(this, VoiceState.recording || playerReady && player != null && player.isPlaying() ? 120 : 900);
    }};
    @Override protected void onSecureCreate(Bundle state) {
         store = new VoiceStore(this); config = new ConfigStore(this);
        if (state != null) { selection=state.getString("selection", ""); exportId=state.getString("exportId", ""); exportFormat=state.getString("exportFormat", "md"); }
        root = new FrameLayout(this); web = new WebView(this); root.addView(web, new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        UiHost.attach(this, root, web); web.addJavascriptInterface(new Bridge(), "Studio"); web.loadUrl(UiHost.ORIGIN + "studio.html");
    }
    public final class Bridge {
        @JavascriptInterface public String language() { return I18n.language(); }
        @JavascriptInterface public String translations() { return I18n.dictionary(); }
        @JavascriptInterface public String appearance() { return UiHost.appearance(VoiceActivity.this); }
        @JavascriptInterface public void ready() {if(!dev.pocketllama.security.GateSession.isOpen())return; ready = true; io.execute(() -> { try { store.recover(VoiceActivity.this, SummaryService.speechAlive(VoiceActivity.this)); store.getWritableDatabase();sqlFold=true; } catch (Exception e) { error(e); } emit("boot", object("selection", !VoiceState.id.isEmpty()?VoiceState.id:selection, "screen", getIntent().getStringExtra("screen"))); main.post(()->consumeSharedLink()); }); }
        @JavascriptInterface public void call(String request, String action, String payload) {
            if(!dev.pocketllama.security.GateSession.isOpen()){reject(request,new java.io.IOException(dev.pocketllama.I18n.t("Введите код входа")));return;}
            if (destroyed || request == null || request.length()>80 || payload == null || payload.length()>100000) return;
            try {
                JSONObject args = new JSONObject(payload);
                if (action.equals("copyTranscript")) {
                    copyTranscript(request,args.optString("id"));
                } else if (Arrays.asList("record","pauseRecord","stopRecord","process","summarize","cancel","play","seek","speed","importAudio","importVideo","openVideo","export","photos","notes","chat","theme","language","importModel","startServer","stopServer","copy","copyApiKey","security","lock","licenses","downloadModel","pauseModelDownload","catalogueSource").contains(action)) {
                    main.post(() -> { try { ui(request, action, args); } catch(Exception e) { reject(request,e); } });
                } else io.execute(() -> { try { reply(request, data(action,args)); } catch(Exception e) { reject(request,e); } });
            } catch (Exception e) { reject(request,e); }
        }
    }
    private JSONObject data(String action, JSONObject a) throws Exception {
        dev.pocketllama.security.GateSession.requireOpen();
        String id = a.optString("id");
        switch (action) {
            case "snapshot": {
                JSONObject result=object("recording",VoiceState.recording,"recordId",VoiceState.id,"busy",dev.pocketllama.photo.PhotoStore.busy(this)||store.anyBusy(),"modelAvailable",!config.load().modelName.isEmpty(),"importing",importing);
                if(a.optBoolean("library")) result.put("library", library(a.optString("query"),a.optInt("limit",100)));
                if(!id.isEmpty()) {
                    JSONObject j=store.get(id); String summary=j.optString("summary"); j.remove("summary"); j.put("imported",(!j.optString("source_uri").isEmpty()||!j.optString("video_url").isEmpty())); j.remove("source_uri"); j.remove("summary_key");
                    j.put("summaryTag",summary.length()+":"+summary.hashCode()); j.put("has_summary",!summary.isEmpty()); j.put("turnCount", countTurns(id,"")); result.put("meeting",j);
                }
                return result;
            }
            case "detail": {
                JSONObject j=store.get(id); j.put("imported",(!j.optString("source_uri").isEmpty()||!j.optString("video_url").isEmpty()));j.remove("source_uri"); j.remove("summary_key"); j.put("turnCount",countTurns(id,""));j.put("summaryTag",j.optString("summary").length()+":"+j.optString("summary").hashCode());
                JSONArray names=new JSONArray(); for(int n=0;n<=12;n++) names.put(store.speaker(id,n)); j.put("names",names); return j;
            }
            case "turns": return turns(id,a.optString("query"),a.optInt("offset"));
            case "source": {
                try(Cursor c=store.getReadableDatabase().rawQuery("SELECT t.start,COALESCE(e.speaker,t.speaker),COALESCE(e.text,t.text) FROM turns t LEFT JOIN edits e ON e.mid=t.mid AND e.idx=t.idx WHERE t.mid=? AND t.idx=?",new String[]{id,""+a.getInt("index")})) {
                    if(!c.moveToFirst()) throw new IOException(dev.pocketllama.I18n.t("Реплика не найдена")); return object("start",c.getLong(0),"name",store.speaker(id,c.getInt(1)),"text",c.getString(2));
                }
            }
            case "rename": {
                String title=a.optString("title").trim(); if(title.isEmpty()) throw new IOException(dev.pocketllama.I18n.t("Введите название записи")); store.rename(id,title); return object("ok",true);
            }
            case "name": editable(id); store.name(id,bounded(a.optInt("speaker"),0,12),a.optString("name").trim()); return object("ok",true);
            case "edit": editable(id); store.editTurn(id,a.getInt("index"),bounded(a.optInt("speaker"),0,12),a.optString("text").trim()); return object("ok",true);
            case "delete": {
                if(importing) throw new IOException(dev.pocketllama.I18n.t("Дождитесь завершения импорта"));
                store.delete(this,id); main.post(() -> { if(id.equals(playerId)) releasePlayer(); }); return object("ok",true);
            }
            case "config": return settings();
            case "catalogue": return new dev.pocketllama.models.CatalogRepository(this).snapshot();
            case "refreshCatalogue": new dev.pocketllama.models.CatalogRepository(this).refresh();return settings();
            case "useCatalogueModel": requireIdleModels();new dev.pocketllama.models.CatalogRepository(this).activate(a.getString("id"));return settings();
            case "saveConfig": {
                requireIdleModels(); ServerConfig c=config.load();
                c.modelName=a.optString("modelName",c.modelName); c.projectorName=a.optString("projectorName",c.projectorName);
                c.backend=a.optString("backend",c.backend); c.contextPerSlot=a.optInt("contextPerSlot",c.contextPerSlot); c.slots=a.optInt("slots",c.slots); c.threads=a.optInt("threads",c.threads);
                c.gpuLayers=a.optInt("gpuLayers",c.gpuLayers); c.budgetMiB=a.optInt("budgetMiB",c.budgetMiB); c.systemReserveMiB=a.optInt("systemReserveMiB",c.systemReserveMiB); c.unknownKvMiB=a.optInt("unknownKvMiB",c.unknownKvMiB); c.port=a.optInt("port",c.port);
                c.cacheK=a.optString("cacheK",c.cacheK); c.cacheV=a.optString("cacheV",c.cacheV); c.mmap=a.optBoolean("mmap",c.mmap); c.lan=a.optBoolean("lan",c.lan); c.stayAwake=a.optBoolean("stayAwake",c.stayAwake);
                c.strictMemory=a.optBoolean("strictMemory",c.strictMemory); c.vulkanCompatibility=a.optBoolean("vulkanCompatibility",c.vulkanCompatibility); c.cpuAcceleration=a.optBoolean("cpuAcceleration",c.cpuAcceleration);
                // Empty selection is valid before the first import. All other parameters still use the native validator.
                String model=c.modelName; if(model.isEmpty()) c.modelName="empty.gguf"; c.validate(); c.modelName=model;
                if(!model.isEmpty()&&!new File(config.modelsDir(),model).isFile()) throw new IOException(dev.pocketllama.I18n.t("Выберите импортированную модель"));
                if(!c.projectorName.isEmpty()&&!new File(config.projectorsDir(),c.projectorName).isFile()) throw new IOException(dev.pocketllama.I18n.t("mmproj не найден"));
                config.save(c); return settings();
            }
            case "deleteModel": {
                requireIdleModels(); boolean projector=a.optBoolean("projector"); String name=a.getString("name");
                if(!name.matches("[A-Za-z0-9._-]+\\.gguf")) throw new IOException(dev.pocketllama.I18n.t("Некорректный файл"));
                if(!new File(projector?config.projectorsDir():config.modelsDir(),name).delete()) throw new IOException(dev.pocketllama.I18n.t("Не удалось удалить файл"));
                ServerConfig c=config.load(); if(projector&&c.projectorName.equals(name))c.projectorName=""; if(!projector&&c.modelName.equals(name)){c.modelName="";c.projectorName="";} config.save(c); return settings();
            }
            case "inspect": {
                ServerConfig c=config.load();c.validate();GgufMetadata meta=GgufReader.read(new File(config.modelsDir(),c.modelName));MemorySnapshot m=MemorySnapshot.capture(this);
                long projector=c.projectorName.isEmpty()?0:new File(config.projectorsDir(),c.projectorName).length();
                return object("model",c.modelName,"architecture",meta.architecture(),"bytes",meta.fileBytes,"memory",MemoryPolicy.plan(c,meta,m.totalBytes,m.availableBytes,projector).describe(),"metadata",new JSONObject(meta.values));
            }
            case "logs": return object("logs",RuntimeState.logText(),"memory",RuntimeState.memory,"endpoint",RuntimeState.endpoint,"cpuProfile",RuntimeState.cpuProfile);
            default: throw new IOException(dev.pocketllama.I18n.t("Неизвестное действие"));
        }
    }
    private void ui(String request, String action, JSONObject a) throws Exception {
        dev.pocketllama.security.GateSession.requireOpen();
        String id=a.optString("id");
        switch(action) {
            case "record":
                if(store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing) throw new IOException(dev.pocketllama.I18n.t("Сначала завершите текущую задачу"));
                ArrayList<String> permissions=new ArrayList<>();
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.RECORD_AUDIO);
                if(Build.VERSION.SDK_INT>=33&&!getSharedPreferences("interface",MODE_PRIVATE).getBoolean("notificationsAsked",false)&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){permissions.add(Manifest.permission.POST_NOTIFICATIONS);getSharedPreferences("interface",MODE_PRIVATE).edit().putBoolean("notificationsAsked",true).apply();}
                if(!permissions.isEmpty()){permissionRequest=request;requestPermissions(permissions.toArray(new String[0]),40);return;}
                beginRecord(request); return;
            case "pauseRecord": command(RecorderService.class,RecorderService.PAUSE,id); break;
            case "stopRecord": command(RecorderService.class,RecorderService.STOP,id); break;
            case "process": {
                if(store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing) throw new IOException(dev.pocketllama.I18n.t("Сначала завершите текущую задачу"));
                if(a.optBoolean("restart"))store.restartRecognition(id);
                store.configure(id,a.optBoolean("diarize",true),bounded(a.optInt("expected"),0,12));
                store.recognitionLanguage(id,I18n.language());
                JSONObject source=store.get(id);
                if(!source.optString("video_url").isEmpty()&&!VoiceStore.audio(this,id).isFile()){startForegroundService(new Intent(this,VideoImportService.class).setAction(VideoImportService.START).putExtra("id",id));break;}
                Intent work=new Intent(this,SpeechService.class).setAction(SpeechService.PROCESS).putExtra("id",id);if(source.optLong("samples")==0&&!source.optString("source_uri").isEmpty())work.putExtra("uri",source.optString("source_uri"));startForegroundService(work);break;
            }
            case "summarize":
                if(store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing) throw new IOException(dev.pocketllama.I18n.t("Сначала завершите текущую задачу"));
                if(config.load().modelName.isEmpty()) throw new IOException(dev.pocketllama.I18n.t("Добавьте модель для конспектов в Настройках"));
                if(a.optBoolean("restart"))store.clearSummary(id);
                startForegroundService(new Intent(this,SummaryService.class).setAction(SummaryService.START).putExtra("id",id)); break;
            case "cancel": if(store.get(id).optString("state").equals("downloading"))command(VideoImportService.class,VideoImportService.CANCEL,id);else{command(SpeechService.class,SpeechService.CANCEL,id);command(SummaryService.class,SummaryService.CANCEL,id);}break;
            case "importVideo": {
                if(store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing)throw new IOException(dev.pocketllama.I18n.t("Сначала завершите текущую задачу"));
                String url=VideoUrl.parse(a.optString("url"));boolean automatic=a.optBoolean("summarize",true);
                if(automatic&&config.load().modelName.isEmpty())throw new IOException(dev.pocketllama.I18n.t("Добавьте GGUF в Настройках или выберите только расшифровку"));
                String video=store.create(dev.pocketllama.I18n.t("Видео · ")+VideoUrl.host(url),a.optBoolean("diarize",true),0,I18n.language());
                ContentValues values=new ContentValues();values.put("video_url",url);values.put("video_platform",VideoUrl.host(url));values.put("video_stage","queued");values.put("auto_summary",automatic?1:0);store.update(video,values);selection=video;
                try{startForegroundService(new Intent(this,VideoImportService.class).setAction(VideoImportService.START).putExtra("id",video));}catch(Exception e){store.state(video,"interrupted",dev.pocketllama.I18n.t("Не удалось запустить загрузку. Можно повторить."),0);throw e;}
                reply(request,object("id",video));return;
            }
            case "openVideo": {String url=store.get(id).optString("video_url");startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(VideoUrl.at(url,a.optLong("position")))));break;}
            case "play":
                if(playerReady&&player!=null&&playerId.equals(id)&&player.isPlaying()&&!a.has("position"))player.pause();
                else playAt(id,a.optLong("position",playerReady&&playerId.equals(id)?player.getCurrentPosition():0)); break;
            case "speed":
                playbackSpeed=(float)Math.max(0.75,Math.min(2,a.optDouble("value",1))); if(playerReady&&player!=null){boolean wasPlaying=player.isPlaying();player.setPlaybackParams(player.getPlaybackParams().setSpeed(playbackSpeed));if(!wasPlaying)player.pause();} break;
            case "seek":
                if(playerReady&&player!=null&&playerId.equals(id)) player.seekTo((int)Math.max(0,a.optLong("position")),MediaPlayer.SEEK_CLOSEST);
                else playAt(id,a.optLong("position")); break;
            case "importAudio":
                if(store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing) throw new IOException(dev.pocketllama.I18n.t("Сначала завершите текущую задачу"));
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"}),10); break;
            case "export": {
                exportId=id;exportFormat=a.optString("format","md"); if(!Arrays.asList("md","srt","json","wav").contains(exportFormat))throw new IOException(dev.pocketllama.I18n.t("Неизвестный формат"));
                JSONObject meeting=store.get(id); String filename=meeting.optString("title",dev.pocketllama.I18n.t("Запись")).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");
                final String format=exportFormat;
                new android.app.AlertDialog.Builder(this).setTitle(dev.pocketllama.I18n.t("Экспортировать копию?")).setMessage(dev.pocketllama.I18n.t("Файл будет сохранён без шифрования Pocket в выбранную вами папку. Доступ к этой копии зависит от места сохранения.")).setNegativeButton(dev.pocketllama.I18n.t("Отмена"),null).setPositiveButton(dev.pocketllama.I18n.t("Выбрать папку"),(dialog,which)->{if(!dev.pocketllama.security.GateSession.isOpen())return;exportId=id;exportFormat=format;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(format.equals("wav")?"audio/wav":format.equals("json")?"application/json":"text/plain").putExtra(Intent.EXTRA_TITLE,filename+"."+format),11);}).show(); break;
            }
            case "notes": startActivity(new Intent(this,dev.pocketllama.notes.NotesActivity.class)); break;
            case "photos": startActivity(new Intent(this,dev.pocketllama.photo.PhotoActivity.class)); break;
            case "licenses": {
                String[] names=getAssets().list("notices");StringBuilder text=new StringBuilder();if(names!=null){Arrays.sort(names);for(String name:names){try(InputStream input=getAssets().open("notices/"+name)){byte[] raw=new byte[65536];int n; text.append("\n\n===== ").append(name).append(" =====\n");while((n=input.read(raw))!=-1){text.append(new String(raw,0,n,java.nio.charset.StandardCharsets.UTF_8));if(text.length()>1000000)throw new IOException(dev.pocketllama.I18n.t("Слишком большой файл лицензий"));}}}}
                android.widget.ScrollView scroll=new android.widget.ScrollView(this);android.widget.TextView content=new android.widget.TextView(this);content.setText(text);content.setTextSize(12);content.setTextIsSelectable(true);int pad=Math.round(20*getResources().getDisplayMetrics().density);content.setPadding(pad,pad,pad,pad);scroll.addView(content);new android.app.AlertDialog.Builder(this).setTitle(dev.pocketllama.I18n.t("Лицензии компонентов")).setView(scroll).setPositiveButton(dev.pocketllama.I18n.t("Закрыть"),null).show();break;
            }
            case "security":startActivity(new Intent(this,dev.pocketllama.security.LockActivity.class).putExtra("change",true));break;
            case "lock":lockNow();break;
            case "chat": startActivity(new Intent(this,ChatActivity.class)); break;
            case "theme": UiHost.appearance(this,root,web,a.optString("value","auto")); break;
            case "language":
                I18n.setLanguage(a.getString("value"));getIntent().putExtra("screen","settings");
                main.postDelayed(()->{if(!destroyed){web.setTag(I18n.language());web.reload();}},100);break;
            case "downloadModel": {
                dev.pocketllama.models.CatalogRepository repo=new dev.pocketllama.models.CatalogRepository(this);
                dev.pocketllama.models.ModelCatalog.Entry entry=repo.load().find(a.getString("id"));
                JSONObject snapshot=repo.snapshot();if(snapshot.getJSONObject("download").optBoolean("active"))throw new IOException(dev.pocketllama.I18n.t("Сначала приостановите текущую загрузку"));
                boolean allowed=false;JSONArray models=snapshot.getJSONArray("models");for(int i=0;i<models.length();i++){JSONObject m=models.getJSONObject(i);if(m.getString("id").equals(entry.id)){if(!m.getBoolean("compatible"))throw new IOException(m.optString("warning"));if(!m.getBoolean("hasSpace"))throw new IOException(dev.pocketllama.I18n.t("Недостаточно места для модели"));allowed=true;}}
                if(!allowed)throw new IOException(dev.pocketllama.I18n.t("Модель недоступна"));
                startForegroundService(new Intent(this,dev.pocketllama.models.ModelDownloadService.class).setAction(dev.pocketllama.models.ModelDownloadService.START).putExtra("id",entry.id));break;
            }
            case "pauseModelDownload": startService(new Intent(this,dev.pocketllama.models.ModelDownloadService.class).setAction(dev.pocketllama.models.ModelDownloadService.PAUSE));break;
            case "catalogueSource": startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(new dev.pocketllama.models.CatalogRepository(this).load().find(a.getString("id")).source)));break;
            case "importModel": requireIdleModels(); startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),a.optBoolean("projector")?13:12); break;
            case "startServer":
                if(store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing)throw new IOException(dev.pocketllama.I18n.t("Сначала завершите текущую задачу"));
                config.load().validate(); startForegroundService(new Intent(this,InferenceService.class).setAction(InferenceService.START)); break;
            case "stopServer": startService(new Intent(this,InferenceService.class).setAction(InferenceService.STOP)); break;
            case "copy": copyText(a.optString("text"));break;
            case "copyApiKey": {
                ClipData clip=ClipData.newPlainText("API key",config.apiKey()); PersistableBundle extras=new PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);getSystemService(ClipboardManager.class).setPrimaryClip(clip); break;
            }
            default: throw new IOException(dev.pocketllama.I18n.t("Неизвестное действие"));
        }
        reply(request,object("ok",true));
    }
    private void copyTranscript(String request,String id) {
        long epoch=dev.pocketllama.security.GateSession.epoch();
        io.execute(()->{try{
            dev.pocketllama.security.GateSession.requireOpen();
            String text=VoiceExport.transcript(store,id);
            main.post(()->{try{
                dev.pocketllama.security.GateSession.requireOpen();
                if(destroyed||!resumed||epoch!=dev.pocketllama.security.GateSession.epoch())
                    throw new IOException(dev.pocketllama.I18n.t("Введите код входа"));
                copyText(text);reply(request,object("ok",true));
            }catch(Exception e){reject(request,e);}});
        }catch(Exception e){reject(request,e);}});
    }
    private void copyText(String text) {
        ClipData clip=ClipData.newPlainText("Pocket Llama",text);
        PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);
        clip.getDescription().setExtras(extras);getSystemService(ClipboardManager.class).setPrimaryClip(clip);
    }
    private void beginRecord(String request) throws Exception {
        if(store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing) throw new IOException(dev.pocketllama.I18n.t("Сначала завершите текущую задачу"));
        releasePlayer(); String id=store.create(dev.pocketllama.I18n.t("Запись ")+new SimpleDateFormat("d MMMM, HH:mm",Locale.forLanguageTag(I18n.language())).format(new Date()),true,0,I18n.language()); selection=id;
        try { startForegroundService(new Intent(this,RecorderService.class).setAction(RecorderService.RECORD).putExtra("id",id)); }
        catch(Exception e){store.state(id,"interrupted",dev.pocketllama.I18n.t("Не удалось открыть микрофон"),0);throw e;}
        reply(request,object("id",id));
    }
    private void command(Class<?> service,String action,String id){startService(new Intent(this,service).setAction(action).putExtra("id",id));}
    private void editable(String id) throws Exception {if(store.anyBusy()||store.get(id).optInt("asr_done")==0)throw new IOException(dev.pocketllama.I18n.t("Исправления доступны после завершения распознавания"));}
    private void requireIdleModels() throws IOException { if(RuntimeState.active||store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing)throw new IOException(dev.pocketllama.I18n.t("Сначала остановите модель и завершите текущую задачу")); }
    private String fold(String column){return !sqlFold?column:column.equals("m.title")?"m.title_fold":"COALESCE(e.text_fold,t.text_fold)";}
    private int countTurns(String id,String query) {
        if(!sqlFold&&!query.isEmpty()){int total=0;String needle=query.toLowerCase(Locale.ROOT);try(Cursor c=store.turns(id)){while(c.moveToNext())if(c.getString(4).toLowerCase(Locale.ROOT).contains(needle))total++;}return total;}
        String where=query.isEmpty()?"":" AND "+fold("COALESCE(e.text,t.text)")+" LIKE ? ESCAPE '\\'";
        String[] args=query.isEmpty()?new String[]{id}:new String[]{id,"%"+escapeLike(query.toLowerCase(Locale.ROOT))+"%"};
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT count(*) FROM turns t LEFT JOIN edits e ON e.mid=t.mid AND e.idx=t.idx WHERE t.mid=?"+where,args)){return c.moveToFirst()?c.getInt(0):0;}
    }
    private JSONObject turns(String id,String query,int offset) throws Exception {
        offset=Math.max(0,offset); if(query.length()>200)query=query.substring(0,200);
        if(!sqlFold&&!query.isEmpty()){
            JSONArray matches=new JSONArray();int total=0;String needle=query.toLowerCase(Locale.ROOT);
            try(Cursor c=store.turns(id)){while(c.moveToNext())if(c.getString(4).toLowerCase(Locale.ROOT).contains(needle)){
                if(total>=offset&&matches.length()<100)matches.put(object("index",c.getInt(0),"start",c.getLong(1),"end",c.getLong(2),"speaker",c.getInt(3),"text",c.getString(4),"name",store.speaker(id,c.getInt(3))));total++;
            }}return object("id",id,"query",query,"offset",offset,"total",total,"turns",matches);
        }
        String where=query.isEmpty()?"":" AND "+fold("COALESCE(e.text,t.text)")+" LIKE ? ESCAPE '\\'";
        ArrayList<String> args=new ArrayList<>();args.add(id);if(!query.isEmpty())args.add("%"+escapeLike(query.toLowerCase(Locale.ROOT))+"%");args.add("100");args.add(""+offset);
        JSONArray rows=new JSONArray();
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT t.idx,t.start,t.end,COALESCE(e.speaker,t.speaker),COALESCE(e.text,t.text) FROM turns t LEFT JOIN edits e ON e.mid=t.mid AND e.idx=t.idx WHERE t.mid=?"+where+" ORDER BY t.idx LIMIT ? OFFSET ?",args.toArray(new String[0]))) {
            while(c.moveToNext()) rows.put(object("index",c.getInt(0),"start",c.getLong(1),"end",c.getLong(2),"speaker",c.getInt(3),"text",c.getString(4),"name",store.speaker(id,c.getInt(3))));
        }
        return object("id",id,"query",query,"offset",offset,"total",countTurns(id,query),"turns",rows);
    }
    private static String escapeLike(String s){return s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    private JSONObject library(String query,int limit) throws Exception {
        limit=Math.max(50,Math.min(2000,limit)); if(query.length()>200)query=query.substring(0,200);
        boolean fallback=!sqlFold&&!query.isEmpty();
        String where=query.isEmpty()||fallback?"":" WHERE "+fold("m.title")+" LIKE ? ESCAPE '\\' OR EXISTS(SELECT 1 FROM turns t LEFT JOIN edits e ON e.mid=t.mid AND e.idx=t.idx WHERE t.mid=m.id AND "+fold("COALESCE(e.text,t.text)")+" LIKE ? ESCAPE '\\')";
        String like="%"+escapeLike(query.toLowerCase(Locale.ROOT))+"%";String[] args=query.isEmpty()||fallback?new String[]{fallback?"2147483647":""+(limit+1)}:new String[]{like,like,""+(limit+1)};
        JSONArray rows=new JSONArray(); boolean more=false;
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT m.id,m.title,m.created,m.samples,m.state,m.progress,m.asr_done,length(m.summary)>0,(SELECT count(DISTINCT COALESCE(e.speaker,t.speaker)) FROM turns t LEFT JOIN edits e ON e.mid=t.mid AND e.idx=t.idx WHERE t.mid=m.id AND COALESCE(e.speaker,t.speaker)>0),m.video_platform,m.video_url<>'' FROM meetings m"+where+" ORDER BY m.created DESC LIMIT ?",args)){
            while(c.moveToNext()){if(fallback&&!queryHit(c.getString(0),c.getString(1),query))continue;if(rows.length()>=limit){more=true;break;} rows.put(object("id",c.getString(0),"title",c.getString(1),"created",c.getLong(2),"samples",c.getLong(3),"state",c.getString(4),"progress",c.getDouble(5),"asr_done",c.getInt(6),"has_summary",c.getInt(7)>0,"speakers",c.getInt(8),"video_platform",c.getString(9),"video",c.getInt(10)>0));}
        }
        return object("items",rows,"more",more,"query",query);
    }
    private boolean queryHit(String id,String title,String query){
        String needle=query.toLowerCase(Locale.ROOT);if(title.toLowerCase(Locale.ROOT).contains(needle))return true;
        try(Cursor c=store.turns(id)){while(c.moveToNext())if(c.getString(4).toLowerCase(Locale.ROOT).contains(needle))return true;}return false;
    }
    private JSONObject settings() throws Exception {
        ServerConfig c=config.load(); JSONObject values=new JSONObject();
        for(java.lang.reflect.Field field:ServerConfig.class.getFields())values.put(field.getName(),field.get(c));
        MemorySnapshot memory=MemorySnapshot.capture(this);
        JSONArray backends=new JSONArray().put("cpu"); File libs=new File(getApplicationInfo().nativeLibraryDir);if(new File(libs,"libllama_vulkan.so").isFile())backends.put("vulkan");if(new File(libs,"libllama_opencl.so").isFile())backends.put("opencl");
        return object("config",values,"models",modelFiles(config.modelsDir()),"projectors",modelFiles(config.projectorsDir()),"backends",backends,"active",RuntimeState.active,"ready",RuntimeState.ready,"status",RuntimeState.status,"availableBytes",memory.availableBytes,"totalBytes",memory.totalBytes,"importing",importing,"appearance",UiHost.appearance(this),"busy",dev.pocketllama.photo.PhotoStore.busy(this)||store.anyBusy()||VoiceState.busy,"version",DistributionConfig.version(this),"catalogue",new dev.pocketllama.models.CatalogRepository(this).snapshot());
    }
    private JSONArray modelFiles(File dir) throws Exception {
        JSONArray result=new JSONArray(); File[] list=dir.listFiles((d,n)->n.endsWith(".gguf"));if(list!=null){Arrays.sort(list,Comparator.comparing(File::getName));for(File file:list)result.put(object("name",file.getName(),"bytes",file.length()));}return result;
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(deferResult(()->onRequestPermissionsResult(code,permissions,results)))return;if(code==40){String req=permissionRequest;permissionRequest="";try{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new IOException(dev.pocketllama.I18n.t("Доступ к микрофону выключен. Разрешите его в настройках Android, чтобы записать голос."));beginRecord(req);}catch(Exception e){reject(req,e);}}}
    private void playAt(String id,long ms) throws Exception {
        if(VoiceState.recording)throw new IOException(dev.pocketllama.I18n.t("Сначала завершите запись"));
        if(playerReady&&player!=null&&playerId.equals(id)){player.seekTo((int)Math.max(0,Math.min(ms,player.getDuration())),MediaPlayer.SEEK_CLOSEST);player.start();return;}
        releasePlayer();pendingSeek=Math.max(0,ms);playerId=id;MediaPlayer p=new MediaPlayer();player=p;playerSource=new dev.pocketllama.security.AudioSource(new PcmFile(VoiceStore.audio(this,id),false,dev.pocketllama.security.Vault.key(this)));p.setDataSource(playerSource);
        p.setOnPreparedListener(m->{if(player!=m)return;playerReady=true;m.seekTo((int)Math.min(pendingSeek,m.getDuration()),MediaPlayer.SEEK_CLOSEST);if(resumed&&dev.pocketllama.security.GateSession.isOpen()){m.setPlaybackParams(m.getPlaybackParams().setSpeed(playbackSpeed));m.start();}});
        p.setOnCompletionListener(m->emit("playbackEnd",object("id",id)));p.setOnErrorListener((m,a,b)->{error(new IOException(dev.pocketllama.I18n.t("Не удалось воспроизвести запись")));releasePlayer();return true;});p.prepareAsync();
    }
    private void releasePlayer(){MediaPlayer p=player;player=null;playerReady=false;playerId="";if(p!=null)p.release();if(playerSource!=null){try{playerSource.close();}catch(Exception ignored){}playerSource=null;}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(deferResult(()->onActivityResult(request,result,data)))return;if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==10){try{
            if(store.anyBusy()||dev.pocketllama.photo.PhotoStore.busy(this)||VoiceState.busy||importing)throw new IOException(dev.pocketllama.I18n.t("Сначала завершите текущую задачу"));
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
            String title=dev.pocketllama.I18n.t("Импортированная запись");try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))title=c.getString(0).replaceFirst("\\.[^.]+$","");}
            String id=store.create(title.substring(0,Math.min(120,title.length())),true,0,I18n.language());selection=id;ContentValues source=new ContentValues();source.put("source_uri",uri.toString());store.update(id,source);
            startForegroundService(new Intent(this,SpeechService.class).setAction(SpeechService.PROCESS).putExtra("id",id).putExtra("uri",uri.toString()));emit("open",object("id",id));
        }catch(Exception e){error(e);}}
        if(request==11){final String id=exportId,format=exportFormat;files.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException(dev.pocketllama.I18n.t("Не удалось открыть файл"));VoiceExport.write(this,store,id,format,out);emit("toast",object("message",dev.pocketllama.I18n.t("Файл сохранён")));}catch(Exception e){error(e);}});}
        if(request==12||request==13){try{requireIdleModels();}catch(Exception e){error(e);return;}importing=true;boolean projector=request==13;
            files.execute(()->{try{
                File file=ModelImporter.copy(getApplicationContext(),uri,(done,total)->emit("importProgress",object("done",done,"total",total)),projector);
                ServerConfig c=config.load();if(projector)c.projectorName=file.getName();else{c.modelName=file.getName();c.projectorName="";}config.save(c);emit("toast",object("message",projector?dev.pocketllama.I18n.t("Модуль изображений добавлен"):dev.pocketllama.I18n.t("Модель добавлена")));
            }catch(Exception e){error(e);}finally{importing=false;emit("configChanged",new JSONObject());}});
        }
    }
    private static int bounded(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    private static JSONObject object(Object... pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return j;}
    private void reply(String request,JSONObject value){emit("reply",object("request",request,"value",value));}
    private void reject(String request,Exception e){emit("reply",object("request",request,"error",useful(e)));}
    private void error(Exception e){emit("error",object("message",useful(e)));}
    private String useful(Exception e){return dev.pocketllama.I18n.status(e.getMessage())==null?dev.pocketllama.I18n.t("Не удалось выполнить действие"):dev.pocketllama.I18n.status(e.getMessage());}
    private void emit(String type,JSONObject data){if(!ready||destroyed)return;String json=object("type",type,"data",data).toString().replace("\u2028","\\u2028").replace("\u2029","\\u2029");main.post(()->{if(!destroyed&&web!=null)web.evaluateJavascript("window.StudioEvents&&window.StudioEvents("+json+")",null);});}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.StudioBack&&window.StudioBack()",value->{if("false".equals(value))super.onBackPressed();});else super.onBackPressed();}
    private void consumeSharedLink(){if(!ready||!dev.pocketllama.security.GateSession.isOpen())return;Intent intent=getIntent();if(!Intent.ACTION_SEND.equals(intent.getAction()))return;CharSequence value=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);intent.removeExtra(Intent.EXTRA_TEXT);intent.setAction(Intent.ACTION_MAIN);if(value!=null)try{emit("videoLink",object("url",VideoUrl.parse(value.toString())));}catch(Exception e){error(e);}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(Intent.ACTION_SEND.equals(intent.getAction()))consumeSharedLink();else emit("navigate",object("screen",intent.getStringExtra("screen")));}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(!secureInitialized()||!dev.pocketllama.security.GateSession.isOpen())return;UiHost.colors(this,root,web);web.getSettings().setTextZoom(Math.round(100*c.fontScale));}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("selection",selection);state.putString("exportId",exportId);state.putString("exportFormat",exportFormat);}
    @Override protected void onResume(){super.onResume();if(!secureInitialized()||!dev.pocketllama.security.GateSession.isOpen())return;resumed=true;main.post(pulse);if(web!=null){web.onResume();UiHost.syncLanguage(web);}emit("resume",new JSONObject());consumeSharedLink();}
    @Override protected void onPause(){if(!secureInitialized()){super.onPause();return;}resumed=false;emit("visibility",object("visible",false));main.removeCallbacks(pulse);if(playerReady&&player!=null)try{player.pause();}catch(Exception ignored){}if(web!=null)web.onPause();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);releasePlayer();io.shutdown();files.shutdown();if(web!=null){web.removeJavascriptInterface("Studio");web.destroy();web=null;}super.onDestroy();}
}
