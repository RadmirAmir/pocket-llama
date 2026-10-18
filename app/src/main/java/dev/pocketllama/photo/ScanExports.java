package dev.pocketllama.photo;
import android.content.Context;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
public final class ScanExports {
    private ScanExports(){}
    public static void write(Context c,String id,String format,OutputStream out)throws Exception {
        DocumentStore ds=new DocumentStore(c);JSONObject doc=ds.get(id);JSONArray pages=doc.getJSONArray("pages");PhotoStore photos=new PhotoStore(c);if(pages.length()==0)throw new IOException("Добавьте страницы документа");
        if(format.equals("pdf")){byte[] font;try(InputStream in=c.getAssets().open("fonts/Manrope.ttf")){font=dev.pocketllama.core.TextAttachment.readLimited(in,4*1024*1024);}new ScanPdf(out,font).write(new ScanPdf.Source(){public int count(){return pages.length();}public ScanPdf.Page page(int i)throws Exception{JSONObject meta=photos.get(pages.getString(i));Bitmap b=ScanImages.render(photos.image(pages.getString(i),false),meta,2400);try{ScanPdf.Page p=new ScanPdf.Page();p.jpeg=ScanImages.jpeg(b,92);p.width=b.getWidth();p.height=b.getHeight();int[] dims=ScanImages.dimensions(meta);p.coordinateWidth=dims[0];p.coordinateHeight=dims[1];p.lines=meta.optJSONArray("lines");if(meta.optBoolean("edited"))p.lines=new JSONArray().put(new JSONObject().put("text",meta.optString("text")).put("box",new JSONArray().put(0).put(0).put(dims[0]).put(0).put(dims[0]).put(dims[1]).put(0).put(dims[1])));return p;}finally{b.recycle();}}});return;}
        if(format.equals("xlsx")){List<JSONObject> tables=new ArrayList<>();for(int i=0;i<pages.length();i++){JSONObject meta=photos.get(pages.getString(i)),analysis=meta.optJSONObject("analysis");if(analysis==null)continue;JSONArray blocks=analysis.optJSONArray("blocks");if(blocks==null)continue;for(int j=0;j<blocks.length();j++){JSONArray found=blocks.getJSONObject(j).optJSONArray("tables");if(found!=null)for(int k=0;k<found.length();k++)tables.add(found.getJSONObject(k));}}ScanXlsx.write(out,tables);return;}
        if(format.equals("json")){Writer writer=new OutputStreamWriter(out,StandardCharsets.UTF_8);writer.write("{\"format\":\"pocket-scan-v1\",\"document\":");writer.write(doc.toString());writer.write(",\"pages\":[");for(int i=0;i<pages.length();i++){if(i>0)writer.write(',');writer.write(photos.get(pages.getString(i)).toString());}writer.write("]}");writer.flush();return;}
        if(!format.equals("txt")&&!format.equals("md"))throw new IOException("Неизвестный формат экспорта");Writer writer=new OutputStreamWriter(out,StandardCharsets.UTF_8);writer.write((format.equals("md")?"# ":"")+doc.optString("title")+"\n\n");for(int i=0;i<pages.length();i++){JSONObject p=photos.get(pages.getString(i));writer.write((format.equals("md")?"## ":"")+"Страница "+(i+1)+"\n\n");JSONObject a=p.optJSONObject("analysis");if(format.equals("md")&&!p.optBoolean("edited")&&a!=null&&a.optJSONArray("blocks")!=null){JSONArray blocks=a.getJSONArray("blocks");for(int j=0;j<blocks.length();j++){JSONObject b=blocks.getJSONObject(j);if(b.optBoolean("complete")&&!b.optString("html").isEmpty())writer.write(b.getString("html")+"\n\n");}}else writer.write(p.optString("text")+"\n\n");}writer.flush();
    }
}
