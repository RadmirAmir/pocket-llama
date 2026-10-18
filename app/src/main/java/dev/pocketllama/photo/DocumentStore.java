package dev.pocketllama.photo;

import android.content.Context;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.SecretKey;
import org.json.*;

/** Encrypted document index. Page images and analysis stay in the existing PhotoStore. */
public final class DocumentStore {
    public static final int MAX_PAGES=50;
    private final Context context;private final File root;private final SecretKey key;private final String defaultEngine;
    public DocumentStore(Context c)throws Exception {this(c,"surya");}
    public DocumentStore(Context c,String engine)throws Exception {if(!engine.equals("surya")&&!engine.equals("paddle"))throw new IOException("Неизвестный движок");defaultEngine=engine;context=c.getApplicationContext();root=new File(context.getNoBackupFilesDir(),"private-documents-v1");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Нет места для документов");key=PhotoKey.get(c);}
    private static void valid(String id)throws IOException {if(id==null||!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new IOException("Документ не найден");}
    private File file(String id)throws IOException {valid(id);return new File(root,id+".bin");}
    private JSONObject read(String id)throws Exception {return new JSONObject(new String(EncryptedFiles.read(file(id),key,"document/"+id,256*1024),StandardCharsets.UTF_8));}
    private void write(JSONObject value)throws Exception {String id=value.getString("id");EncryptedFiles.write(file(id),key,"document/"+id,value.toString().getBytes(StandardCharsets.UTF_8));}
    private interface Work<T>{T run()throws Exception;}
    private <T>T locked(Work<T> work)throws Exception {synchronized(DocumentStore.class){try(RandomAccessFile f=new RandomAccessFile(new File(root,"index.lock"),"rw");FileLock lock=f.getChannel().lock()){return work.run();}}}
    public String create()throws Exception {return locked(()->{String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();write(new JSONObject().put("id",id).put("title","Документ · "+new java.text.SimpleDateFormat("d MMM, HH:mm",new Locale("ru")).format(new Date(now))).put("created",now).put("updated",now).put("pages",new JSONArray()).put("state","ready").put("engine",defaultEngine).put("detail","").put("progress",0));return id;});}
    public JSONObject get(String id)throws Exception {return locked(()->read(id));}
    public String resolve(String id)throws Exception {
        if(id==null||id.isEmpty())return "";valid(id);
        String existing=locked(()->{if(file(id).isFile())return id;for(File f:files())try{JSONObject j=read(f.getName().substring(0,36));JSONArray a=j.getJSONArray("pages");for(int i=0;i<a.length();i++)if(a.getString(i).equals(id))return j.getString("id");}catch(Exception unreadable){}return "";});
        if(!existing.isEmpty())return existing;
        JSONObject photo=new PhotoStore(context).get(id);
        return locked(()->{if(file(id).isFile())return id;write(new JSONObject().put("id",id).put("title",photo.optString("title","Документ")).put("created",photo.optLong("created")).put("updated",System.currentTimeMillis()).put("pages",new JSONArray().put(id)).put("state","ready").put("engine",defaultEngine).put("detail","").put("progress",0));return id;});
    }
    private File[] files(){File[] f=root.listFiles(p->p.isFile()&&p.getName().matches("[a-f0-9-]{36}\\.bin"));return f==null?new File[0]:f;}
    public JSONObject list(String query,int limit)throws Exception {
        final int[] unreadable={0};ArrayList<JSONObject> documents=locked(()->{ArrayList<JSONObject> out=new ArrayList<>();for(File f:files())try{out.add(read(f.getName().substring(0,36)));}catch(Exception e){unreadable[0]++;}return out;});
        Set<String> used=new HashSet<>();for(JSONObject d:documents){JSONArray a=d.getJSONArray("pages");for(int i=0;i<a.length();i++)used.add(a.getString(i));}
        PhotoStore photos=new PhotoStore(context);JSONObject legacy=photos.list("",1000);unreadable[0]+=legacy.optInt("unreadable");JSONArray old=legacy.getJSONArray("items");for(int i=0;i<old.length();i++){JSONObject p=old.getJSONObject(i);if(used.contains(p.getString("id")))continue;documents.add(new JSONObject().put("id",p.getString("id")).put("title",p.optString("title")).put("created",p.optLong("created")).put("updated",p.optLong("updated")).put("pages",new JSONArray().put(p.getString("id"))).put("state",p.optString("state")).put("legacy",true));}
        String needle=query.trim().toLowerCase(Locale.ROOT);JSONArray result=new JSONArray();ArrayList<JSONObject> matched=new ArrayList<>();
        for(JSONObject d:documents){JSONArray pages=d.getJSONArray("pages");boolean found=needle.isEmpty()||d.optString("title").toLowerCase(Locale.ROOT).contains(needle);String cover="",coverVisual="";int done=0;
            for(int i=0;i<pages.length();i++){if(!photos.exists(pages.getString(i)))continue;JSONObject p;try{p=photos.get(pages.getString(i));}catch(Exception e){unreadable[0]++;continue;}if(cover.isEmpty()){cover=p.getString("id");coverVisual=p.optInt("rotation")+":"+String.valueOf(p.optJSONObject("scan"));}if(p.optString("state").equals("done"))done++;if(!found&&p.optString("text").toLowerCase(Locale.ROOT).contains(needle))found=true;}
            if(!found)continue;JSONObject item=new JSONObject(d.toString());item.remove("pages");item.put("pageCount",pages.length()).put("doneCount",done).put("cover",cover).put("coverVisual",coverVisual);matched.add(item);
        }
        matched.sort((a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));for(int i=0;i<Math.min(matched.size(),Math.max(1,Math.min(1000,limit)));i++)result.put(matched.get(i));
        return new JSONObject().put("items",result).put("total",matched.size()).put("more",result.length()<matched.size()).put("unreadable",unreadable[0]);
    }
    public JSONObject detail(String id)throws Exception {JSONObject d=get(resolve(id));PhotoStore photos=new PhotoStore(context);JSONArray rows=new JSONArray(),ids=d.getJSONArray("pages");
        for(int i=0;i<ids.length();i++){if(!photos.exists(ids.getString(i))){rows.put(new JSONObject().put("id",ids.getString(i)).put("missing",true).put("pageNumber",i+1));continue;}JSONObject p=photos.get(ids.getString(i));p.remove("analysis");p.remove("lines");p.remove("text");p.put("pageNumber",i+1);rows.put(p);}
        return new JSONObject().put("document",d).put("pages",rows);
    }
    private interface Change{void apply(JSONObject j)throws Exception;}
    private void change(String id,boolean idle,Change edit)throws Exception {locked(()->{JSONObject j=read(id);if(idle&&j.optString("state").equals("processing"))throw new IOException("Сначала остановите распознавание документа");edit.apply(j);j.put("updated",System.currentTimeMillis());write(j);return null;});}
    public void add(String id,String page)throws Exception {valid(page);change(id,true,j->{JSONArray a=j.getJSONArray("pages");if(a.length()>=MAX_PAGES)throw new IOException("В документе может быть до 50 страниц");for(int i=0;i<a.length();i++)if(a.getString(i).equals(page))throw new IOException("Эта страница уже добавлена");a.put(page);j.put("state","ready").put("detail","");});}
    public void rename(String id,String title)throws Exception {change(id,false,j->{String t=title.trim();if(t.isEmpty()||t.length()>160)throw new IOException("Название: от 1 до 160 символов");j.put("title",t);});}
    public void engine(String id,String engine)throws Exception {if(!engine.equals("surya")&&!engine.equals("paddle"))throw new IOException("Неизвестный движок");change(id,true,j->{j.put("engine",engine).put("state","ready");});}
    public void reorder(String id,JSONArray order)throws Exception {change(id,true,j->{JSONArray old=j.getJSONArray("pages");if(old.length()!=order.length())throw new IOException("Состав страниц изменился. Обновите документ");Set<String> a=new HashSet<>(),b=new HashSet<>();for(int i=0;i<old.length();i++){a.add(old.getString(i));b.add(order.getString(i));}if(b.size()!=order.length()||!a.equals(b))throw new IOException("Некорректный порядок страниц");j.put("pages",order);});}
    public void removePage(String id,String page)throws Exception {JSONObject d=get(id);if(d.optString("state").equals("processing"))throw new IOException("Сначала остановите распознавание");JSONArray old=d.getJSONArray("pages");boolean found=false;for(int i=0;i<old.length();i++)found|=old.getString(i).equals(page);if(!found)throw new IOException("Страница не относится к документу");PhotoStore ps=new PhotoStore(context);if(ps.exists(page))ps.delete(page);change(id,true,j->{JSONArray a=j.getJSONArray("pages"),b=new JSONArray();for(int i=0;i<a.length();i++)if(!a.getString(i).equals(page))b.put(a.getString(i));j.put("pages",b).put("state","ready");});}
    public void delete(String id)throws Exception {JSONObject d=get(id);if(d.optString("state").equals("processing"))throw new IOException("Сначала остановите распознавание");PhotoStore photos=new PhotoStore(context);JSONArray a=d.getJSONArray("pages");for(int i=0;i<a.length();i++)if(photos.exists(a.getString(i)))photos.delete(a.getString(i));locked(()->{if(!file(id).delete())throw new IOException("Не удалось удалить документ");return null;});}
    public void dirty(String id)throws Exception {change(id,true,j->j.put("state","ready").put("detail","Страница изменена. Можно обновить распознавание"));}
    public void claim(String id)throws Exception {claim(id,"ocr","");}
    public void claim(String id,String task,String page)throws Exception {change(id,true,j->{JSONArray pages=j.getJSONArray("pages");if(pages.length()==0)throw new IOException("Добавьте страницы документа");if(!task.equals("ocr")&&!task.equals("details"))throw new IOException("Неизвестное задание OCR");if(task.equals("details")){boolean found=false;for(int i=0;i<pages.length();i++)found|=pages.getString(i).equals(page);if(!found)throw new IOException("Страница не относится к документу");}j.put("state","processing").put("task",task).put("taskPage",task.equals("details")?page:"").put("detail",task.equals("details")?"Подготовка уточнения рамок":"Подготовка распознавания").put("progress",0);});}
    public void progress(String id,String state,String detail,double progress)throws Exception {progress(id,state,detail,progress,"",0);}
    public void progress(String id,String state,String detail,double progress,String phase,long startedAt)throws Exception {
        change(id,false,j->{j.put("state",state).put("detail",detail).put("progress",Math.max(0,Math.min(1,progress)));if(state.equals("processing")&&!phase.isEmpty()&&startedAt>0)j.put("phase",phase).put("phaseStartedAt",startedAt);else{j.remove("phase");j.remove("phaseStartedAt");}});
    }
    public void recover()throws Exception {if(PhotoStore.alive(context))return;locked(()->{for(File f:files()){JSONObject j;try{j=read(f.getName().substring(0,36));}catch(Exception unreadable){continue;}if(j.optString("state").equals("processing")&&System.currentTimeMillis()-j.optLong("updated")>15000){j.put("state","interrupted").put("detail","Обработка прервалась. Готовые страницы сохранены; можно продолжить.");write(j);}}return null;});}
}
