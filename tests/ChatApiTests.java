import dev.pocketllama.ChatApi;
import dev.pocketllama.core.GenerationProfile;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ChatApiTests {
    private static int checks;
    private static void check(boolean condition,String text){checks++;if(!condition)throw new AssertionError(text);}
    private interface Throwing{void run() throws Exception;}
    private static void rejects(Throwing task,String text)throws Exception{
        boolean failed=false;try{task.run();}catch(Exception error){failed=true;}check(failed,text);
    }
    public static void main(String[] ignored)throws Exception{
        AtomicInteger mode=new AtomicInteger();
        AtomicReference<JSONObject> captured=new AtomicReference<>();
        CountDownLatch waiting=new CountDownLatch(1),release=new CountDownLatch(1);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ExecutorService threads=Executors.newCachedThreadPool();server.setExecutor(threads);
        server.createContext("/",exchange->{
            try{
                if(!"Bearer test-local-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))){
                    byte[] error="{\"error\":{\"message\":\"unauthorized\"}}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(401,error.length);exchange.getResponseBody().write(error);return;
                }
                if(exchange.getRequestURI().getPath().equals("/props")){
                    byte[] props="{\"modalities\":{\"vision\":true},\"default_generation_settings\":{\"n_ctx\":8192},\"model_alias\":\"mobile-llama\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200,props.length);exchange.getResponseBody().write(props);return;
                }
                captured.set(new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
                int currentMode=mode.get();
                if(currentMode==4){waiting.countDown();release.await(5,TimeUnit.SECONDS);return;}
                if(currentMode==5){new CountDownLatch(1).await(700,TimeUnit.MILLISECONDS);return;}
                if(currentMode==1){
                    byte[] error="{\"error\":{\"message\":\"request exceeds the available context size\"}}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(400,error.length);exchange.getResponseBody().write(error);return;
                }
                exchange.getResponseHeaders().set("Content-Type","text/event-stream; charset=utf-8");
                exchange.sendResponseHeaders(200,0);
                String stream=": heartbeat\r\n\r\ndata: {\"choices\":[{\"delta\":{\"reasoning_content\":\"Проверю. \"},\"finish_reason\":null}]}\r\n\r\n"
                    +"data: {\"choices\":[{\"delta\":{\"content\":null},\"finish_reason\":null}],\"prompt_progress\":{\"total\":100,\"cache\":20,\"processed\":30,\"time_ms\":1000}}\n\n"
                    +"data: {\"choices\":[{\"delta\":{\"content\":\"Привет, \"},\"finish_reason\":null}]}\n\n"
                    +"data: {\"choices\":[{\"delta\":{\"content\":\"мир!\"},\"finish_reason\":null}]}\n\n";
                if(mode.get()!=3)stream+="data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                    +"data: {\"choices\":[],\"usage\":{\"completion_tokens\":3},\"timings\":{\"predicted_per_second\":7.5,\"predicted_n\":3,\"prompt_ms\":25,\"prompt_n\":12,\"untrusted\":\"discard\"}}\n\ndata: [DONE]\n\n";
                byte[] bytes=stream.getBytes(StandardCharsets.UTF_8);
                for(int i=0;i<bytes.length;i+=3){exchange.getResponseBody().write(bytes,i,Math.min(3,bytes.length-i));exchange.getResponseBody().flush();}
            }catch(Exception ignoredError){}finally{exchange.close();}
        });
        server.start();
        try{
            int port=server.getAddress().getPort();
            JSONObject caps=new ChatApi(port,"test-local-key").capabilities();
            check(caps.getBoolean("vision"),"Detect real server vision capability");
            check(caps.getInt("context")==8192,"Read effective per-slot context");
            rejects(()->new ChatApi(port,"wrong-key").capabilities(),"Authenticate capability query");
            JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content","Привет"));
            JSONObject request=ChatApi.request(messages,false,512,false);
            check(!request.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"),"Disable thinking via chat template");
            check(request.getDouble("min_p")==0.0,"Disable native min-p default that can trap MiniCPM in a loop");
            check(request.getInt("reasoning_budget_tokens")==0&&request.getString("reasoning_effort").equals("none"),"No-thinking request also closes native reasoning budget");
            JSONObject mini=ChatApi.request(messages,true,512,false,GenerationProfile.MINICPM5);
            check(mini.getDouble("temperature")==1.0&&mini.getDouble("top_p")==0.95&&mini.getInt("top_k")==40,"MiniCPM5 uses publisher sampling with the pinned server's top-k default");
            check(mini.getInt("reasoning_budget_tokens")==256&&!mini.has("reasoning_effort"),"Enabled thinking has a separate native budget without disabling thinking");
            check(ChatApi.request(messages,true,100000,false,GenerationProfile.MINICPM5).getInt("max_tokens")==2048,"Reasoning request still has a bounded total generation limit");
            for(GenerationProfile profile:GenerationProfile.values()) {
                for(int maxTokens:new int[]{-1,64,128,256,512,1024,2048,100000}) {
                    JSONObject bounded=ChatApi.request(messages,true,maxTokens,false,profile);
                    int total=bounded.getInt("max_tokens"),budget=bounded.getInt("reasoning_budget_tokens");
                    check(budget>0&&budget<=256&&budget<=total/2,"Thinking cannot consume the whole output allowance for "+profile.id+" / "+maxTokens);
                    check(bounded.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"),"Positive budget keeps the model's thinking template enabled");
                }
            }
            check(request.getBoolean("stream"),"Streaming mode");
            check(request.getBoolean("return_progress")&&request.getBoolean("timings_per_token")&&request.getBoolean("cache_prompt"),"Request progress, real native timings and prompt reuse");
            check(request.getJSONArray("messages").getJSONObject(0).getString("content").equals("Привет"),"Preserve Cyrillic user content");
            JSONArray imageContent=new JSONArray().put(new JSONObject().put("type","text").put("text","Что на фото?"))
                    .put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/png;base64,aGVsbG8=")));
            JSONArray withImage=new JSONArray().put(new JSONObject().put("role","user").put("content",imageContent));
            rejects(()->ChatApi.request(withImage,false,512,false),"Block images for text-only server");
            JSONObject multimodal=ChatApi.request(withImage,false,512,true);
            check(multimodal.getJSONArray("messages").getJSONObject(0).getJSONArray("content").length()==2,"Send image as typed content, not filename");
            imageContent.getJSONObject(1).getJSONObject("image_url").put("url","https://external.invalid/image.png");
            rejects(()->ChatApi.request(withImage,false,512,true),"Do not make server fetch remote attachments");
            JSONArray badRole=new JSONArray().put(new JSONObject().put("role","tool").put("content","bad"));
            rejects(()->ChatApi.request(badRole,false,512,false),"Reject unexpected roles");
            StringBuilder content=new StringBuilder(),reasoning=new StringBuilder();
            AtomicInteger finishes=new AtomicInteger();
            AtomicInteger progress=new AtomicInteger();
            ChatApi measured=new ChatApi(port,"test-local-key");
            measured.stream(request,new ChatApi.Listener(){
                public void chunk(String text,String thought){content.append(text);reasoning.append(thought);}
                public void complete(String reason,JSONObject usage){check(reason.equals("stop"),"Read finish reason");check(usage.optInt("completion_tokens")==3,"Read terminal usage");finishes.incrementAndGet();}
                public void progress(JSONObject prompt,JSONObject metrics){if(prompt!=null){check(prompt.getInt("processed")==30,"Progress preserves server processed count");progress.incrementAndGet();}}
            });
            check(content.toString().equals("Привет, мир!"),"Reassemble UTF-8 split across socket writes");
            check(reasoning.toString().equals("Проверю. "),"Keep reasoning separate from answer");
            check(finishes.get()==1,"One successful terminal callback");
            check(progress.get()==1,"One prompt progress event");
            JSONObject metrics=measured.statistics();
            check(metrics.getDouble("predicted_per_second")==7.5,"Use native tokens per second rather than counting chunks");
            check(metrics.getDouble("first_text_ms")>=metrics.getDouble("first_output_ms"),"Reasoning generation is distinguished from first visible answer");
            check(metrics.getDouble("request_ms")>=metrics.getDouble("first_text_ms"),"Monotonic request duration covers first output");
            check(!metrics.has("untrusted"),"Metric snapshot only contains allowlisted numeric fields");
            check(captured.get().getInt("max_tokens")==512,"Send bounded generation limit");
            check(!captured.get().toString().contains("test-local-key"),"Key stays in Authorization header");
            mode.set(1);
            try{new ChatApi(port,"test-local-key").stream(request,new ChatApi.Listener(){
                public void chunk(String text,String thought){}
                public void complete(String reason,JSONObject usage){throw new AssertionError("Unexpected success");}
            });throw new AssertionError("Context error was not raised");}
            catch(java.io.IOException error){check(error.getMessage().contains("Начните новый диалог"),"Explain context overflow");}
            mode.set(0);
            ChatApi cancelled=new ChatApi(port,"test-local-key");AtomicInteger cancelledEnds=new AtomicInteger();
            cancelled.stream(request,new ChatApi.Listener(){
                public void chunk(String text,String thought){if(!text.isEmpty())cancelled.cancel();}
                public void complete(String reason,JSONObject usage){check(reason.equals("cancelled"),"Cancellation is distinguished from success");cancelledEnds.incrementAndGet();}
            });
            check(cancelledEnds.get()==1,"One cancellation terminal callback");
            ChatApi before=new ChatApi(port,"test-local-key");before.cancel();
            rejects(before::capabilities,"Pre-cancelled capability query does not start");
            mode.set(4);
            ChatApi blocked=new ChatApi(port,"test-local-key");
            Future<String> blockedRequest=threads.submit(()->{AtomicReference<String> reason=new AtomicReference<>();blocked.stream(request,new ChatApi.Listener(){
                public void chunk(String a,String b){}
                public void complete(String r,JSONObject usage){reason.set(r);}
            });return reason.get();});
            check(waiting.await(2,TimeUnit.SECONDS),"Test request is blocked before response headers");
            long start=System.nanoTime();blocked.cancel();
            check(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1),"Stop does not wait for a silent server");
            check(blockedRequest.get(2,TimeUnit.SECONDS).equals("cancelled"),"Cancellation releases pending response headers");release.countDown();
            mode.set(5);
            try{new ChatApi(port,"test-local-key",150).stream(request,new ChatApi.Listener(){
                public void chunk(String a,String b){}
                public void complete(String a,JSONObject b){throw new AssertionError("Unexpected stalled-server success");}
            });throw new AssertionError("Timeout absent");}
            catch(java.io.IOException error){check(error.getMessage().contains("долго не присылает"),"Silent-server timeout produces an actionable error");}
            mode.set(3);
            rejects(()->new ChatApi(port,"test-local-key").stream(request,new ChatApi.Listener(){
                public void chunk(String text,String thought){}
                public void complete(String reason,JSONObject usage){throw new AssertionError("Unexpected EOF success");}
            }),"Do not report truncated connection as complete");
            System.out.println("PASS: "+checks+" chat HTTP assertions");
        }finally{server.stop(0);threads.shutdownNow();}
    }
}
