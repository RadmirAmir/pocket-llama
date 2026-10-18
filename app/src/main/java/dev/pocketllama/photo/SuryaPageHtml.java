package dev.pocketllama.photo;

import java.io.IOException;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Full-page training format. Only explicitly closed, attributed blocks become checkpoints. */
public final class SuryaPageHtml {
    public static class FormatException extends IOException {
        FormatException(String message){super(message);}
        FormatException(Exception cause){super("Не удалось прочитать разметку целой страницы",cause);}
    }
    public static final class RepetitionException extends FormatException {
        public final JSONArray completed;
        RepetitionException(JSONArray completed){super("Surya повторяет область страницы");this.completed=completed;}
    }
    private SuryaPageHtml() { }
    public static JSONArray parse(String raw,int width,int height,boolean finished)throws FormatException {
        try {
            if(raw==null||raw.length()>600000||width<1||height<1)throw new FormatException("Слишком большая страница Surya");
            String value=raw.trim();
            if(value.startsWith("```")){int n=value.indexOf('\n');if(n<0)return incomplete(finished);value=value.substring(n+1).replaceFirst("\\s*```$","").trim();}
            // Jsoup repairs incomplete HTML. Do not mistake that repair for a completed model block.
            int depth=0,last=0;
            for(int i=0;i<value.length();i++)if(value.charAt(i)=='<') {
                if(value.startsWith("<!--",i)){int end=value.indexOf("-->",i+4);if(end<0)break;i=end+2;continue;}
                int end=endTag(value,i);if(end<0)break;
                String tag=value.substring(i+1,end).trim().toLowerCase(Locale.ROOT);
                if(tag.matches("div(?:\\s[\\s\\S]*)?"))depth++;
                else if(tag.matches("/div\\s*")){if(depth==0)throw new FormatException("Некорректное завершение области Surya");if(--depth==0)last=end+1;}
                i=end;
            }
            if(finished&&(depth!=0||last==0||!value.substring(last).trim().isEmpty()))throw new FormatException("Незавершённая разметка страницы");
            Document source=Jsoup.parseBodyFragment(value.substring(0,last));JSONArray blocks=new JSONArray();
            for(Node node:source.body().childNodes()) {
                if(node instanceof TextNode&&((TextNode)node).isBlank())continue;
                if(!(node instanceof Element))throw new FormatException("Текст вне областей страницы");
                Element div=(Element)node;
                if(!div.tagName().equals("div")||!div.hasAttr("data-label")||!div.hasAttr("data-bbox"))throw new FormatException("У области нет координат или типа");
                if(blocks.length()>=200)throw new FormatException("На странице больше 200 областей");
                String label=label(div.attr("data-label"));
                String coords=div.attr("data-bbox").trim();if(coords.startsWith("[")&&coords.endsWith("]"))coords=coords.substring(1,coords.length()-1);
                JSONObject descriptor=new JSONObject().put("label",label).put("bbox",coords).put("count",0);
                JSONObject block=SuryaProtocol.parse(new JSONArray().put(descriptor).toString(),false,width,height).getJSONObject(0);
                try{new SuryaOutputGuard(false).append(div.html());}catch(SuryaOutputGuard.Repetition e){throw new RepetitionException(blocks);}
                JSONObject html=SafeOcrHtml.parse(div.html());
                for(int i=0;i<blocks.length();i++){JSONObject old=blocks.getJSONObject(i);if(label.equals(old.getString("label"))&&block.getJSONArray("bbox").toString().equals(old.getJSONArray("bbox").toString())&&html.getString("text").equals(old.getString("text")))throw new RepetitionException(blocks);}
                block.put("index",blocks.length()).put("html",html.getString("html")).put("text",html.getString("text")).put("tables",html.getJSONArray("tables")).put("confidence",-1).put("complete",true);
                blocks.put(block);
            }
            if(finished&&blocks.length()==0)throw new FormatException("Surya не вернула области страницы");
            return blocks;
        }catch(FormatException e){throw e;}catch(Exception e){throw new FormatException(e);}
    }
    private static JSONArray incomplete(boolean finished)throws FormatException {if(finished)throw new FormatException("Незавершённый ответ Surya");return new JSONArray();}
    private static int endTag(String s,int begin) {
        char quote=0;for(int i=begin+1;i<s.length();i++){char c=s.charAt(i);if(quote!=0){if(c==quote)quote=0;}else if(c=='\''||c=='"')quote=c;else if(c=='>')return i;}return -1;
    }
    private static String label(String label)throws FormatException {
        String normalized=label.replaceAll("[-_ ]","").toLowerCase(Locale.ROOT);
        for(String value:SuryaProtocol.LABELS)if(value.replace("-","").toLowerCase(Locale.ROOT).equals(normalized))return value;
        if(normalized.equals("picture"))return "Image";
        if(normalized.equals("equation"))return "Equation-Block";
        if(normalized.equals("code"))return "Code-Block";
        throw new FormatException("Неизвестный тип области Surya");
    }
    /** Match only nearly identical regions when a long page needs the block fallback. */
    public static void reuse(JSONArray layout,JSONArray completed)throws JSONException {
        Set<Integer> used=new HashSet<>();
        for(int i=0;i<layout.length();i++) {
            JSONObject target=layout.getJSONObject(i);JSONArray a=target.getJSONArray("bbox");
            for(int j=0;j<completed.length();j++) {
                JSONObject old=completed.getJSONObject(j);if(used.contains(j)||!old.optBoolean("complete")||!target.getString("label").equals(old.optString("label")))continue;
                try{new SuryaOutputGuard(false).append(old.optString("html",old.optString("text")));}catch(SuryaOutputGuard.Repetition repeated){continue;}
                JSONArray b=old.getJSONArray("bbox");double overlap=Math.max(0,Math.min(a.getDouble(2),b.getDouble(2))-Math.max(a.getDouble(0),b.getDouble(0)))*Math.max(0,Math.min(a.getDouble(3),b.getDouble(3))-Math.max(a.getDouble(1),b.getDouble(1)));
                double area=(a.getDouble(2)-a.getDouble(0))*(a.getDouble(3)-a.getDouble(1))+(b.getDouble(2)-b.getDouble(0))*(b.getDouble(3)-b.getDouble(1))-overlap;
                if(area>0&&overlap/area>=.9){for(String key:new String[]{"html","text","tables","confidence","tableStructure"})if(old.has(key))target.put(key,old.get(key));target.put("complete",true);used.add(j);break;}
            }
        }
    }
}
