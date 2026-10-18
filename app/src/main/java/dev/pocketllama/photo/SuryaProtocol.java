package dev.pocketllama.photo;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** Training-time prompts and bounded coordinate decoding from the pinned Surya release. */
public final class SuryaProtocol {
    public static final String VERSION="surya-2-block-v1";
    public static final String LAYOUT="Output the layout of this image as JSON. Each entry is a dict with \"label\", \"bbox\", and \"count\" fields. Bbox is x0 y0 x1 y1, normalized 0-1000.";
    public static final String BLOCK="OCR this block image to HTML.";
    public static final String TABLE="Output the table rows then columns as JSON. Each entry is a dict with \"label\" (\"Row\" or \"Col\") and \"bbox\" (x0 y0 x1 y1, normalized 0-1000).";
    public static final String PAGE="OCR this image to HTML. Each block is a div with data-label and data-bbox (x0 y0 x1 y1, normalized 0-1000).";
    public static final List<String> LABELS=Collections.unmodifiableList(Arrays.asList("Caption","Footnote","Equation-Block","List-Group","Page-Header","Page-Footer","Image","Section-Header","Table","Text","Complex-Block","Code-Block","Form","Table-Of-Contents","Figure","Chemical-Block","Diagram","Bibliography","Blank-Page"));
    private SuryaProtocol() { }
    public static JSONObject schema(boolean table) throws JSONException {
        JSONArray labels=new JSONArray(table?Arrays.asList("Row","Col"):LABELS);
        JSONObject fields=new JSONObject().put("label",new JSONObject().put("type","string").put("enum",labels))
            .put("bbox",new JSONObject().put("type","string").put("pattern","^[0-9]{1,4} [0-9]{1,4} [0-9]{1,4} [0-9]{1,4}$"));
        JSONArray required=new JSONArray().put("label").put("bbox");
        if(!table){fields.put("count",new JSONObject().put("type","integer").put("minimum",0).put("maximum",10000));required.put("count");}
        return new JSONObject().put("type","array").put("maxItems",200).put("items",new JSONObject().put("type","object").put("properties",fields).put("required",required).put("additionalProperties",false));
    }
    public static JSONArray parse(String raw,boolean table,int width,int height) throws Exception {
        if(raw==null||raw.length()>512000||width<1||height<1)throw new IOException("Некорректный ответ разметки Surya");
        String text=raw.trim();if(text.startsWith("```")){int n=text.indexOf('\n');if(n<0)throw new IOException("Незавершённая разметка");text=text.substring(n+1).replaceFirst("\\s*```$","").trim();}
        JSONArray items=new JSONArray(text);if(items.length()>200)throw new IOException("На странице слишком много областей. Разделите её на части");
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.getJSONObject(i);String label=item.getString("label");
            if(table?(!label.equals("Row")&&!label.equals("Col")):!LABELS.contains(label))throw new IOException("Неизвестный тип области Surya");
            double[] n=coordinates(item.get("bbox"));
            double x0=Math.max(0,Math.min(width,n[0]*width/1000)),y0=Math.max(0,Math.min(height,n[1]*height/1000));
            double x1=Math.max(0,Math.min(width,n[2]*width/1000)),y1=Math.max(0,Math.min(height,n[3]*height/1000));
            if(x1-x0<1||y1-y0<1)throw new IOException("Surya вернула пустую область");
            JSONArray bbox=new JSONArray().put(x0).put(y0).put(x1).put(y1),quad=new JSONArray().put(x0).put(y0).put(x1).put(y0).put(x1).put(y1).put(x0).put(y1);
            if(!seen.add(label+":"+bbox))throw new IOException("Surya повторила координаты области. Повтор остановлен; готовый текст сохранён");
            result.put(new JSONObject().put("index",i).put("label",label).put("bbox",bbox).put("box",quad).put("count",Math.max(0,Math.min(10000,item.optInt("count")))).put("complete",false));
        }return result;
    }
    public static double[] coordinates(Object value) throws Exception {
        double[] q=new double[4];if(value instanceof JSONArray){JSONArray a=(JSONArray)value;if(a.length()!=4)throw new IOException("Четыре координаты области");for(int i=0;i<4;i++)q[i]=a.getDouble(i);}
        else {String[] a=String.valueOf(value).trim().split("[ ,]+");if(a.length!=4)throw new IOException("Четыре координаты области");for(int i=0;i<4;i++)q[i]=Double.parseDouble(a[i]);}
        for(double n:q)if(!Double.isFinite(n)||n<0||n>1000)throw new IOException("Координаты Surya вне страницы");
        if(q[2]<=q[0]||q[3]<=q[1])throw new IOException("Пересечённая область Surya");return q;
    }
    public static boolean visual(String label){return label.equals("Figure")||label.equals("Image")||label.equals("Diagram")||label.equals("Blank-Page");}
    public static int tokenBudget(int count){return Math.max(256,Math.min(4096,count+100));}
}
