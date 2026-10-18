import dev.pocketllama.video.*;
import dev.pocketllama.security.ChunkedFile;
import org.json.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class VideoCoreTests {
 private static int checks;
 private static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 private static void reject(String url)throws Exception{try{VideoUrl.parse(url);throw new AssertionError("Accepted "+url);}catch(IOException expected){checks++;}}
 private static void infoReject(JSONObject info)throws Exception{try{VideoDownloader.validateInfo(info);throw new AssertionError("Accepted metadata "+info);}catch(IOException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  check(VideoUrl.parse("Смотри https://youtu.be/abc?t=9 текст").equals("https://youtu.be/abc?t=9"),"share text");
  check(VideoUrl.parse("HTTPS://www.youtube.com/watch?v=abc").startsWith("HTTPS://"),"case-insensitive scheme");
  for(String u:new String[]{"file:///data/a","javascript:alert(1)","https://user:pass@example.com/a","https://localhost/a","http://127.0.0.1/a","https://[::1]/a","https://example.local/a","https://example.com:22/a","https://example.com/"+"x".repeat(5000)})reject(u);
  check(VideoUrl.at("https://youtu.be/abc?t=9&x=1",128000).equals("https://youtu.be/abc?x=1&t=128"),"replace timestamp");
  check(VideoUrl.at("https://youtu.be/abc?t=9&start=2&t=8&x=1#old",1000).equals("https://youtu.be/abc?x=1&t=1"),"replace repeated time parameters");
  check(VideoUrl.at("https://rutube.ru/video/abc",10000).equals("https://rutube.ru/video/abc"),"other sites keep original URL");
  infoReject(new JSONObject().put("_type","playlist"));infoReject(new JSONObject().put("is_live",true));infoReject(new JSONObject().put("live_status","is_upcoming"));infoReject(new JSONObject().put("duration",21601));infoReject(new JSONObject().put("filesize",VideoDownloader.MAX_BYTES+1));
  VideoDownloader.validateInfo(new JSONObject().put("duration",21600));checks++;
  Path dir=Files.createTempDirectory("pocket-video-tests");File media=dir.resolve("42e9de65-84f5-4d41-88c8-d75a5ec3ad20.media").toFile();SecretKeySpec key=new SecretKeySpec(new byte[32],"AES");
  VideoDownloader downloader=new VideoDownloader(new VideoRuntime(args[0]));JSONObject metadata=downloader.inspect("https://fixture.example/good",(n,t)->{});check(metadata.getString("title").equals("Видео · тест"),"bounded JSON and concurrent stderr drain");
  downloader.download("https://fixture.example/good",media,key,0,(n,t)->{});
  try(ChunkedFile encrypted=new ChunkedFile(media,false,key)){byte[] actual=new byte[1024*1024+333];check(encrypted.length()==actual.length,"all media streamed");check(encrypted.read(0,actual,0,actual.length)==actual.length,"random container read");for(int i=0;i<actual.length;i++)if(actual[i]!=(byte)(i%251))throw new AssertionError("byte "+i);checks++;byte[] window=new byte[4096];encrypted.read(65533,window,0,window.length);check(Arrays.equals(window,Arrays.copyOfRange(actual,65533,65533+window.length)),"cross-block random read");}
  byte[] disk=Files.readAllBytes(media.toPath());check(disk.length>media.length()-1024,"ciphertext exists");check(!Arrays.equals(Arrays.copyOfRange(disk,84,100),new byte[16]),"no zero PCM file");media.delete();
  try{new VideoDownloader(new VideoRuntime(args[0])).download("https://fixture.example/fail",media,key,0,(n,t)->{});throw new AssertionError("failure accepted");}catch(IOException e){check(e.getMessage().contains("ограничила"),"bounded error mapped without leaking URL");}
  check(!media.exists()&&!new File(media+".part").exists(),"failed transfer removed");
  for(int attempt=0;attempt<3;attempt++)try{downloader.inspect("https://fixture.example/bad-options",(n,t)->{});throw new AssertionError("invalid arguments accepted");}catch(IOException e){
   check(e.getMessage().contains("встроенный загрузчик")&&e.getMessage().contains("VIDEO_METADATA, yt-dlp 2")&&e.getMessage().contains("no such option"),"startup error and final stderr retained");
  }
  try{downloader.inspect("https://fixture.example/python-fail",(n,t)->{});throw new AssertionError("Python failure accepted");}catch(IOException e){check(e.getMessage().contains("встроенный загрузчик")&&e.getMessage().contains("ModuleNotFoundError")&&!e.getMessage().contains("/data/user"),"Python startup diagnosis without private paths");}
  try{downloader.download("https://fixture.example/diagnostic",media,key,0,(n,t)->{});throw new AssertionError("failed transport accepted");}catch(IOException e){
   check(e.getMessage().contains("VIDEO_AUDIO, yt-dlp 7")&&e.getMessage().contains("[ссылка]")&&e.getMessage().contains("[файл]"),"download stage and safe detail");
   check(!e.getMessage().contains("supersecret")&&!e.getMessage().contains("anothersecret")&&!e.getMessage().contains("cdn.example")&&!e.getMessage().contains("/data/user")&&e.getMessage().length()<500,"diagnostic bounded and secrets redacted");
  }
  try{downloader.download("https://fixture.example/html",media,key,0,(n,t)->{});throw new AssertionError("HTML accepted as media");}catch(IOException e){check(e.getMessage().contains("VIDEO_AUDIO_HTTP"),"HTML proxy response rejected even with fragmented BOM prefix");}
  check(!media.exists()&&!new File(media+".part").exists(),"HTML response leaves no failed media");
  downloader.download("https://fixture.example/good",media,key,0,(n,t)->{});
  check(media.exists()&&!new File(media+".part").exists(),"retry after loader failures succeeds");media.delete();
  VideoDownloader slow=new VideoDownloader(new VideoRuntime(args[0]));ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch receiving=new CountDownLatch(1);
  Future<?> running=worker.submit(()->{try{slow.download("https://fixture.example/slow",media,key,0,(n,t)->receiving.countDown());throw new AssertionError("cancel ignored");}catch(Exception expected){}});
  check(receiving.await(5,TimeUnit.SECONDS),"stream started");slow.cancel();running.get(5,TimeUnit.SECONDS);worker.shutdownNow();check(!media.exists()&&!new File(media+".part").exists(),"cancel stops process and removes partial ciphertext");
  System.out.println("PASS: "+checks+" video core checks");
 }
}
