package dev.pocketllama.rag;

import android.content.Context;
import android.os.SystemClock;
import dev.pocketllama.ChatApi;
import dev.pocketllama.core.LoopbackHttp;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Short-lived CPU embedding process. The coordinator releases the chat model first. */
public final class RagRuntime implements AutoCloseable {
    private final String key=UUID.randomUUID().toString()+UUID.randomUUID(),alias="pocket-embeddings-"+UUID.randomUUID();
    private final int port;
    private final RagSources.Check check;
    private Process process;
    private Thread logs;
    private volatile HttpURLConnection active;
    private volatile boolean cancelled;
    public RagRuntime(Context c,File model,RagSources.Check check)throws Exception {
        this.check=check;try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=s.getLocalPort();}
        try {
            List<String> command=new ArrayList<>(Arrays.asList(new File(c.getApplicationInfo().nativeLibraryDir,"libllama_cpu.so").getAbsolutePath(),
                "--model",model.getAbsolutePath(),"--alias",alias,"--host","127.0.0.1","--port",""+port,"--api-key-file","/proc/self/fd/0",
                "--embeddings","--pooling","mean","--embd-normalize","2","--ctx-size","2048","--parallel","1","--batch-size","2048","--ubatch-size","2048",
                "--threads",""+Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors())),"--threads-batch","4","--device","none","--load-mode","mmap",
                "--cache-ram","0","--no-webui","--no-slots","--offline","--fit","off","--log-colors","off","--log-verbosity","0"));
            ProcessBuilder pb=new ProcessBuilder(command).directory(c.getFilesDir()).redirectErrorStream(true);
            pb.environment().put("LD_LIBRARY_PATH",c.getApplicationInfo().nativeLibraryDir);pb.environment().put("TMPDIR",c.getCacheDir().getAbsolutePath());pb.environment().put("POCKET_LLAMA_PARENT_PID",""+android.os.Process.myPid());pb.environment().remove("LLAMA_API_KEY");pb.environment().remove("CLASSPATH");
            process=pb.start();try(OutputStream out=process.getOutputStream()){out.write((key+"\n").getBytes(StandardCharsets.US_ASCII));}
            // Drain output without persisting model inputs, vectors or private source text.
            logs=new Thread(()->{try(InputStream in=process.getInputStream()){byte[] b=new byte[8192];while(in.read(b)!=-1){}}catch(IOException ignored){}},"rag-native-output");logs.start();
            long deadline=SystemClock.elapsedRealtime()+90000;
            while(true){check();if(!process.isAlive())throw new IOException("Не удалось запустить EmbeddingGemma");try{JSONObject caps=new ChatApi(port,key).capabilities();if(alias.equals(caps.optString("alias")))break;}catch(Exception ignored){}if(SystemClock.elapsedRealtime()>deadline)throw new IOException("EmbeddingGemma долго загружается. Повторите поиск");Thread.sleep(150);}
        }catch(Exception|Error e){close();throw e;}
    }
    private void check()throws Exception{check.run();if(cancelled)throw new InterruptedException("Поиск остановлен");}
    public float[] embed(String prompt)throws Exception {
        check();
        JSONObject tokens=post("/tokenize",new JSONObject().put("content",prompt).put("add_special",true).put("parse_special",false));
        JSONArray input=tokens.getJSONArray("tokens");if(input.length()>2048)throw new IOException("Фрагмент превышает контекст EmbeddingGemma");
        // Passing validated token IDs also prevents literal special-token strings inside sources being interpreted.
        JSONObject result=post("/v1/embeddings",new JSONObject().put("model",alias).put("input",input).put("encoding_format","float"));
        JSONArray rows=result.getJSONArray("data");if(rows.length()!=1)throw new IOException("Неверный ответ модели поиска");JSONArray values=rows.getJSONObject(0).getJSONArray("embedding");
        float[] vector=new float[values.length()];for(int i=0;i<vector.length;i++)vector[i]=(float)values.getDouble(i);check();return RagText.normalize(vector);
    }
    private JSONObject post(String path,JSONObject body)throws Exception {
        check();HttpURLConnection c=LoopbackHttp.open(port,path,key,3000,90000);active=c;
        try {
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            if(c.getResponseCode()!=200)throw new IOException("EmbeddingGemma: HTTP "+c.getResponseCode());
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){check();if(out.size()+n>1024*1024)throw new IOException("Слишком большой ответ EmbeddingGemma");out.write(b,0,n);}}
            return new JSONObject(out.toString("UTF-8"));
        }finally{active=null;c.disconnect();}
    }
    public void cancel(){cancelled=true;HttpURLConnection c=active;if(c!=null)c.disconnect();Process p=process;if(p!=null)p.destroy();}
    @Override public void close(){cancel();boolean interrupted=Thread.interrupted();if(process!=null){try{if(!process.waitFor(2,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor();}}catch(InterruptedException e){interrupted=true;process.destroyForcibly();}}if(logs!=null)try{logs.join(1000);}catch(InterruptedException e){interrupted=true;}if(interrupted)Thread.currentThread().interrupt();}
}
