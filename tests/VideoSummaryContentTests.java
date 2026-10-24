import com.sun.net.httpserver.*;
import dev.pocketllama.voice.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The model may select speech, but can never manufacture the displayed video text. */
public final class VideoSummaryContentTests {
    static int checks,calls;static String mode;static final List<JSONObject> requests=new ArrayList<>();
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static final class Source implements SummarySource {
        int lookups;final TreeMap<Integer,TreeMap<Integer,String>> saved=new TreeMap<>();final List<String> data=new ArrayList<>(List.of(
            "[#65] 01:04 Бабич: Встречу предложили провести в среду, но дату ещё не согласовали.\n",
            "[#66] 01:08 Песков: До согласования точную дату называть рано.\n"));
        Reader reader(Collection<String> values){Iterator<String> it=values.iterator();return new Reader(){public String next(){return it.hasNext()?it.next():null;}public void close(){}};}
        public Reader turns(){return reader(data);}
        public Reader notes(int pass){return reader(new ArrayList<>(saved.getOrDefault(pass,new TreeMap<>()).values()));}
        public String note(int pass,int index){return saved.getOrDefault(pass,new TreeMap<>()).get(index);}
        public void note(int pass,int index,String text){saved.computeIfAbsent(pass,p->new TreeMap<>()).put(index,text);}
        public boolean containsTurn(int id){lookups++;for(String line:data)if(line.startsWith("[#"+id+"]"))return true;return false;}
        boolean noSaved(){return saved.isEmpty();}
    }
    static void reply(HttpExchange e,String body,String type)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type",type);e.sendResponseHeaders(200,b.length);try(OutputStream out=e.getResponseBody()){out.write(b);}}
    static String run(int port,Source source,boolean english,int context)throws Exception{return new LocalSummary(source,port,"test",context,true,english).summarize(x->{});}
    static String run(int port,Source source)throws Exception{return run(port,source,false,4096);}
    static JSONObject legacy(String title){return new JSONObject().put("brief",new JSONArray().put(new JSONObject().put("text",title).put("sources",new JSONArray().put(66)))).put("decisions",new JSONArray()).put("tasks",new JSONArray()).put("questions",new JSONArray());}
    static void reset(String value){mode=value;calls=0;requests.clear();}
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{try{
            JSONObject body=new JSONObject(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));String path=e.getRequestURI().getPath();
            if(path.equals("/apply-template")){reply(e,new JSONObject().put("prompt",body.getJSONArray("messages").toString()).toString(),"application/json");return;}
            if(path.equals("/tokenize")){JSONArray ids=new JSONArray();for(int i=0;i<body.getString("content").length()/2;i++)ids.put(1);reply(e,new JSONObject().put("tokens",ids).toString(),"application/json");return;}
            if(!path.equals("/v1/chat/completions"))throw new AssertionError(path);
            requests.add(body);calls++;
            JSONObject points=body.getJSONObject("response_format").getJSONObject("json_schema").getJSONObject("schema").getJSONObject("properties").getJSONObject("points");
            JSONArray slots=points.getJSONArray("prefixItems");JSONArray choice=new JSONArray();for(int i=0;i<slots.length();i++)choice.put(slots.getJSONObject(i).getJSONArray("enum").getInt(0));
            if(mode.equals("duplicates"))choice=new JSONArray().put(1).put(1);
            JSONObject result=new JSONObject().put("points",choice);
            if(mode.equals("title-once")&&calls==1||mode.equals("title-always"))result=legacy("Переговоры Пескова и Токаева");
            if(mode.equals("reported-once")&&calls==1)result=legacy("Фрагмент про Пескова и Токаева");
            if(mode.equals("foreign"))result.put("points",new JSONArray().put(999));
            if(mode.equals("fraction"))result.put("points",new JSONArray().put(1.5));
            if(mode.equals("strings"))result.put("points",new JSONArray().put("Песков рассказал о переговорах"));
            if(mode.equals("empty"))result.put("points",new JSONArray());
            String chunk=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",result.toString())).put("finish_reason","stop"))).toString();
            reply(e,"data: "+chunk+"\n\ndata: [DONE]\n\n","text/event-stream");
        }catch(Throwable error){error.printStackTrace();e.close();}});
        server.start();int port=server.getAddress().getPort();
        try{
            for(String scenario:new String[]{"title-once","reported-once"}){
                reset(scenario);Source source=new Source();String summary=run(port,source);
                check(calls==2&&summary.contains("Встречу предложили провести в среду, но дату ещё не согласовали."),scenario+" replaced with exact speech");
                check(!summary.contains("Переговоры")&&!summary.contains("Фрагмент про"),"invented topic labels cannot enter the result");
                check(requests.get(1).getJSONArray("messages").toString().contains("Предыдущий выбор"),"one bounded selection repair");
                check(summary.contains("не согласовали. [#65]\n")&&!summary.contains("не согласовали. [#66]")&&!summary.contains("01:04"),"source is bound to selected speech, not model-provided references or timestamps");
                int before=calls;check(run(port,source).equals(summary)&&calls==before,"verified checkpoint reused without inference");
            }
            for(String scenario:new String[]{"title-always","foreign","fraction","strings","empty","duplicates"}){
                reset(scenario);Source source=new Source();
                try{run(port,source);throw new AssertionError("Invalid selection accepted");}
                catch(IOException expected){check(expected.getMessage().contains("SUMMARY_CONTENT"),scenario+" explicit failure");}
                check(calls==2&&source.noSaved(),"invalid selection is bounded and never cached");
            }
            reset("all");Source repeated=new Source();repeated.data.add("[#67] 01:09 Песков: До согласования точную дату называть рано.\n");String unique=run(port,repeated);
            check(unique.indexOf("[#65]")==unique.lastIndexOf("[#65]")&&unique.contains("[#66]"),"repeated selected points render once");
            reset("all");Source six=new Source();six.data.clear();for(int n=1;n<=6;n++)six.data.add("[#"+n+"] Спикер: Утверждение "+n+" содержит конкретную сумму "+(n*12)+" рублей.\n");
            String coverage=run(port,six);check(coverage.contains("72 рублей")&&calls==1,"six different points in one short selection");
            check(requests.get(0).getInt("max_tokens")==192,"mobile selection output is limited to 192 tokens");
            check(!coverage.contains("Не зафиксировано")&&!coverage.contains("Решения"),"no empty meeting sections");
            reset("all");Source continuation=new Source();continuation.data.clear();continuation.data.add("[#65] 00:01 Спикер 1: Бюджет в размере 12 миллионов пока не\n");continuation.data.add("[#66] 00:02 Спикер 1: согласован. Начинать работы нельзя.\n");
            String joined=run(port,continuation);check(joined.contains("пока не согласован. Начинать работы нельзя.")&&joined.contains("[#65]")&&joined.contains("[#66]"),"split ASR sentences retain their negation and both source IDs");
            reset("all");Source heading=new Source();heading.data.add(0,"[#64] Фрагмент про Пескова и Токаева\n");String content=run(port,heading);
            check(!content.contains("Фрагмент про")&&content.contains("дату ещё не согласовали"),"a leading transcript heading does not abort or replace content");
            reset("all");Source stale=new Source();run(port,stale);int before=calls;
            for(TreeMap<Integer,String> notes:stale.saved.values())for(Map.Entry<Integer,String> note:notes.entrySet()){
                JSONObject cached=new JSONObject(note.getValue());cached.put("text","## Кратко\n- Песков и Токаев [#66]");note.setValue(cached.toString());
            }
            check(run(port,stale).contains("дату ещё не согласовали")&&calls==before+1,"matching cache key cannot authorize topic-only cached text");
            reset("all");String english=run(port,new Source(),true,4096);check(english.startsWith("## Overview")&&english.contains("дату ещё не согласовали"),"localized heading preserves speech language and qualifications");
            reset("all");Source longSource=new Source();longSource.data.clear();for(int n=1;n<=48;n++)longSource.data.add("[#"+n+"] Спикер: В районе номер "+n+" откроют библиотеку после экспертизы. Бюджет "+(n*12)+" миллионов ещё не утверждён. Проверку здания закончат до 20 мая.\n");
            String reduced=run(port,longSource,false,2048);check(calls>2&&reduced.contains("ещё не утверждён"),"hierarchical selection keeps actual speech at a 2048-token mobile context");
            check(longSource.lookups<=longSource.data.size(),"source existence checks are cached within a bounded window instead of quadratic SQLite queries");
            check(longSource.saved.keySet().stream().anyMatch(p->p>=0),"long-video intermediate selection checkpointed");
            before=calls;check(run(port,longSource,false,2048).equals(reduced)&&calls==before,"long-video resume validates and reuses selected speech");
            check(LocalSummary.VERSION.equals("summary-v3-adaptive")&&!LocalSummary.version(true).equals("summary-v4-video-facts"),"video cache invalidated without breaking meeting checkpoints");
            System.out.println("PASS: "+checks+" evidence-bound video content, mobile budget and recovery checks");
        }finally{server.stop(0);}
    }
}
