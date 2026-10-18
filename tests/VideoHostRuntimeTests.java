import android.content.Context;
import dev.pocketllama.video.*;
import dev.pocketllama.security.ChunkedFile;
import org.json.JSONObject;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Executes production VideoRuntime, launcher, pinned yt-dlp and VideoDownloader. */
public final class VideoHostRuntimeTests {
 private static int checks;
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 private static String hex(byte[] value){StringBuilder s=new StringBuilder();for(byte b:value)s.append(String.format("%02x",b&255));return s.toString();}
 public static void main(String[] args)throws Exception {
  File root=new File(args[0]);Context context=new Context(new File(root,"private"),new File(root,"native"),new File(args[1]));
  VideoRuntime runtime=VideoRuntime.prepare(context);
  Process version=runtime.start(Collections.singletonList("--version"));
  check(version.waitFor(20,TimeUnit.SECONDS),"loader startup timeout");
  String output=new String(version.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
  String error=new String(version.getErrorStream().readAllBytes(),StandardCharsets.UTF_8).trim();
  check(version.exitValue()==0,"production loader arguments rejected: "+error);
  check(output.equals("2026.08.19"),"pinned yt-dlp actually started");
  VideoDownloader downloader=new VideoDownloader(runtime);JSONObject info=downloader.inspect(args[2],(n,t)->{});
  check(!info.optString("title").isEmpty(),"real extractor metadata");
  if(args.length>4)check(info.optString("id").equals(args[4]),"requested YouTube Shorts ID");
  File media=new File(root,"download.media");SecretKeySpec key=new SecretKeySpec(new byte[32],"AES");
  downloader.download(args[2],media,key,info.optLong("filesize",0),(n,t)->{});
  MessageDigest hash=MessageDigest.getInstance("SHA-256");long size;
  try(ChunkedFile encrypted=new ChunkedFile(media,false,key)){
   size=encrypted.length();check(size>=1024,"real yt-dlp audio bytes received");
   byte[] bytes=new byte[65536];long at=0;int n;while((n=encrypted.read(at,bytes,0,bytes.length))>0){hash.update(bytes,0,n);at+=n;}Arrays.fill(bytes,(byte)0);
  }
  if(!args[3].equals("-"))check(hex(hash.digest()).equals(args[3]),"real downloader stdout encrypted without byte changes");
  check(!new File(media+".part").exists(),"download committed");
  File temp=new File(context.getNoBackupFilesDir(),"video-runtime-v1/tmp");
  check(temp.list()!=null&&temp.list().length==0,"runtime created no plaintext media, metadata or JS files");
  System.out.println("PASS: "+checks+" real-loader host checks; extractor="+info.optString("extractor_key")+"; duration="+info.optDouble("duration")+"; audio="+size+" bytes");
 }
}
