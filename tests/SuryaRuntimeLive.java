import android.content.Context;
import android.graphics.Bitmap;
import dev.pocketllama.photo.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Real production runtime + ChatApi + pinned native server. Android image APIs are host adapters. */
public final class SuryaRuntimeLive {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),model=Path.of(args[1]);Context context=new Context(root.toFile(),args[2]);
  JSONArray events=new JSONArray();long started=System.nanoTime();final long[] firstText={-1};
  try(SuryaRuntime runtime=new SuryaRuntime(context,model.toFile(),()->{})) {
   String html=runtime.request(new Bitmap(900,1200),SuryaProtocol.PAGE,false,false,8192,SuryaImageSize.Task.PAGE,text->{if(firstText[0]<0)firstText[0]=(System.nanoTime()-started)/1000000;},(phase,message)->{long ms=(System.nanoTime()-started)/1000000;events.put(new JSONObject().put("phase",phase).put("message",message).put("elapsed_ms",ms));System.out.println(phase+" · "+message+" · "+ms+" ms");});
   if(firstText[0]<0)throw new AssertionError("No text");boolean beforeText=false;
   for(int i=0;i<events.length();i++){JSONObject event=events.getJSONObject(i);if(event.getString("phase").equals("input")&&event.getString("message").contains("% подготовки")&&event.getLong("elapsed_ms")<firstText[0])beforeText=true;}
   if(!beforeText)throw new AssertionError("Native input progress did not arrive before first text");
   JSONArray blocks=SuryaPageHtml.parse(html,900,1200,true);if(blocks.length()==0)throw new AssertionError("No full-page blocks");
   JSONObject result=new JSONObject().put("model_load_ms",runtime.loadMs()).put("first_text_ms",firstText[0]).put("events",events).put("metrics",runtime.metrics()).put("block_count",blocks.length());Files.writeString(root.resolve("result.json"),result.toString(2));
   System.out.println("Production SuryaRuntime: actual native input progress before first text; "+blocks.length()+" parsed blocks; load "+runtime.loadMs()+" ms");
  }
 }
}
