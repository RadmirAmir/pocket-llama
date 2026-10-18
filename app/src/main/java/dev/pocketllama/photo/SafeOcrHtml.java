package dev.pocketllama.photo;

import java.io.IOException;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.safety.*;

/** Model output is document data; URLs, CSS and executable elements never reach the WebView. */
public final class SafeOcrHtml {
    private SafeOcrHtml() { }
    public static JSONObject parse(String raw) throws Exception {
        if(raw==null||raw.length()>600000)throw new IOException("Область содержит слишком много текста");
        if(raw.chars().filter(c->c=='<').count()>20000)throw new IOException("Слишком сложная разметка области");
        String value=raw.trim();if(value.startsWith("```")){int n=value.indexOf('\n');if(n>=0)value=value.substring(n+1).replaceFirst("\\s*```$","").trim();}
        Document source=Jsoup.parseBodyFragment(value);source.select("script,style,iframe,object,embed,svg,form,textarea,button,link,meta").remove();
        for(Element e:source.select("input"))e.replaceWith(new TextNode(e.hasAttr("checked")?"☑":"☐"));
        for(Element e:source.select("img"))e.remove();
        Safelist allowed=new Safelist().addTags("div","p","b","strong","i","em","u","del","s","sup","sub","pre","code","span","ul","ol","li","h1","h2","h3","h4","h5","h6","br","hr","table","thead","tbody","tfoot","tr","th","td","caption","math","small","big","chem").addAttributes("td","rowspan","colspan").addAttributes("th","rowspan","colspan");
        Document clean=new Cleaner(allowed).clean(source);clean.outputSettings().prettyPrint(false);
        for(Element e:clean.select("[rowspan],[colspan]"))for(String attribute:new String[]{"rowspan","colspan"})if(e.hasAttr(attribute)) {
            try{int n=Integer.parseInt(e.attr(attribute));if(n<1||n>200)throw new NumberFormatException();e.attr(attribute,String.valueOf(n));}catch(NumberFormatException x){e.removeAttr(attribute);}
        }
        StringBuilder text=new StringBuilder();plain(clean.body(),text);String plain=text.toString().replaceAll("[ \\t]+\\n","\n").replaceAll("\\n{3,}","\n\n").trim();
        if(plain.length()>250000)throw new IOException("Слишком много текста на странице");
        JSONArray tables=new JSONArray();for(Element table:clean.select("table")){if(table.parents().stream().anyMatch(e->e.tagName().equals("table")))continue;if(tables.length()>=40)throw new IOException("Больше 40 таблиц в одной области");tables.put(table(table));}
        return new JSONObject().put("html",clean.body().html()).put("text",plain).put("tables",tables);
    }
    private static void plain(Node node,StringBuilder text) {plain(node,text,0);}
    private static void plain(Node node,StringBuilder text,int depth) {
        if(depth>128)throw new IllegalArgumentException("Слишком глубокая разметка области");
        if(node instanceof TextNode){text.append(((TextNode)node).getWholeText());return;}
        String tag=node.nodeName();if(tag.equals("br")||tag.equals("hr"))text.append('\n');
        for(Node child:node.childNodes())plain(child,text,depth+1);
        if(tag.equals("td")||tag.equals("th"))text.append('\t');
        else if(Arrays.asList("p","div","li","tr","table","pre","h1","h2","h3","h4","h5","h6").contains(tag))text.append('\n');
    }
    private static JSONObject table(Element table) throws Exception {
        String[][] grid=new String[200][200];JSONArray cells=new JSONArray();int rows=0,cols=0;
        for(Element row:table.select("tr")) {
            Element owner=row.closest("table");if(owner!=table)continue;
            if(rows>=200)throw new IOException("В таблице больше 200 строк");int col=0;
            for(Element cell:row.children())if(cell.tagName().equals("td")||cell.tagName().equals("th")) {
                while(col<200&&grid[rows][col]!=null)col++;if(col>=200||cells.length()>=5000)throw new IOException("Слишком большая таблица");
                int rs=span(cell,"rowspan"),cs=span(cell,"colspan");if(rows+rs>200||col+cs>200)throw new IOException("Слишком большая объединённая ячейка");
                StringBuilder t=new StringBuilder();plain(cell,t);String content=t.toString().trim();
                for(int y=rows;y<rows+rs;y++)for(int x=col;x<col+cs;x++){if(grid[y][x]!=null)throw new IOException("Пересекающиеся ячейки таблицы");grid[y][x]="";}
                grid[rows][col]=content;cells.put(new JSONObject().put("row",rows).put("col",col).put("rowspan",rs).put("colspan",cs).put("header",cell.tagName().equals("th")).put("text",content));col+=cs;cols=Math.max(cols,col);
            }rows++;
        }
        int allRows=rows;for(int y=rows;y<200;y++){boolean used=false;for(int x=0;x<cols;x++)if(grid[y][x]!=null){used=true;break;}if(used)allRows=y+1;}
        JSONArray data=new JSONArray();for(int y=0;y<allRows;y++){JSONArray a=new JSONArray();for(int x=0;x<cols;x++)a.put(grid[y][x]==null?"":grid[y][x]);data.put(a);}
        return new JSONObject().put("rows",data).put("cells",cells).put("rowCount",allRows).put("colCount",cols).put("html",table.outerHtml());
    }
    private static int span(Element cell,String name){try{return Math.max(1,Math.min(200,Integer.parseInt(cell.attr(name))));}catch(Exception e){return 1;}}
}
