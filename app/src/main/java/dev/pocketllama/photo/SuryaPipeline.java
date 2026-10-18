package dev.pocketllama.photo;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** One page call by default, resumable legacy/block fallback, optional precise geometry. */
public final class SuryaPipeline implements AutoCloseable {
    public interface Progress {
        void update(String detail,double fraction)throws Exception;
        default void phase(String phase,String detail,double fraction,long startedAt)throws Exception {update(detail,fraction);}
    }
    private final Context c;private final PhotoStore photos;private final SuryaRuntime.Check check;
    private volatile SuryaRuntime runtime;
    private Progress progressSink;private String phaseKey="";private long phaseStarted;private int requestSerial;private double stageFraction;
    public SuryaPipeline(Context c,PhotoStore photos,SuryaRuntime.Check check){this.c=c;this.photos=photos;this.check=check;}
    public void cancel(){SuryaRuntime r=runtime;if(r!=null)r.cancel();}
    @Override public void close(){SuryaRuntime r=runtime;runtime=null;if(r!=null)r.close();}
    private void begin(Progress progress)throws Exception {progressSink=progress;phaseKey="";requestSerial=0;stageFraction=.01;stage("page","Готовим страницу");}
    private void stage(String key,String message)throws Exception {if(!key.equals(phaseKey)){phaseKey=key;phaseStarted=System.currentTimeMillis();}progressSink.phase(key,message,stageFraction,phaseStarted);}
    private void load(File model,JSONObject result,Progress progress)throws Exception {
        if(runtime!=null)return;
        stage("load","Загружаем Surya OCR 2");runtime=new SuryaRuntime(c,model,check);
        performance(result).put("load_ms",runtime.loadMs()).put("runtime_reused",false);
        stage("ready","Surya готова · подготавливаем распознавание");
    }
    public void page(String id,File model,Progress progress)throws Exception {
        begin(progress);
        JSONObject page=photos.get(id);String signature=signature(page);JSONObject analysis=page.optJSONObject("analysis");
        if(analysis==null||!signature.equals(analysis.optString("signature")))analysis=new JSONObject().put("version",SuryaProtocol.VERSION).put("signature",signature).put("modelSha256",SuryaAssets.MODEL_SHA);
        final JSONObject result=analysis;Bitmap image=ScanImages.render(photos.image(id,false),page,4096);boolean complete=false;
        long started=SystemClock.elapsedRealtime(),previous=performance(result).optLong("recognition_ms");performance(result).put("runtime_reused",runtime!=null);
        try {
            result.put("width",image.getWidth()).put("height",image.getHeight()).put("ocrComplete",false);
            JSONArray blocks=result.optJSONArray("blocks");
            // Preserve 0.7.0 checkpoints. New pages use a single full-page request.
            if(result.optBoolean("layoutPending")||result.optBoolean("pagePending")) {
                load(model,result,progress);blocks=fallback(id,image,result,progress,result.optString("fallbackReason","resume"));
            }else if(blocks==null) {
                load(model,result,progress);result.put("pagePending",true).put("strategy","page");
                stageFraction=.1;stage("read","Читаем страницу целиком");save(id,result,"Читаем страницу целиком",.1);
                StringBuilder streamed=new StringBuilder();final long[] lastUpdate={0};final int[] saved={blocks==null?0:blocks.length()};
                try {
                    String raw=ask(result,image,SuryaProtocol.PAGE,false,false,8192,SuryaImageSize.Task.PAGE,chunk->{
                        streamed.append(chunk);long now=SystemClock.elapsedRealtime();
                        if(now-lastUpdate[0]<2000)return;lastUpdate[0]=now;
                        saved[0]=checkpoint(id,result,streamed.toString(),image,saved[0],true);
                        stageFraction=.2;stage("request:"+requestSerial+":text","Читаем страницу · "+streamed.length()+" символов · готово областей: "+saved[0]);
                    });
                    blocks=SuryaPageHtml.parse(raw,image.getWidth(),image.getHeight(),true);
                    result.put("blocks",blocks).put("pagePending",false);
                }catch(SuryaRuntime.Incomplete e) {
                    checkpoint(id,result,streamed.toString(),image,saved[0],false);
                    if(!Arrays.asList("length","repetition","deadline","output_limit").contains(e.reason))throw e;
                    blocks=fallback(id,image,result,progress,e.reason);
                }catch(SuryaPageHtml.RepetitionException e) {
                    result.put("blocks",e.completed);blocks=fallback(id,image,result,progress,"repetition");
                }catch(SuryaPageHtml.FormatException e) {
                    checkpoint(id,result,streamed.toString(),image,saved[0],false);
                    blocks=fallback(id,image,result,progress,"page_format");
                }catch(Exception e) {
                    checkpoint(id,result,streamed.toString(),image,saved[0],false);throw e;
                }
            }
            if(blocks==null)throw new IOException("Surya не определила области страницы");
            int errors=0;String firstError="";
            for(int i=0;i<blocks.length();i++) {
                check.run();JSONObject b=blocks.getJSONObject(i);if(b.optBoolean("complete"))continue;
                String label=b.getString("label");double fraction=.2+.75*i/Math.max(1,blocks.length());stageFraction=fraction;String status="Область "+(i+1)+" / "+blocks.length()+" · "+label;progress.update(status,fraction);
                if(SuryaProtocol.visual(label)){b.put("complete",true).put("text","");save(id,result,status,fraction);continue;}
                load(model,result,progress);Bitmap crop=crop(image,b.getJSONArray("bbox"));
                try {
                    String raw=generate(result,crop,SuryaProtocol.BLOCK,false,false,SuryaProtocol.tokenBudget(b.optInt("count")));
                    JSONObject parsed=SafeOcrHtml.parse(raw);b.put("html",parsed.getString("html")).put("text",parsed.getString("text")).put("tables",parsed.getJSONArray("tables")).put("confidence",-1).put("complete",true);b.remove("error");save(id,result,status,fraction);
                }catch(SuryaRuntime.Incomplete|JSONException|IllegalArgumentException e) {
                    b.put("complete",false).put("error",e.getMessage()==null?"Повторите область":e.getMessage());errors++;if(firstError.isEmpty())firstError=b.optString("error");save(id,result,"Область сохранена для повтора",fraction);
                }finally{if(crop!=image)crop.recycle();}
            }
            if(errors>0)throw new IOException("Не завершено областей: "+errors+". "+firstError+". Готовые области сохранены");
            performance(result).put("recognition_ms",previous+SystemClock.elapsedRealtime()-started);result.put("ocrComplete",true).put("pagePending",false);
            photos.analysis(id,"done","Surya: текст, разметка и таблицы готовы. Сетки таблиц можно уточнить отдельно.",1,result);complete=true;progress.update("Страница готова",1);
        }finally {
            image.recycle();
            if(!complete){performance(result).put("recognition_ms",previous+SystemClock.elapsedRealtime()-started);try{photos.analysis(id,"interrupted",result.optBoolean("pagePending")||result.optBoolean("layoutPending")?"Страница прервана. Готовые области сохранены; продолжение обработает оставшиеся области.":"Обработка прервана. Готовые области сохранены; можно продолжить.",page.optDouble("progress"),result);}catch(Exception ignored){}}
        }
    }
    private int checkpoint(String id,JSONObject result,String raw,Bitmap image,int saved,boolean rejectRepeat)throws Exception {
        JSONArray ready;boolean repeated=false;
        try{ready=SuryaPageHtml.parse(raw,image.getWidth(),image.getHeight(),false);}
        catch(SuryaPageHtml.RepetitionException e){ready=e.completed;repeated=true;}
        catch(SuryaPageHtml.FormatException e){return saved;}
        if(ready.length()>saved||repeated){result.put("blocks",ready);save(id,result,"Сохранено областей: "+ready.length(),.2);saved=ready.length();}
        if(repeated&&rejectRepeat)throw new SuryaRuntime.Incomplete("repetition");return saved;
    }
    private JSONArray fallback(String id,Bitmap image,JSONObject result,Progress progress,String reason)throws Exception {
        stageFraction=.2;
        String message=reason.equals("repetition")?"Повтор остановлен · распознаём по областям":"Продолжаем по областям · уточняем разметку";
        stage("fallback",message);JSONArray completed=result.optJSONArray("blocks");
        // Commit the changed strategy before layout: an interrupted fallback must not rerun PAGE.
        result.put("pagePending",false).put("layoutPending",true).put("strategy","blocks").put("fallbackReason",reason);save(id,result,message,.2);
        String raw=generate(result,image,SuryaProtocol.LAYOUT,true,false,3072);JSONArray blocks=SuryaProtocol.parse(raw,false,image.getWidth(),image.getHeight());
        if(blocks.length()==0)throw new IOException("Разметка сложной страницы не определилась. Проверьте ориентацию и рамку");
        if(completed!=null)SuryaPageHtml.reuse(blocks,completed);
        result.put("blocks",blocks).put("layoutPending",false);
        save(id,result,"Области готовы · распознаём оставшиеся",.22);return blocks;
    }
    public void details(String id,File model,Progress progress)throws Exception {
        begin(progress);
        JSONObject page=photos.get(id),result=page.optJSONObject("analysis");
        if(result==null||!signature(page).equals(result.optString("signature"))||result.optBoolean("pagePending")||result.optBoolean("layoutPending"))throw new IOException("Сначала завершите распознавание страницы в Surya");
        JSONArray blocks=result.optJSONArray("blocks");if(blocks==null)throw new IOException("Сначала распознайте страницу");
        for(int i=0;i<blocks.length();i++)if(!blocks.getJSONObject(i).optBoolean("complete"))throw new IOException("Сначала завершите распознавание областей");
        Bitmap image=ScanImages.render(photos.image(id,false),page,4096);boolean complete=false;long started=SystemClock.elapsedRealtime(),previous=performance(result).optLong("geometry_ms");performance(result).put("runtime_reused",runtime!=null);
        try {
            for(int i=0;i<blocks.length();i++) {
                check.run();JSONObject block=blocks.getJSONObject(i);if(!block.optString("label").equals("Table")||block.has("tableStructure"))continue;
                stageFraction=.05+.9*i/Math.max(1,blocks.length());load(model,result,progress);progress.update("Уточняем сетку таблицы · область "+(i+1),stageFraction);Bitmap crop=crop(image,block.getJSONArray("bbox"));
                try {
                    String raw=generate(result,crop,SuryaProtocol.TABLE,true,true,3072);JSONArray structure=SuryaProtocol.parse(raw,true,crop.getWidth(),crop.getHeight());
                    boolean row=false,col=false;for(int j=0;j<structure.length();j++){String label=structure.getJSONObject(j).getString("label");row|=label.equals("Row");col|=label.equals("Col");}
                    if(!row||!col)throw new IOException("Surya не определила строки и столбцы таблицы. Текст сохранён; можно повторить уточнение");
                    block.put("tableStructure",structure);geometrySave(id,result,"Сетка таблицы сохранена",.05+.9*(i+1)/Math.max(1,blocks.length()));
                }finally{if(crop!=image)crop.recycle();}
            }
            performance(result).put("geometry_ms",previous+SystemClock.elapsedRealtime()-started);result.put("geometryComplete",true);
            photos.geometry(id,"done","Текст, разметка и сетки таблиц готовы",1,result);complete=true;progress.update("Сетки таблиц готовы",1);
        }finally {
            image.recycle();if(!complete){performance(result).put("geometry_ms",previous+SystemClock.elapsedRealtime()-started);try{photos.geometry(id,"interrupted","Уточнение прервано. Текст и готовые сетки таблиц сохранены; можно продолжить.",page.optDouble("progress"),result);}catch(Exception ignored){}}
        }
    }
    private static Bitmap crop(Bitmap image,JSONArray box)throws Exception {
        int x=Math.max(0,(int)Math.floor(box.getDouble(0))),y=Math.max(0,(int)Math.floor(box.getDouble(1))),right=Math.min(image.getWidth(),(int)Math.ceil(box.getDouble(2))),bottom=Math.min(image.getHeight(),(int)Math.ceil(box.getDouble(3)));
        if(right<=x||bottom<=y)throw new IOException("Некорректная рамка области");return Bitmap.createBitmap(image,x,y,right-x,bottom-y);
    }
    private void save(String id,JSONObject result,String status,double progress)throws Exception {check.run();enforceSize(result);photos.analysis(id,"processing",status,progress,result);}
    private void geometrySave(String id,JSONObject result,String status,double progress)throws Exception {check.run();enforceSize(result);photos.geometry(id,"processing",status,progress,result);}
    private static JSONObject performance(JSONObject result)throws JSONException {JSONObject p=result.optJSONObject("performance");if(p==null){p=new JSONObject().put("requests",new JSONArray());result.put("performance",p);}return p;}
    private String ask(JSONObject result,Bitmap image,String prompt,boolean structured,boolean table,int budget,SuryaImageSize.Task task,SuryaRuntime.Output output)throws Exception {
        final String request="request:"+(++requestSerial)+":";stage(request+"prepare","Подготавливаем изображение");
        try{return runtime.request(image,prompt,structured,table,budget,task,output,(phase,message)->stage(request+phase,message));}finally{JSONObject p=performance(result);JSONArray requests=p.optJSONArray("requests");if(requests==null){requests=new JSONArray();p.put("requests",requests);}if(requests.length()<512)requests.put(new JSONObject(runtime.metrics().toString()));}
    }
    private String generate(JSONObject result,Bitmap image,String prompt,boolean structured,boolean table,int budget)throws Exception {
        SuryaImageSize.Task task=table?SuryaImageSize.Task.TABLE:structured?SuryaImageSize.Task.LAYOUT:SuryaImageSize.Task.BLOCK;
        try{return ask(result,image,prompt,structured,table,budget,task,null);}catch(SuryaRuntime.Incomplete e){check.run();if(!e.reason.equals("length")||budget>=8192)throw e;stage("retry:"+requestSerial,"Длинная область · одна попытка с большим лимитом");return ask(result,image,prompt,structured,table,8192,task,null);}
    }
    private static void enforceSize(JSONObject analysis)throws IOException {if(analysis.toString().getBytes(StandardCharsets.UTF_8).length>1000000)throw new IOException("Слишком много разметки на странице. Разделите её на части");}
    // Preserve the 0.7.0 signature so completed regions survive the performance update.
    private static String signature(JSONObject page)throws Exception {String value=SuryaProtocol.VERSION+SuryaAssets.MODEL_SHA+page.optInt("rotation")+String.valueOf(page.optJSONObject("scan"))+page.optInt("width")+":"+page.optInt("height");byte[] h=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:h)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
