import com.sun.net.httpserver.*;
import dev.pocketllama.voice.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reported topic-only video output, duplicate citations and bounded semantic repair. */
public final class VideoSummaryContentTests {
    static int checks,calls;static String mode;static final List<JSONObject> requests=new ArrayList<>();
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static final class Source implements SummarySource {
        final Map<String,String> saved=new HashMap<>();
        public Reader turns(){return new Reader(){boolean read;public String next(){if(read)return null;read=true;return "[#65] Бабич: Встречу предложили провести в среду, но дату ещё не согласовали.\n[#66] Песков: До согласования точную дату называть рано.\n";}public void close(){}};}
        public Reader notes(int pass){throw new AssertionError("short fixture unexpectedly reduced");}
        public String note(int pass,int index){return saved.get(pass+":"+index);}
        public void note(int pass,int index,String text){saved.put(pass+":"+index,text);}
        public boolean containsTurn(int id){return id==65||id==66;}
    }
    static void reply(HttpExchange e,String body,String type)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type",type);e.sendResponseHeaders(200,b.length);try(OutputStream out=e.getResponseBody()){out.write(b);}}
    static JSONObject fact(String text,int... ids){JSONArray refs=new JSONArray();for(int id:ids)refs.put(id);return new JSONObject().put("text",text).put("sources",refs);}
    static String run(int port,Source source,boolean english)throws Exception{return new LocalSummary(source,port,"test",4096,true,english).summarize(x->{});}
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{try{
            JSONObject body=new JSONObject(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));String path=e.getRequestURI().getPath();
            if(path.equals("/apply-template")){reply(e,new JSONObject().put("prompt",body.getJSONArray("messages").toString()).toString(),"application/json");return;}
            if(path.equals("/tokenize")){JSONArray ids=new JSONArray();for(int i=0;i<body.getString("content").length()/2;i++)ids.put(1);reply(e,new JSONObject().put("tokens",ids).toString(),"application/json");return;}
            if(!path.equals("/v1/chat/completions"))throw new AssertionError(path);
            requests.add(body);calls++;
            String text="Дату встречи ещё не согласовали.";
            if(mode.equals("topic-always")||mode.equals("topic-once")&&calls==1)text="Фрагмент про Пескова и Токаева";
            if(mode.equals("english-topic"))text="Segment about Peskov and Tokayev";
            if(mode.equals("markdown-once")&&calls==1)text="## Кратко\n- Фрагмент про Пескова";
            if(mode.equals("nested-markdown-once")&&calls==1)text="Дату пока не согласовали.\n## Кратко";
            if(mode.equals("citations-only"))text="[#66] [#66]";
            if(mode.equals("inline"))text+=" [#66]";
            JSONArray points=new JSONArray();if(mode.equals("coverage"))for(int i=0;i<5;i++)points.put(fact("Деталь "+i+": дату встречи ещё не согласовали.",65));
            JSONObject notes=new JSONObject().put("brief",new JSONArray().put(fact(text,66,66,66))).put("decisions",points).put("tasks",new JSONArray()).put("questions",new JSONArray());
            String chunk=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",notes.toString())).put("finish_reason","stop"))).toString();
            reply(e,"data: "+chunk+"\n\ndata: [DONE]\n\n","text/event-stream");
        }catch(Throwable error){error.printStackTrace();e.close();}});
        server.start();int port=server.getAddress().getPort();
        try{
            for(String scenario:new String[]{"topic-once","markdown-once","nested-markdown-once"}){
                mode=scenario;calls=0;requests.clear();Source source=new Source();String summary=run(port,source,false);
                check(calls==2&&summary.contains("Дату встречи ещё не согласовали."),scenario+" repaired using actual content");
                check(!summary.contains("Фрагмент про")&&!summary.contains("## Кратко -"),"placeholder and nested Markdown are never published");
                check(requests.get(1).getJSONArray("messages").toString().contains("Предыдущий ответ"),"repair receives an explicit content correction");
                check(source.saved.values().stream().noneMatch(x->x.contains("Фрагмент про")),"bad first response is never checkpointed");
                check(summary.indexOf("[#66]")==summary.lastIndexOf("[#66]"),"duplicate sources render once");
                int before=calls;check(run(port,source,false).equals(summary)&&calls==before,"only repaired content is reused");
            }
            for(String scenario:new String[]{"topic-always","english-topic","citations-only"}){
                mode=scenario;calls=0;Source source=new Source();
                try{run(port,source,scenario.equals("english-topic"));throw new AssertionError("Placeholder was accepted");}
                catch(IOException expected){check(expected.getMessage().contains("SUMMARY_CONTENT"),"persistent "+scenario+" has an explicit content failure");}
                check(calls==2&&source.saved.isEmpty(),"semantic repair is bounded and publishes no false result");
            }
            mode="inline";calls=0;String inline=run(port,new Source(),false);
            check(inline.indexOf("[#66]")==inline.lastIndexOf("[#66]"),"inline citation and repeated source array render once");
            mode="coverage";calls=0;String coverage=run(port,new Source(),false);
            check(coverage.contains("Деталь 4")&&calls==1,"final video summary keeps five key points without extra inference");
            check(!coverage.contains("Практические выводы")&&!coverage.contains("Не зафиксировано"),"empty video sections do not replace useful content");
            System.out.println("PASS: "+checks+" video-summary content, coverage and citation checks");
        }finally{server.stop(0);}
    }
}
