package dev.pocketllama.photo;

import android.app.ActivityManager;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.SecretKey;

/** One encrypted record per photo, in internal no-backup storage. No media provider, URI, API or cloud index. */
public final class PhotoStore {
    private final Context context;private final File root;private final SecretKey key;
    public PhotoStore(Context c)throws Exception {context=c.getApplicationContext();root=new File(context.getNoBackupFilesDir(),"private-photos-v1");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Нет места для фото");synchronized(PhotoStore.class){try(RandomAccessFile f=new RandomAccessFile(new File(root,"store.lock"),"rw");FileLock lock=f.getChannel().lock()){key=PhotoKey.get(c);}}}
    private static void id(String id)throws IOException {if(id==null||!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new IOException("Фото не найдено");}
    private File dir(String id)throws IOException {id(id);return new File(root,id);}
    private JSONObject read(String id)throws Exception {return new JSONObject(new String(EncryptedFiles.read(new File(dir(id),"record.bin"),key,id+"/record",2*1024*1024),StandardCharsets.UTF_8));}
    private void write(String id,JSONObject record)throws Exception {byte[] data=record.toString().getBytes(StandardCharsets.UTF_8);if(data.length>2*1024*1024)throw new IOException("Слишком много текста");EncryptedFiles.write(new File(dir(id),"record.bin"),key,id+"/record",data);}
    private interface Work<T>{T run()throws Exception;}
    private <T>T locked(Work<T> work)throws Exception {synchronized(PhotoStore.class){try(RandomAccessFile f=new RandomAccessFile(new File(root,"store.lock"),"rw");FileLock lock=f.getChannel().lock()){return work.run();}}}
    public JSONObject get(String id)throws Exception {return locked(()->{JSONObject j=read(id);if(j.optString("state").equals("processing")&&!alive(context)&&!new File(root,"active-job").exists()&&System.currentTimeMillis()-j.optLong("updated")>15000){j.put("state","interrupted").put("detail","Распознавание прервано. Фото сохранено; можно повторить.");write(id,j);}return j;});}
    public boolean exists(String id)throws Exception {return locked(()->new File(dir(id),"record.bin").isFile());}
    public void scan(String id,JSONArray corners,String filter)throws Exception {
        double[] q=ScanGeometry.read(corners);if(!Arrays.asList("color","gray","bw").contains(filter))throw new IOException("Неизвестный фильтр");
        change(id,j->{idle(j);j.put("scan",new JSONObject().put("corners",ScanGeometry.json(q)).put("filter",filter));invalidate(j);});
    }
    public void analysis(String id,String state,String detail,double progress,JSONObject analysis)throws Exception {
        change(id,j->{j.put("state",state).put("detail",detail).put("progress",progress).put("analysis",analysis).put("engine","surya");
            JSONArray blocks=analysis.optJSONArray("blocks"),lines=new JSONArray();StringBuilder text=new StringBuilder();
            if(blocks!=null)for(int i=0;i<blocks.length();i++){JSONObject b=blocks.getJSONObject(i);String t=b.optString("text");if(!b.optBoolean("complete")||t.isEmpty())continue;if(text.length()>0)text.append("\n\n");text.append(t);lines.put(new JSONObject().put("text",t).put("score",b.optDouble("confidence",0)).put("box",b.getJSONArray("box")));}
            if(text.length()>250000)throw new IOException("Слишком много текста на странице");j.put("text",text.toString()).put("lines",lines).put("edited",false);
        });
        if(!state.equals("processing"))releaseJob(id);
    }
    /** Geometry enrichment must never replace manually edited or already recognized text. */
    public void geometry(String id,String state,String detail,double progress,JSONObject analysis)throws Exception {
        change(id,j->{if(!j.optString("engine").equals("surya"))throw new IOException("Сначала распознайте страницу в Surya");j.put("state",state).put("detail",detail).put("progress",progress).put("analysis",analysis);});
        if(!state.equals("processing"))releaseJob(id);
    }
    public void beginInService(String id)throws Exception {locked(()->{JSONObject j=read(id);j.put("state","processing").put("detail","Подготовка страницы").put("updated",System.currentTimeMillis());write(id,j);try(FileOutputStream out=new FileOutputStream(new File(root,"active-job"))){out.write(id.getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}return null;});}
    public void reserveJob(String id)throws Exception {locked(()->{read(id);if(anyBusyUnlocked())throw new IOException("Другой документ уже обрабатывается");try(FileOutputStream out=new FileOutputStream(new File(root,"active-job"))){out.write(id.getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}return null;});}
    public void finishJob()throws Exception {locked(()->{File f=new File(root,"active-job");if(f.exists()&&!f.delete())throw new IOException("Не удалось освободить задание OCR");return null;});}
    private void releaseJob(String id)throws Exception {locked(()->{File active=new File(root,"active-job");if(active.isFile()&&new String(java.nio.file.Files.readAllBytes(active.toPath()),StandardCharsets.US_ASCII).equals(id))active.delete();return null;});}
    private static void invalidate(JSONObject j)throws JSONException {j.remove("analysis");j.remove("engine");j.put("state","ready").put("text","").put("lines",new JSONArray()).put("edited",false).put("detail","").put("progress",0);}
    public String create(byte[] jpeg,byte[] thumb,int width,int height)throws Exception {return locked(()->{
        if(root.getUsableSpace()<jpeg.length+64*1024*1024L)throw new IOException("Недостаточно места для фото");String id=UUID.randomUUID().toString();File dir=dir(id);if(!dir.mkdir())throw new IOException("Не удалось сохранить фото");
        try {
            EncryptedFiles.write(new File(dir,"photo.bin"),key,id+"/photo",jpeg);EncryptedFiles.write(new File(dir,"thumb.bin"),key,id+"/thumb",thumb);
            long now=System.currentTimeMillis();JSONObject j=new JSONObject().put("id",id).put("title","Фото · "+new java.text.SimpleDateFormat("d MMM, HH:mm",new Locale("ru")).format(new Date(now))).put("created",now).put("updated",now).put("width",width).put("height",height).put("rotation",0).put("state","ready").put("progress",0).put("text","").put("lines",new JSONArray()).put("edited",false).put("bytes",jpeg.length);
            write(id,j);return id;
        }catch(Exception e){for(File f:Objects.requireNonNull(dir.listFiles()))f.delete();dir.delete();throw e;}
    });}
    public byte[] image(String id,boolean thumbnail)throws Exception {return locked(()->{read(id);String kind=thumbnail?"thumb":"photo";return EncryptedFiles.read(new File(dir(id),kind+".bin"),key,id+"/"+kind,thumbnail?1024*1024:24*1024*1024);});}
    public JSONObject list(String query,int limit)throws Exception {return locked(()->{
        String needle=query.trim().toLowerCase(Locale.ROOT);ArrayList<JSONObject> rows=new ArrayList<>();int unreadable=0;File[] dirs=root.listFiles(File::isDirectory);
        if(dirs!=null)for(File d:dirs){if(!d.getName().matches("[a-f0-9-]{36}"))continue;try{JSONObject j=read(d.getName());if(!needle.isEmpty()&&!(j.optString("title")+"\n"+j.optString("text")).toLowerCase(Locale.ROOT).contains(needle))continue;j.remove("analysis");j.remove("lines");j.remove("text");rows.add(j);}catch(Exception e){unreadable++;}}
        rows.sort((a,b)->Long.compare(b.optLong("created"),a.optLong("created")));int total=rows.size();JSONArray result=new JSONArray();for(int i=0;i<Math.min(total,Math.max(1,Math.min(1000,limit)));i++)result.put(rows.get(i));
        return new JSONObject().put("items",result).put("total",total).put("more",total>result.length()).put("unreadable",unreadable);
    });}
    public List<String> idsForLocalIndex()throws Exception {return locked(()->{List<String> ids=new ArrayList<>();File[] dirs=root.listFiles(File::isDirectory);if(dirs!=null)for(File d:dirs)if(d.getName().matches("[a-f0-9-]{36}"))ids.add(d.getName());return ids;});}
    public void rename(String id,String title)throws Exception {change(id,j->{String t=title.trim();if(t.isEmpty()||t.length()>160)throw new IOException("Название: от 1 до 160 символов");j.put("title",t);});}
    public void edit(String id,String text)throws Exception {change(id,j->{idle(j);if(text.length()>250000)throw new IOException("Слишком большой текст");j.put("text",text).put("edited",true);});}
    public void resetRecognition(String id)throws Exception {change(id,j->{idle(j);invalidate(j);});}
    public void rotate(String id)throws Exception {change(id,j->{idle(j);j.put("rotation",(j.optInt("rotation")+1)%4);invalidate(j);});}
    private interface Change{void apply(JSONObject j)throws Exception;}
    private void change(String id,Change update)throws Exception {locked(()->{JSONObject j=read(id);update.apply(j);j.put("updated",System.currentTimeMillis());write(id,j);return null;});}
    private static void idle(JSONObject j)throws IOException {if(j.optString("state").equals("processing"))throw new IOException("Сначала остановите распознавание");}
    public void claim(String id)throws Exception {locked(()->{if(anyBusyUnlocked())throw new IOException("Другое фото уже распознаётся");JSONObject j=read(id);j.put("state","processing").put("detail","Подготовка OCR").put("progress",0).put("updated",System.currentTimeMillis());write(id,j);try(FileOutputStream out=new FileOutputStream(new File(root,"active-job"))){out.write(id.getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}return null;});}
    public void progress(String id,String state,String detail,double progress,List<OcrEngine.Line> lines,boolean replace)throws Exception {change(id,j->{j.put("state",state).put("detail",detail).put("progress",progress);if(replace){JSONArray array=new JSONArray();StringBuilder text=new StringBuilder();for(OcrEngine.Line line:lines){JSONArray box=new JSONArray();for(double coordinate:line.box)box.put(coordinate);array.put(new JSONObject().put("text",line.text).put("score",line.score).put("box",box));if(text.length()>0)text.append('\n');text.append(line.text);}j.remove("analysis");j.put("text",text.toString()).put("lines",array).put("edited",false).put("engine","paddle");}});if(!state.equals("processing"))locked(()->{File active=new File(root,"active-job");if(active.isFile()&&new String(java.nio.file.Files.readAllBytes(active.toPath()),StandardCharsets.US_ASCII).equals(id))active.delete();return null;});}
    public boolean anyBusy()throws Exception {return locked(this::anyBusyUnlocked);}
    private boolean anyBusyUnlocked(){return new File(root,"active-job").isFile()||alive(context);}
    public static boolean busy(Context c){
        if(alive(c))return true;File active=new File(new File(c.getNoBackupFilesDir(),"private-photos-v1"),"active-job");
        if(!active.isFile())return false;
        if(System.currentTimeMillis()-active.lastModified()>15000)try{new PhotoStore(c).recover();}catch(Exception ignored){}
        return active.isFile()||alive(c);
    }
    public static boolean alive(Context c){List<ActivityManager.RunningAppProcessInfo> ps=c.getSystemService(ActivityManager.class).getRunningAppProcesses();if(ps!=null)for(ActivityManager.RunningAppProcessInfo p:ps)if(p.processName.equals(c.getPackageName()+":ocr"))return true;return false;}
    public void recover()throws Exception {if(alive(context))return;File active=new File(root,"active-job");if(!active.isFile()||System.currentTimeMillis()-active.lastModified()<15000)return;
        locked(()->{if(alive(context)||!active.isFile())return null;String id=new String(java.nio.file.Files.readAllBytes(active.toPath()),StandardCharsets.US_ASCII);try{JSONObject j=read(id);if(j.optString("state").equals("processing")){j.put("state","interrupted").put("detail","Распознавание прервано. Фото сохранено; можно повторить.");write(id,j);}}finally{active.delete();}return null;});
    }
    public void delete(String id)throws Exception {locked(()->{JSONObject j=read(id);idle(j);File dir=dir(id);File record=new File(dir,"record.bin");
        // Remove image bytes first. Keep the catalog entry on failure so deletion can be retried.
        for(String name:new String[]{"photo.bin","thumb.bin","photo.bin.tmp","thumb.bin.tmp","record.bin.tmp"}){File f=new File(dir,name);if(f.exists()&&!f.delete())throw new IOException("Не удалось удалить фото. Повторите удаление");}
        if(!record.delete()||!dir.delete())throw new IOException("Не удалось удалить запись фото");return null;});}
}
