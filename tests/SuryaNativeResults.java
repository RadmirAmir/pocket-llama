import dev.pocketllama.photo.*;
import java.nio.file.*;
import org.json.*;

/** Production parser consumes real pinned model responses, including the optional Q8 experiment. */
public final class SuryaNativeResults {
 public static void main(String[] args)throws Exception {
  JSONArray results=new JSONArray(Files.readString(Path.of(args[0])));int pages=0;
  for(int i=0;i<results.length();i++) {
   JSONObject r=results.getJSONObject(i);String label=r.getString("label");if(!label.endsWith("invoice")&&!label.endsWith("text"))continue;
   String raw=r.getJSONObject("response").getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");JSONArray blocks=SuryaPageHtml.parse(raw,r.getInt("width"),r.getInt("height"),true);
   StringBuilder text=new StringBuilder();boolean table=false;
   for(int j=0;j<blocks.length();j++){JSONObject b=blocks.getJSONObject(j);text.append(b.getString("text"));if(!b.getBoolean("complete"))throw new AssertionError("unfinished native block");if(b.getString("label").equals("Table")){JSONObject t=b.getJSONArray("tables").getJSONObject(0);if(t.getInt("rowCount")!=4||t.getInt("colCount")!=3)throw new AssertionError("native table shape");table=true;}}
   for(String value:label.endsWith("invoice")?new String[]{"Invoice","Бумага","500","100","600","Итого"}:new String[]{"25 октября 2026","Иван Петров","904172","15 дней"})if(text.indexOf(value)<0)throw new AssertionError(label+" missing "+value);
   if(label.endsWith("invoice")&&!table)throw new AssertionError("No table block");pages++;
  }
  if(pages<2)throw new AssertionError("Missing real full-page results");System.out.println("Production HTML parser: "+pages+" real VLM page responses passed");
 }
}
