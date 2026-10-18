package dev.pocketllama.photo;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Base64;
import dev.pocketllama.ChatApi;
import dev.pocketllama.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Dedicated authenticated CPU VLM. Never changes the user's chat model or template settings. */
public final class SuryaRuntime implements AutoCloseable {
    public interface Check {void run()throws Exception;}
    public interface Output {void chunk(String text)throws Exception;}
    public interface Status {void update(String stage,String message)throws Exception;}
    private JSONObject lastMetrics=new JSONObject();private long loadMs;
    private Process process;private Thread logs;private volatile ChatApi client;private volatile boolean cancelled;private final StringBuilder tail=new StringBuilder();private final String alias="surya-"+UUID.randomUUID();private final String key=UUID.randomUUID().toString()+UUID.randomUUID();private final int port;private final Check check;
    public SuryaRuntime(Context c,File model,Check check)throws Exception {
        long loadStarted=SystemClock.elapsedRealtime();
        this.check=check;try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=s.getLocalPort();}
        try{ServerConfig cfg=new ServerConfig();cfg.modelName=model.getName();cfg.projectorName="surya-2-mmproj.gguf";cfg.port=port;cfg.contextPerSlot=16384;cfg.slots=1;cfg.threads=4;
            java.util.List<String> command=ServerCommand.build(new File(c.getApplicationInfo().nativeLibraryDir,"libllama_cpu.so"),model,new File("/proc/self/fd/0"),cfg,true);command.set(command.indexOf("--alias")+1,alias);
            command.set(command.indexOf("--batch-size")+1,"512");command.set(command.indexOf("--ubatch-size")+1,"128");
            command.addAll(java.util.Arrays.asList("--image-min-tokens",String.valueOf(SuryaImageSize.MIN_IMAGE_TOKENS),"--image-max-tokens",String.valueOf(SuryaImageSize.MAX_IMAGE_TOKENS)));ProcessBuilder pb=new ProcessBuilder(command).directory(c.getFilesDir()).redirectErrorStream(true);
            pb.environment().put("LD_LIBRARY_PATH",c.getApplicationInfo().nativeLibraryDir);pb.environment().put("TMPDIR",c.getCacheDir().getAbsolutePath());pb.environment().put("POCKET_LLAMA_PARENT_PID",String.valueOf(android.os.Process.myPid()));BackendEnvironment.apply(pb.environment(),cfg);pb.environment().remove("LLAMA_API_KEY");pb.environment().remove("CLASSPATH");
            process=pb.start();try(OutputStream out=process.getOutputStream()){out.write((key+"\n").getBytes(StandardCharsets.US_ASCII));}
            logs=new Thread(()->{try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null){synchronized(tail){tail.append(line.replace(key,"[redacted]")).append('\n');if(tail.length()>12000)tail.delete(0,tail.length()-12000);}}}catch(IOException ignored){}},"surya-native-log");logs.start();
            long deadline=SystemClock.elapsedRealtime()+180000;while(true){check.run();if(!process.isAlive())throw new IOException("Surya не запустилась: "+diagnostic());try{ChatApi probe=new ChatApi(port,key);JSONObject capabilities=probe.capabilities();if(capabilities.optBoolean("vision")&&alias.equals(capabilities.optString("alias"))&&process.isAlive())break;}catch(Exception ignored){}if(SystemClock.elapsedRealtime()>deadline)throw new IOException("Surya долго загружается. Освободите память и повторите");Thread.sleep(250);}
            loadMs=SystemClock.elapsedRealtime()-loadStarted;
        }catch(Exception|Error e){close();throw e;}
    }
    private String diagnostic(){synchronized(tail){String s=tail.toString();return s.substring(Math.max(0,s.length()-1200));}}
    public String request(Bitmap input,String prompt,boolean structured,boolean table,int tokens)throws Exception {
        return request(input,prompt,structured,table,tokens,table?SuryaImageSize.Task.TABLE:structured?SuryaImageSize.Task.LAYOUT:SuryaImageSize.Task.BLOCK,null);
    }
    public long loadMs(){return loadMs;}
    public JSONObject metrics(){return lastMetrics;}
    public String request(Bitmap input,String prompt,boolean structured,boolean table,int tokens,SuryaImageSize.Task task,Output output)throws Exception {
        return request(input,prompt,structured,table,tokens,task,output,null);
    }
    public String request(Bitmap input,String prompt,boolean structured,boolean table,int tokens,SuryaImageSize.Task task,Output output,Status status)throws Exception {
        lastMetrics=new JSONObject();long prepareStarted=SystemClock.elapsedRealtime();check.run();if(cancelled||!process.isAlive())throw new IOException("Surya остановлена. Готовые области сохранены");if(status!=null)status.update("prepare","Подготавливаем изображение");int[] size=SuryaImageSize.fit(input.getWidth(),input.getHeight(),task);Bitmap scaled=size[0]==input.getWidth()&&size[1]==input.getHeight()?input:Bitmap.createScaledBitmap(input,size[0],size[1],true);String data;try{byte[] encoded=ScanImages.jpeg(scaled,95);if(encoded.length>7*1024*1024)encoded=ScanImages.jpeg(scaled,85);if(encoded.length>7*1024*1024)throw new IOException("Слишком сложное изображение. Разделите страницу");data="data:image/jpeg;base64,"+Base64.encodeToString(encoded,Base64.NO_WRAP);}finally{if(scaled!=input)scaled.recycle();}
        long prepareMs=SystemClock.elapsedRealtime()-prepareStarted;
        JSONArray content=new JSONArray().put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",data))).put(new JSONObject().put("type","text").put("text",prompt));
        JSONObject body=new JSONObject().put("model",alias).put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",content))).put("stream",true).put("max_tokens",tokens).put("temperature",0.0).put("top_p",0.1).put("min_p",0).put("repeat_penalty",1.0).put("reasoning_budget_tokens",0).put("reasoning_effort","none").put("chat_template_kwargs",new JSONObject().put("enable_thinking",false)).put("cache_prompt",false).put("return_progress",true).put("stream_options",new JSONObject().put("include_usage",true));
        if(structured)body.put("response_format",new JSONObject().put("type","json_schema").put("json_schema",new JSONObject().put("name",table?"table":"layout").put("strict",true).put("schema",SuryaProtocol.schema(table))));
        final StringBuilder text=new StringBuilder();final String[] finish={""};final long[] progressAt={0};final Exception[] callbackFailure={null};final SuryaOutputGuard guard=structured?null:new SuryaOutputGuard(task==SuryaImageSize.Task.PAGE);ChatApi api=new ChatApi(port,key,180000);client=api;
        SuryaDeadline deadline=new SuryaDeadline(SuryaDeadline.milliseconds(task),api::cancel);
        try {
            if(status!=null)status.update("input",SuryaInputProgress.message(null));
            api.stream(body,new ChatApi.Listener(){
                private void fail(Exception e){callbackFailure[0]=e;api.cancel();throw new ProgressFailure(e);}
                private void tick(){try{check.run();}catch(Exception e){fail(e);}}
                private void report(String phase,String message){if(status!=null)try{status.update(phase,message);}catch(Exception e){fail(e);}}
                public void chunk(String s,String reasoning){tick();if(text.length()+s.length()>600000)fail(new Incomplete("output_limit"));if(text.length()==0&&!s.isEmpty())report("text","Распознаём текст");text.append(s);try{if(output!=null)output.chunk(s);if(guard!=null)guard.append(s);}catch(SuryaOutputGuard.Repetition e){fail(new Incomplete("repetition"));}catch(Exception e){fail(e);}}
                public void progress(JSONObject p,JSONObject metrics){tick();long now=SystemClock.elapsedRealtime();if(text.length()==0&&p!=null&&(now-progressAt[0]>=1000||p.optLong("total",0)>0&&p.optLong("processed",-1)==p.optLong("total",0))){progressAt[0]=now;report("input",SuryaInputProgress.message(p));}}
                public void complete(String why,JSONObject usage){finish[0]=why;}
            });
        }
        catch(Exception e){if(callbackFailure[0]!=null)throw callbackFailure[0];if(deadline.expired())throw new Incomplete("deadline");throw e;}finally{deadline.close();client=null;String why=callbackFailure[0] instanceof Incomplete?((Incomplete)callbackFailure[0]).reason:deadline.expired()?"deadline":finish[0];lastMetrics=api.statistics().put("task",task.name().toLowerCase(Locale.ROOT)).put("prepare_ms",prepareMs).put("image_width",size[0]).put("image_height",size[1]).put("max_tokens",tokens).put("deadline_ms",SuryaDeadline.milliseconds(task)).put("finish",why);}
        // ChatApi reports cancellation instead of propagating a cancelled listener's exception.
        if(callbackFailure[0]!=null)throw callbackFailure[0];if(deadline.expired())throw new Incomplete("deadline");
        check.run();if(!finish[0].equals("stop"))throw new Incomplete(finish[0]);if(text.length()==0)throw new IOException("Surya вернула пустой ответ. Повторите область");return text.toString();
    }
    private static final class ProgressFailure extends RuntimeException {final Exception original;ProgressFailure(Exception e){super(e);original=e;}}
    public static final class Incomplete extends IOException {public final String reason;Incomplete(String why){super(why.equals("repetition")?"Surya повторяет фрагмент; повтор остановлен. Можно продолжить оставшиеся области":why.equals("deadline")?"Истекло время запроса Surya. Готовые области сохранены; можно продолжить": "Surya не завершила область ("+why+"). Можно продолжить распознавание");reason=why;}}
    public void cancel(){cancelled=true;ChatApi c=client;if(c!=null)c.cancel();}
    @Override public void close(){cancel();boolean interrupted=Thread.interrupted();if(process!=null){process.destroy();try{if(!process.waitFor(3,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor();}}catch(InterruptedException e){interrupted=true;process.destroyForcibly();boolean done=false;while(!done)try{process.waitFor();done=true;}catch(InterruptedException again){interrupted=true;}}}if(logs!=null)try{logs.join(1500);}catch(InterruptedException e){interrupted=true;}if(interrupted)Thread.currentThread().interrupt();}
}
