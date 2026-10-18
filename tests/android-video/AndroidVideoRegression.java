import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.database.Cursor;
import dev.pocketllama.video.*;
import dev.pocketllama.voice.*;
import dev.pocketllama.security.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Production runtime, downloader, decoder, encrypted PCM, and VoiceStore on Android ART.
 * Synthetic fixtures only; no real app data or private device keys are accessed.
 */
public final class AndroidVideoRegression {
 private static int checks;private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("OK "+label);}
 private static String text(Process p)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=p.getInputStream().read(b))!=-1)out.write(b,0,n);int code=p.waitFor();if(code!=0)throw new IOException("process "+code+": "+new String(read(p.getErrorStream()),"UTF-8"));return out.toString("UTF-8").trim();}
 private static byte[] read(InputStream in)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
 private static Context context(File data,File nativeDir,AssetManager assets){data.mkdirs();return new ContextWrapper(null){@Override public Context getApplicationContext(){return this;}@Override public File getNoBackupFilesDir(){return data;}@Override public AssetManager getAssets(){return assets;}@Override public ApplicationInfo getApplicationInfo(){ApplicationInfo info=new ApplicationInfo();info.nativeLibraryDir=nativeDir.getAbsolutePath();return info;}};}
 public static void main(String[] args)throws Exception{System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out),true,"UTF-8"));try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
 private static void run(String[] args)throws Exception {
  File root=new File(args[0]),nativeDir=new File(root,"native");System.load(new File(nativeDir,"libsqlcipher.so").getAbsolutePath());net.zetetic.database.Logger.setTarget(new net.zetetic.database.NoopTarget());
  AssetManager assets=AssetManager.class.getDeclaredConstructor().newInstance();AssetManager.class.getMethod("addAssetPath",String.class).invoke(assets,new File(root,"assets.apk").getAbsolutePath());
  Context c=context(new File(root,"fresh"),nativeDir,assets);VideoRuntime runtime=VideoRuntime.prepare(c);check(text(runtime.start(Arrays.asList("--version"))).equals("2026.08.19"),"bundled CPython executes pinned yt-dlp");
  check(text(new ProcessBuilder(new File(nativeDir,"libqjs.so").getAbsolutePath(),"-e","console.log(6*7)").start()).equals("42"),"bundled QuickJS executes");
  check(text(VideoRuntime.prepare(c).start(Arrays.asList("--version"))).equals("2026.08.19"),"runtime reuse after reopen");
  File runtimeRoot=new File(c.getNoBackupFilesDir(),"video-runtime-v1");String program="import sys,types\nsys.argv=['probe',sys.argv[1]]\nsource=open('"+new File(runtimeRoot,"launcher.py").getAbsolutePath()+"').read().split('QuickJSJCP._run_js_runtime =')[0]\nexec(source)\nprint(memory_solver(types.SimpleNamespace(runtime_info=types.SimpleNamespace(path='"+new File(nativeDir,"libqjs.so").getAbsolutePath()+"')),'console.log(6*7)'))";
  ProcessBuilder memory=new ProcessBuilder(new File(nativeDir,"libpython.so").getAbsolutePath(),"-B","-c",program,new File(runtimeRoot,"yt-dlp").getAbsolutePath());memory.environment().put("PYTHONHOME",new File(runtimeRoot,"python/usr").getAbsolutePath());memory.environment().put("LD_LIBRARY_PATH",new File(runtimeRoot,"python/usr/lib").getAbsolutePath());check(text(memory.start()).equals("42"),"production QuickJS adapter executes from anonymous memfd");
  String url="http://media.fixture.test/fixture.m4a";VideoDownloader download=new VideoDownloader(runtime);JSONObject info=download.inspect(url,(done,total)->{});check(info.optString("_type","video").equals("video"),"real yt-dlp extracts public media metadata through fixture proxy");
  File media=new File(Vault.root(c),"ba585069-fde2-4e86-ac81-4c8dddb8da6a.media");download.download(url,media,Vault.key(c),0,(done,total)->{});check(media.isFile(),"real native downloader streams to encrypted file");
  try(ChunkedFile encrypted=new ChunkedFile(media,false,Vault.key(c))){byte[] bytes=new byte[(int)encrypted.length()];encrypted.read(0,bytes,0,bytes.length);StringBuilder sha=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))sha.append(String.format(Locale.ROOT,"%02x",b&255));check(sha.toString().equals(args[1]),"decrypted download is byte-identical to fixture");encrypted.verifyAll();checks++;}
  File audio=Vault.audio(c,"ba585069-fde2-4e86-ac81-4c8dddb8da6a");long samples;try(EncryptedMediaSource source=new EncryptedMediaSource(media,Vault.key(c))){samples=AudioImporter.decode(c,source,audio,n->{});}check(samples>=47000&&samples<51000,"Android MediaExtractor/MediaCodec decode encrypted M4A at 16 kHz");
  try(PcmFile pcm=new PcmFile(audio,false,Vault.key(c))){float[] sound=pcm.read(16000,16000);double energy=0;for(float f:sound)energy+=f*f;check(energy>1,"decoded signal contains fixture tone");check(pcm.samples()==samples,"encrypted PCM reopens after decode");}
  for(int old:new int[]{1,2}){
   Context legacy=context(new File(root,"upgrade"+old),nativeDir,assets);String id;
   try(net.zetetic.database.sqlcipher.SQLiteOpenHelper h=old==1?new LegacyVoiceStore1(legacy):new LegacyVoiceStore2(legacy)){
    net.zetetic.database.sqlcipher.SQLiteDatabase db=h.getWritableDatabase();id=UUID.randomUUID().toString();db.execSQL("INSERT INTO meetings(id,title,summary,created,samples,state) VALUES(?,?,?,?,?,?)",new Object[]{id,"Старая запись · тест","Сохранённый конспект",1,32000,"done"});db.execSQL("INSERT INTO turns(mid,idx,start,end,speaker,text) VALUES(?,?,?,?,?,?)",new Object[]{id,1,0,1000,1,"Не потерять эту реплику"});
   }
   try(VoiceStore store=new VoiceStore(legacy)){
    check(store.getWritableDatabase().getVersion()==4,"v"+old+" upgrades to schema 4");JSONObject row=store.get(id);check(row.optString("asr_language").equals("ru"),"legacy transcript keeps Russian ASR default");check(row.getString("title").equals("Старая запись · тест")&&row.getString("summary").equals("Сохранённый конспект"),"v"+old+" keeps original text and summary");check(row.optString("video_url").isEmpty()&&row.optInt("auto_summary")==0,"legacy row has neutral video defaults");
    try(Cursor turns=store.turns(id)){check(turns.moveToFirst()&&turns.getString(4).contains("Не потерять"),"v"+old+" retains transcript");}
    String video=store.create("Новый ролик",false,0);ContentValues meta=new ContentValues();meta.put("video_url",url);meta.put("auto_summary",1);store.update(video,meta);store.startJob(video,"downloading");check(store.anyBusy()&&VoiceStore.busy("downloading"),"download reserves cross-process job slot");
    boolean rejected=false;try{store.startJob(id,"processing");}catch(IOException e){rejected=true;}check(rejected,"ASR cannot overlap another video job");
    store.recover(legacy,false);check(store.get(video).getString("state").equals("interrupted"),"dead downloader becomes resumable");check(store.get(id).getString("title").contains("Старая"),"recovery preserves existing recording");
   }
   try(VoiceStore store=new VoiceStore(legacy)){check(store.get(id).getString("summary").equals("Сохранённый конспект"),"upgraded database reopens with same key");}
  }
  try(VoiceStore store=new VoiceStore(c)){check(store.getWritableDatabase().getVersion()==4,"fresh schema 4 opens");check(store.list().length()==0,"fresh database starts empty");}
  check(!new String(Files.readAllBytes(Vault.database(c).toPath()),java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("SQLite format 3"),"database stays encrypted");
  System.out.println("PASS: "+checks+" Android video and migration checks");
 }
}
