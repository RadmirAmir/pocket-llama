import com.sun.net.httpserver.*;
import dev.pocketllama.voice.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Behavioral tests for long-input bounds, checkpoint reuse, invalid citations, cancel. */
public final class SummaryPipelineTests {
    static int requests;static boolean badCitation;
    static final class Source implements SummarySource{
        final ArrayList<String> data=new ArrayList<>();final TreeMap<Integer,TreeMap<Integer,String>> saved=new TreeMap<>();
        Reader reader(Collection<String> items){Iterator<String> i=items.iterator();return new Reader(){public String next(){return i.hasNext()?i.next():null;}public void close(){}};}
        public Reader turns(){return reader(data);}
        public Reader notes(int pass){return reader(new ArrayList<>(saved.getOrDefault(pass,new TreeMap<>()).values()));}
        public String note(int pass,int idx){return saved.getOrDefault(pass,new TreeMap<>()).get(idx);}
        public void note(int pass,int idx,String text){saved.computeIfAbsent(pass,p->new TreeMap<>()).put(idx,text);}
        public boolean containsTurn(int idx){return idx>=1&&idx<=data.size();}
    }
    static void reply(HttpExchange e,String text,String type)throws IOException{byte[] b=text.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type",type);e.sendResponseHeaders(200,b.length);try(OutputStream out=e.getResponseBody()){out.write(b);}}
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{try{
            String path=e.getRequestURI().getPath();
            if(path.equals("/v1/models")&&e.getRequestMethod().equals("GET")){reply(e,"{\"data\":[]}","application/json");return;}
            JSONObject body=new JSONObject(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            if(path.equals("/apply-template")){reply(e,new JSONObject().put("prompt",body.getJSONArray("messages").toString()).toString(),"application/json");return;}
            if(path.equals("/tokenize")){JSONArray tokens=new JSONArray();for(int i=0;i<(body.getString("content").length()+1)/2;i++)tokens.put(1);reply(e,new JSONObject().put("tokens",tokens).toString(),"application/json");return;}
            if(!path.equals("/v1/chat/completions"))throw new AssertionError(path);
            // Same mock tokenization for generation as the budget endpoint, with JSON
            // escaping overhead deliberately included on the conservative side.
            int tokens=(body.getJSONArray("messages").toString().length()+1)/2;
            if(tokens+body.getInt("max_tokens")+64>2048)throw new AssertionError("Context overflow");
            requests++;JSONObject properties=body.getJSONObject("response_format").getJSONObject("json_schema").getJSONObject("schema").getJSONObject("properties");boolean video=properties.has("points");
            JSONArray ids=video?new JSONArray().put(1):properties.getJSONObject("brief").getJSONObject("items").getJSONObject("properties").getJSONObject("sources").getJSONObject("items").getJSONArray("enum");
            JSONObject fact=new JSONObject().put("text","Автор привёл два аргумента.").put("sources",new JSONArray().put(badCitation?99999:ids.getInt(0)));
            String json=(video?new JSONObject().put("points",new JSONArray().put(1)):new JSONObject().put("brief",new JSONArray().put(fact)).put("decisions",new JSONArray().put(fact)).put("tasks",new JSONArray()).put("questions",new JSONArray())).toString();
            String chunk=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",json)))).toString();
            String end=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject()).put("finish_reason","stop"))).toString();
            reply(e,"data: "+chunk+"\n\ndata: "+end+"\n\ndata: [DONE]\n\n","text/event-stream");
        }catch(Throwable err){err.printStackTrace();e.close();}});
        server.start();int port=server.getAddress().getPort();
        try{
            Source longSource=new Source();for(int i=1;i<=60;i++)longSource.data.add("[#"+i+"] Спикер: "+"данные встречи ".repeat(25)+"\n");
            String summary=new LocalSummary(longSource,port,"test",2048).summarize(s->{});
            if(!summary.contains("[#")||requests<3||longSource.saved.getOrDefault(1,new TreeMap<>()).isEmpty())throw new AssertionError("Hierarchical reduction was not exercised");
            int before=requests;new LocalSummary(longSource,port,"test",2048).summarize(s->{});if(requests!=before)throw new AssertionError("Completed chunks/final summary were not reused");
            Source shortSource=new Source();shortSource.data.add("[#1] Реплика для теста\n");badCitation=true;
            try{new LocalSummary(shortSource,port,"test",2048).summarize(s->{});throw new AssertionError("Invented citation accepted");}catch(IOException expected){if(!expected.getMessage().contains("несуществующую"))throw expected;}
            badCitation=false;LocalSummary stopped=new LocalSummary(shortSource,port,"test",2048);stopped.cancel();int count=requests;
            try{stopped.summarize(s->{});throw new AssertionError("Cancelled summary continued");}catch(InterruptedException expected){}
            if(requests!=count)throw new AssertionError("Cancel still made inference calls");
            Source video=new Source();video.data.add("[#1] В лекции приведены два аргумента.\n");String videoSummary=new LocalSummary(video,port,"test",2048,true).summarize(s->{});
            if(!videoSummary.contains("В лекции приведены два аргумента.")||videoSummary.contains("Практические выводы")||videoSummary.contains("Ответственный:")||videoSummary.contains("Не зафиксировано"))throw new AssertionError("Video should contain the actual speech");
            Source english=new Source();english.data.add("[#1] Анна: запуск в пятницу.\n");
            String englishSummary=new LocalSummary(english,port,"test",2048,false,true).summarize(s->{});
            if(!englishSummary.contains("## Overview")||!englishSummary.contains("## Decisions")||!englishSummary.contains("Not recorded.")||!englishSummary.contains("[#1]"))throw new AssertionError("English summary labels or references lost");
            Source englishVideo=new Source();englishVideo.data.add("[#1] Пример из лекции.\n");
            String englishVideoSummary=new LocalSummary(englishVideo,port,"test",2048,true,true).summarize(s->{});
            if(!englishVideoSummary.contains("## Overview")||!englishVideoSummary.contains("Пример из лекции.")||englishVideoSummary.contains("## Practical takeaways")||englishVideoSummary.contains("## Decisions"))throw new AssertionError("Video uses localized labels and original speech");
            System.out.println("PASS: Russian and English meeting/video summary formats");
            System.out.println("PASS: long summary budgets, hierarchical reduction, checkpoint reuse, citation rejection, cancellation; inference calls="+requests);
        }finally{server.stop(0);}
    }
}
