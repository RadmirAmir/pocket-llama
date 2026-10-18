import dev.pocketllama.voice.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Real TCP faults. Can run with the OkHttp 2.x URLConnection adapter used by Android's client family. */
public final class SummaryTransportTests {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static final String TEXT="[#1] Иван: Пилот состоится в пятницу.\n";
    static final class Source implements SummarySource{
        final TreeMap<Integer,TreeMap<Integer,String>> saved=new TreeMap<>();
        Reader reader(Collection<String> items){Iterator<String> it=items.iterator();return new Reader(){public String next(){return it.hasNext()?it.next():null;}public void close(){}};}
        public Reader turns(){return reader(Collections.singletonList(TEXT));}
        public Reader notes(int pass){return reader(new ArrayList<>(saved.getOrDefault(pass,new TreeMap<>()).values()));}
        public String note(int pass,int index){return saved.getOrDefault(pass,new TreeMap<>()).get(index);}
        public void note(int pass,int index,String text){saved.computeIfAbsent(pass,p->new TreeMap<>()).put(index,text);}
        public boolean containsTurn(int id){return id==1;}
        String export()throws Exception{JSONArray entries=new JSONArray();for(Map.Entry<Integer,TreeMap<Integer,String>> p:saved.entrySet())for(Map.Entry<Integer,String> n:p.getValue().entrySet())entries.put(new JSONObject().put("pass",p.getKey()).put("index",n.getKey()).put("text",n.getValue()));return entries.toString();}
        void restore(String text)throws Exception{JSONArray entries=new JSONArray(text);for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);note(e.getInt("pass"),e.getInt("index"),e.getString("text"));}}
    }
    static final class FaultServer implements AutoCloseable{
        final ServerSocket listener;final ExecutorService workers=Executors.newCachedThreadPool();final String path,mode;
        final AtomicInteger remaining,hits=new AtomicInteger(),generations=new AtomicInteger(),probes=new AtomicInteger();
        final AtomicBoolean badHeaders=new AtomicBoolean();volatile boolean closed,healthy=true;volatile Throwable error;
        FaultServer(String path,String mode,int faults)throws IOException{
            this.path=path;this.mode=mode;remaining=new AtomicInteger(faults);listener=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));
            workers.submit(()->{while(!closed)try{Socket s=listener.accept();workers.submit(()->serve(s));}catch(IOException e){if(!closed)error=e;}});
        }
        int port(){return listener.getLocalPort();}
        static String line(InputStream in)throws IOException{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')break;if(b!='\r')bytes.write(b);if(bytes.size()>65536)throw new IOException("Test header too big");}
            return b<0&&bytes.size()==0?null:bytes.toString("ISO-8859-1");
        }
        void send(Socket s,int status,String type,String body,boolean truncate)throws IOException{
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 "+status+" test\r\nContent-Type: "+type+"\r\nContent-Length: "+(bytes.length+(truncate?700:0))+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
        }
        void serve(Socket socket){
            try(Socket s=socket){s.setSoTimeout(5000);InputStream in=s.getInputStream();String first=line(in);if(first==null)return;
                String target=first.split(" ")[1];Map<String,String> headers=new HashMap<>();String row;
                while((row=line(in))!=null&&!row.isEmpty()){int at=row.indexOf(':');if(at>0)headers.put(row.substring(0,at).toLowerCase(Locale.ROOT),row.substring(at+1).trim());}
                if(!"close".equalsIgnoreCase(headers.get("connection")))badHeaders.set(true);
                if(!"Bearer transport-key".equals(headers.get("authorization"))){send(s,401,"application/json","{\"error\":{\"message\":\"unauthorized\"}}",false);return;}
                int size=Integer.parseInt(headers.getOrDefault("content-length","0"));ByteArrayOutputStream data=new ByteArrayOutputStream();
                for(int i=0;i<size;i++){int b=in.read();if(b<0)return;data.write(b);}
                if(target.equals("/v1/models")){probes.incrementAndGet();send(s,healthy?200:503,"application/json","{\"data\":[{\"id\":\"mobile-llama\"}]}",false);return;}
                boolean fault=target.equals(path)&&remaining.getAndUpdate(n->n>0?n-1:0)>0;
                if(target.equals(path))hits.incrementAndGet();
                if(target.equals("/v1/chat/completions"))generations.incrementAndGet();
                if(fault&&mode.equals("headers"))return;
                if(fault&&mode.equals("lost")){healthy=false;return;}
                if(fault&&mode.startsWith("http")){int status=Integer.parseInt(mode.substring(4));send(s,status,"application/json","{\"error\":{\"message\":\"test refusal\"}}",false);return;}
                JSONObject body=new JSONObject(data.toString("UTF-8"));
                if(target.equals("/apply-template")){
                    String value=new JSONObject().put("prompt",body.getJSONArray("messages").toString()).toString();send(s,200,"application/json",fault?value.substring(0,value.length()/2):value,fault);return;
                }
                if(target.equals("/tokenize")){
                    JSONArray tokens=new JSONArray();for(int i=0;i<(body.getString("content").length()+1)/2;i++)tokens.put(1);
                    String value=new JSONObject().put("tokens",tokens).toString();send(s,200,"application/json",fault?value.substring(0,value.length()/2):value,fault);return;
                }
                if(!target.equals("/v1/chat/completions"))throw new AssertionError(target);
                if(fault&&mode.equals("frame")){send(s,200,"text/event-stream","data: {\"choices\":[{\"delta\":{\"content\":\"prefix",true);return;}
                if(fault&&mode.equals("body")){send(s,200,"text/event-stream",event("{\"brief\":[",null),true);return;}
                JSONObject fact=new JSONObject().put("text","Пилот состоится в пятницу.").put("sources",new JSONArray().put(1));
                String result=new JSONObject().put("brief",new JSONArray().put(fact)).put("decisions",new JSONArray()).put("tasks",new JSONArray()).put("questions",new JSONArray()).toString();
                String complete=event(result,null)+event("","stop");
                if(mode.equals("terminal"))send(s,200,"text/event-stream",complete,true);
                else send(s,200,"text/event-stream",complete+"data: [DONE]\n\n",false);
            }catch(Throwable e){if(!closed)error=e;}
        }
        static String event(String text,String finish)throws Exception{
            JSONObject choice=new JSONObject().put("delta",new JSONObject().put("content",text));if(finish!=null)choice.put("finish_reason",finish);
            return "data: "+new JSONObject().put("choices",new JSONArray().put(choice))+"\n\n";
        }
        public void close()throws Exception{closed=true;listener.close();workers.shutdownNow();workers.awaitTermination(2,TimeUnit.SECONDS);if(error!=null)throw new AssertionError("Fault server error",error);}
    }
    static String run(Source source,FaultServer server)throws Exception{return new LocalSummary(source,server.port(),"transport-key",2048).summarize(x->{});}
    public static void main(String[] args)throws Exception{
        List<String> options=Arrays.asList(args);
        if(options.contains("--android-http")){
            Object client=Class.forName("com.squareup.okhttp.OkHttpClient").getConstructor().newInstance();
            URLStreamHandlerFactory factory=(URLStreamHandlerFactory)Class.forName("com.squareup.okhttp.OkUrlFactory").getConstructor(client.getClass()).newInstance(client);
            URL.setURLStreamHandlerFactory(factory);
        }
        if(options.contains("--baseline")){
            try(FaultServer server=new FaultServer("/apply-template","headers",1)){
                try{run(new Source(),server);throw new AssertionError("Baseline unexpectedly recovered");}
                catch(IOException expected){System.out.println("BASELINE reproduced: "+expected.getClass().getSimpleName()+": "+expected.getMessage());}
            }return;
        }
        int exportAt=options.indexOf("--export");
        if(exportAt>=0){try(FaultServer server=new FaultServer("","",0)){Source source=new Source();run(source,server);Files.writeString(Path.of(args[exportAt+1]),source.export());System.out.println("Old checkpoint exported");}return;}
        for(String path:new String[]{"/apply-template","/tokenize","/v1/chat/completions"}){
            try(FaultServer server=new FaultServer(path,"headers",1)){Source source=new Source();String value=run(source,server);check(value.contains("[#1]"),"recover "+path+" before headers");check(server.hits.get()==(path.equals("/v1/chat/completions")?2:3)&&server.probes.get()==1,"one authenticated reconnect "+path+"; hits="+server.hits+", probes="+server.probes);check(!server.badHeaders.get(),"fresh connections "+path);}
        }
        for(String path:new String[]{"/apply-template","/tokenize"}){
            try(FaultServer server=new FaultServer(path,"body",1)){check(run(new Source(),server).contains("[#1]"),"recover truncated JSON body "+path);check(server.hits.get()==3,"metadata not mistaken for valid partial JSON "+path);}
        }
        for(String mode:new String[]{"body","frame"}){
            try(FaultServer server=new FaultServer("/v1/chat/completions",mode,1)){Source source=new Source();String result=run(source,server);check(result.contains("Пилот состоится")&&!result.contains("prefix"),"restart stream without concatenating partial output "+mode);check(server.generations.get()==2&&server.probes.get()==1,"one stream reconnect "+mode);check(!source.saved.isEmpty(),"only completed recovered result persisted "+mode);}
        }
        for(int status:new int[]{400,401,500}){
            try(FaultServer server=new FaultServer("/apply-template","http"+status,1)){Source source=new Source();try{run(source,server);throw new AssertionError("HTTP failure accepted");}catch(IOException expected){check(expected.getMessage().contains(""+status),"HTTP status retained "+status);}check(server.hits.get()==1&&server.probes.get()==0&&source.saved.isEmpty(),"permanent HTTP errors not retried "+status);}
        }
        try(FaultServer server=new FaultServer("/v1/chat/completions","lost",1)){Source source=new Source();try{run(source,server);throw new AssertionError("Dead server accepted");}catch(IOException expected){check(expected.getMessage().contains("SUMMARY_SERVER_LOST"),"server death diagnosed separately");check(!expected.getMessage().contains("Address@"),"no raw Android address in UI");}check(server.generations.get()==1&&source.saved.isEmpty(),"unavailable model not repeatedly relaunched");}
        try(FaultServer server=new FaultServer("/v1/chat/completions","headers",100)){Source source=new Source();try{run(source,server);throw new AssertionError("Endless reconnect accepted");}catch(IOException expected){check(expected.getMessage().contains("SUMMARY_STREAM_EOF"),"reconnect exhaustion names stage");}check(server.generations.get()==4&&server.probes.get()==3,"three reconnects per job maximum");check(source.saved.isEmpty(),"failed streams not checkpointed");}
        try(FaultServer server=new FaultServer("/v1/chat/completions","headers",1)){Source source=new Source();LocalSummary engine=new LocalSummary(source,server.port(),"transport-key",2048);try{engine.summarize(x->{if(x.contains("Восстанавливаем соединение"))engine.cancel();});throw new AssertionError("Reconnect cancellation ignored");}catch(InterruptedException expected){check(true,"cancel during reconnect");}check(server.generations.get()==1&&server.probes.get()==0&&source.saved.isEmpty(),"cancel prevents probe and replay");}
        try(FaultServer server=new FaultServer("/v1/chat/completions","terminal",0)){check(run(new Source(),server).contains("[#1]"),"complete terminal event survives later socket EOF");check(server.generations.get()==1&&server.probes.get()==0,"terminal reply not regenerated");}
        int checkpointAt=options.indexOf("--checkpoint");
        if(checkpointAt>=0)try(FaultServer server=new FaultServer("","",0)){Source source=new Source();source.restore(Files.readString(Path.of(args[checkpointAt+1])));check(run(source,server).contains("[#1]")&&server.generations.get()==0,"0.6.2 checkpoint remains reusable");}
        System.out.println("PASS: "+checks+" transport recovery checks ("+(options.contains("--android-http")?"OkHttp 2.7.5 URLConnection":"JDK URLConnection")+")");
    }
}

