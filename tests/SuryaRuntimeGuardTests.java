import android.content.Context;
import android.graphics.Bitmap;
import dev.pocketllama.ChatApi;
import dev.pocketllama.photo.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Actual production runtime and HTTP client; only the child server and Android graphics are fixtures. */
public final class SuryaRuntimeGuardTests {
 static int checks;static void ok(boolean v,String name){if(!v)throw new AssertionError(name);checks++;}
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);Context context=new Context(root.toFile(),args[2]);
  try(SuryaRuntime runtime=new SuryaRuntime(context,Path.of(args[1]).toFile(),()->{})) {
   try{runtime.request(new Bitmap(900,1200),"repeat",false,false,8192,SuryaImageSize.Task.BLOCK,null);throw new AssertionError("loop accepted");}
   catch(SuryaRuntime.Incomplete e){ok(e.reason.equals("repetition"),"repetition cancellation retains its cause through ChatApi");}
   ok(runtime.metrics().getString("finish").equals("repetition"),"metrics explain the stopped generation");
   try{runtime.request(new Bitmap(900,1200),"normal",false,false,8192,SuryaImageSize.Task.PAGE,text->{throw new IOException("checkpoint sentinel");});throw new AssertionError("callback failure lost");}
   catch(IOException e){ok(e.getMessage().equals("checkpoint sentinel"),"checkpoint errors are not swallowed as cancellation");}
   String good=runtime.request(new Bitmap(900,1200),"normal",false,false,8192,SuryaImageSize.Task.PAGE,null);
   ok(SuryaPageHtml.parse(good,900,1200,true).getJSONObject(0).getString("text").contains("125 480"),"same loaded runtime remains usable after abort");
  }
  ExecutorService threads=Executors.newCachedThreadPool();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(threads);CountDownLatch entered=new CountDownLatch(1);AtomicReference<String> finish=new AtomicReference<>("");
  server.createContext("/v1/chat/completions",exchange->{try{exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);entered.countDown();for(int i=0;i<100;i++){exchange.getResponseBody().write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));exchange.getResponseBody().flush();Thread.sleep(20);}}catch(Exception expected){}finally{exchange.close();}});server.start();
  ChatApi api=new ChatApi(server.getAddress().getPort(),"test",3000);Future<?> request=threads.submit(()->{try{api.stream(new JSONObject(),new ChatApi.Listener(){public void chunk(String s,String r){}public void complete(String why,JSONObject usage){finish.set(why);}});}catch(Exception e){throw new RuntimeException(e);}});
  try {
   ok(entered.await(2,TimeUnit.SECONDS),"server entered an endless keepalive stream");long started=System.nanoTime();
   try(SuryaDeadline deadline=new SuryaDeadline(100,api::cancel)){request.get(3,TimeUnit.SECONDS);ok(deadline.expired()&&finish.get().equals("cancelled"),"wall deadline stops a live HTTP stream with only keepalives");ok((System.nanoTime()-started)/1000000<1500,"keepalives cannot extend the wall deadline");}
  }finally{api.cancel();server.stop(0);threads.shutdownNow();}
  System.out.println("SuryaRuntimeGuardTests: "+checks+" checks passed (production runtime/HTTP, scripted loopback child)");
 }
}
