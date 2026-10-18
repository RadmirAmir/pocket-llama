import com.sun.net.httpserver.*;
import dev.pocketllama.voice.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reproduces length failures and interrupted/restarted jobs through the real HTTP client. */
public final class SummaryRecoveryTests {
    static int checks,requests,leftRequests;static volatile String mode="normal";static volatile int modeRequests;
    static volatile boolean dropRight;static boolean sawTail;static final List<JSONObject> bodies=new ArrayList<>();
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static final class Source implements SummarySource{
        final List<String> data=new ArrayList<>();final TreeMap<Integer,TreeMap<Integer,String>> saved=new TreeMap<>();int extraIds;
        Reader reader(Collection<String> values){Iterator<String> it=values.iterator();return new Reader(){public String next(){return it.hasNext()?it.next():null;}public void close(){}};}
        public Reader turns(){return reader(data);}
        public Reader notes(int pass){return reader(new ArrayList<>(saved.getOrDefault(pass,new TreeMap<>()).values()));}
        public String note(int pass,int index){return saved.getOrDefault(pass,new TreeMap<>()).get(index);}
        public void note(int pass,int index,String value){saved.computeIfAbsent(pass,p->new TreeMap<>()).put(index,value);}
        public boolean containsTurn(int id){return id>=1&&id<=Math.max(data.size(),extraIds);}
    }
    static void reset(String value){mode=value;modeRequests=0;leftRequests=0;bodies.clear();sawTail=false;dropRight=false;}
    static Source shortSource(){Source s=new Source();s.data.add("[#1] Иван: Решили провести пилот в пятницу.\n");return s;}
    static String run(Source s,int port)throws Exception{return new LocalSummary(s,port,"test",2048).summarize(x->{});}
    static void reply(HttpExchange e,String value,String type)throws IOException{
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type",type);e.sendResponseHeaders(200,bytes.length);
        try(OutputStream out=e.getResponseBody()){out.write(bytes);}
    }
    static String event(String content,String reasoning,String finish)throws Exception{
        JSONObject delta=new JSONObject();if(!content.isEmpty())delta.put("content",content);if(!reasoning.isEmpty())delta.put("reasoning_content",reasoning);
        JSONObject choice=new JSONObject().put("delta",delta);if(finish!=null)choice.put("finish_reason",finish);
        return "data: "+new JSONObject().put("choices",new JSONArray().put(choice))+"\n\n";
    }
    static String source(JSONObject body){String text=body.getJSONArray("messages").getJSONObject(1).getString("content");return text.substring(text.indexOf("<SOURCE>")+8,text.lastIndexOf("</SOURCE>"));}
    static void handle(HttpExchange e){
        try{
            if(!"Bearer test".equals(e.getRequestHeaders().getFirst("Authorization")))throw new AssertionError("Authentication lost");
            // This scenario deliberately keeps the server unavailable so manual
            // resume still exercises reuse of the completed adaptive half.
            if(e.getRequestURI().getPath().equals("/v1/models")){e.sendResponseHeaders(503,-1);e.close();return;}
            JSONObject body=new JSONObject(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));String path=e.getRequestURI().getPath();
            if(path.equals("/apply-template")){
                if(body.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"))throw new AssertionError("Budget template enables thinking");
                reply(e,new JSONObject().put("prompt",body.getJSONArray("messages").toString()).toString(),"application/json");return;
            }
            if(path.equals("/tokenize")){JSONArray tokens=new JSONArray();for(int i=0;i<(body.getString("content").length()+1)/2;i++)tokens.put(1);reply(e,new JSONObject().put("tokens",tokens).toString(),"application/json");return;}
            if(!path.equals("/v1/chat/completions"))throw new AssertionError(path);
            int count=(body.getJSONArray("messages").toString().length()+1)/2;
            if(count+body.getInt("max_tokens")+64>2048)throw new AssertionError("Generation exceeds allocated context");
            if(!body.getString("reasoning_effort").equals("none")||body.getInt("reasoning_budget_tokens")!=0||body.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"))throw new AssertionError("Reasoning not disabled");
            requests++;modeRequests++;bodies.add(body);String src=source(body);sawTail|=src.contains("TAIL_UNIQUE");
            JSONObject schema=body.getJSONObject("response_format").getJSONObject("json_schema").getJSONObject("schema");
            JSONArray ids=schema.getJSONObject("properties").getJSONObject("brief").getJSONObject("items").getJSONObject("properties").getJSONObject("sources").getJSONObject("items").getJSONArray("enum");
            int id=ids.getInt(0);
            boolean left=src.contains("LEFT_MARKER"),right=src.contains("RIGHT_MARKER");
            if(left&&!right)leftRequests++;
            if(mode.equals("split")&&left&&right||mode.equals("partial_once")&&modeRequests==1||mode.equals("all_length")){
                reply(e,event("{\"brief\":[","","length")+"data: [DONE]\n\n","text/event-stream");return;
            }
            if(mode.equals("thinking")){reply(e,event("","unwanted internal reasoning","length")+"data: [DONE]\n\n","text/event-stream");return;}
            if(mode.equals("split")&&right&&!left&&dropRight){
                dropRight=false;reply(e,event("{\"brief\":[","",null),"text/event-stream");return;
            }
            if(mode.equals("foreign"))id=2;
            String text=mode.equals("overlong")?"я".repeat(161):left?"Левая заметка":right?"Правая заметка":"Краткая проверенная заметка";
            JSONObject fact=new JSONObject().put("text",text).put("sources",new JSONArray().put(id));
            String json=new JSONObject().put("brief",new JSONArray().put(fact)).put("decisions",new JSONArray()).put("tasks",new JSONArray()).put("questions",new JSONArray()).toString();
            String finish=mode.equals("complete_length")?"length":"stop";
            reply(e,event(json,"",null)+event("","",finish)+"data: [DONE]\n\n","text/event-stream");
        }catch(Throwable failure){
            failure.printStackTrace();try{byte[] b="{\"error\":{\"message\":\"Fixture failure\"}}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(500,b.length);e.getResponseBody().write(b);}catch(Exception ignored){}finally{e.close();}
        }
    }
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",SummaryRecoveryTests::handle);server.start();int port=server.getAddress().getPort();
        try{
            reset("partial_once");Source retry=shortSource();String result=run(retry,port);
            check(result.contains("[#1]")&&modeRequests==2,"length is retried automatically");
            check(bodies.get(0).getInt("max_tokens")>682,"unused context becomes output budget");
            JSONObject first=bodies.get(0).getJSONObject("response_format").getJSONObject("json_schema").getJSONObject("schema");
            JSONObject second=bodies.get(1).getJSONObject("response_format").getJSONObject("json_schema").getJSONObject("schema");
            check(first.getJSONObject("properties").getJSONObject("brief").getInt("maxItems")>second.getJSONObject("properties").getJSONObject("brief").getInt("maxItems"),"retry uses a smaller answer contract");
            check(second.getJSONObject("properties").getJSONObject("brief").getJSONObject("items").getJSONObject("properties").getJSONObject("text").getInt("maxLength")==80,"retry text is grammar-bounded");
            int before=requests;check(run(retry,port).equals(result)&&requests==before,"completed final answer reused without inference");

            reset("complete_length");Source complete=shortSource();check(run(complete,port).contains("[#1]")&&modeRequests==1,"complete valid JSON at length boundary accepted");
            reset("all_length");Source partial=shortSource();partial.data.set(0,"[#1] "+"длинная реплика ".repeat(20));
            try{run(partial,port);throw new AssertionError("Partial JSON was published");}catch(IOException expected){check(expected.getMessage().contains("SUMMARY_LIMIT"),"persistent length gets a specific bounded failure");}
            check(modeRequests<=8&&partial.saved.isEmpty(),"bounded attempts; incomplete responses never checkpointed");

            reset("thinking");Source thinking=shortSource();
            try{run(thinking,port);throw new AssertionError("Thinking-only output accepted");}catch(IOException expected){check(expected.getMessage().contains("SUMMARY_REASONING"),"incompatible thinking mode diagnosed");}
            check(modeRequests==2,"thinking-only model does not trigger an endless split/retry loop");

            reset("split");dropRight=true;Source split=new Source();
            split.data.add("[#1] LEFT_MARKER "+"данные левой стороны ".repeat(10)+"\n");
            split.data.add("[#2] RIGHT_MARKER "+"данные правой стороны ".repeat(10)+"\n");
            try{run(split,port);throw new AssertionError("Dropped connection accepted");}catch(IOException expected){check(expected.getMessage().contains("SUMMARY_SERVER_LOST"),"unavailable server remains explicit");}
            check(leftRequests==1&&!split.saved.isEmpty(),"completed adaptive half is durable before later failure");
            before=requests;String resumed=run(split,port);
            check(resumed.contains("[#")&&leftRequests==1,"resume reuses completed half");
            check(requests-before<=4,"resume only repeats unfinished work and merge");
            before=requests;check(run(split,port).equals(resumed)&&requests==before,"recovered final result is durable");

            reset("normal");Source longTurn=new Source();longTurn.data.add("[#1] "+"сведения без паузы ".repeat(500)+"TAIL_UNIQUE\n");run(longTurn,port);
            check(sawTail&&modeRequests>2,"oversized utterance is split without discarding its tail");
            reset("foreign");Source foreign=shortSource();foreign.extraIds=100;
            try{run(foreign,port);throw new AssertionError("Out-of-fragment reference accepted");}catch(IOException expected){check(expected.getMessage().contains("несуществующую"),"references constrained to this fragment, not just whole transcript");}
            check(foreign.saved.isEmpty(),"invalid reference never cached");
            reset("overlong");Source overlong=shortSource();
            try{run(overlong,port);throw new AssertionError("Unbounded note accepted");}catch(IOException expected){check(expected.getMessage().contains("длины"),"response bounds checked outside the model too");}
            check(overlong.saved.isEmpty(),"oversized response not cached");

            reset("normal");Source cancel=shortSource();LocalSummary engine=new LocalSummary(cancel,port,"test",2048);
            try{engine.summarize(text->{if(text.contains("пишем заметки"))engine.cancel();});throw new AssertionError("Mid-stream cancel ignored");}catch(InterruptedException expected){check(true,"cancellation inside streaming progress stops the job");}
            check(cancel.saved.isEmpty(),"cancelled response not checkpointed");
            reset("normal");Source hot=shortSource();
            try{new LocalSummary(hot,port,"test",2048).summarize(text->{if(text.contains("пишем заметки"))throw new IOException("THERMAL_TEST");});throw new AssertionError("Progress guard ignored");}catch(IOException expected){check(expected.getMessage().equals("THERMAL_TEST"),"service guards propagate during token streaming");}
            check(hot.saved.isEmpty(),"failed service guard publishes no result");

            reset("normal");Source changed=shortSource();run(changed,port);before=requests;changed.data.set(0,"[#1] Мария: Перенесли пилот на вторник.\n");run(changed,port);
            check(requests==before+1,"changed source invalidates its cached generation");
            before=requests;String video=new LocalSummary(changed,port,"test",2048,true).summarize(x->{});
            check(requests==before+1&&video.contains("Ключевые тезисы"),"video and meeting checkpoints stay distinct");
            System.out.println("PASS: "+checks+" adaptive summary recovery checks; inference calls="+requests);
        }finally{server.stop(0);}
    }
}

