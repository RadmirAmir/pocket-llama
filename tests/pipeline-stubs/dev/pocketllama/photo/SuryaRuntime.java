package dev.pocketllama.photo;
import android.content.Context;
import android.graphics.Bitmap;
import java.io.*;
import java.util.*;
import org.json.JSONObject;
/** Scripted transport only; the pipeline, parsers, stores and encryption are production classes. */
public final class SuryaRuntime implements AutoCloseable {
 public interface Check {void run()throws Exception;}public interface Output {void chunk(String text)throws Exception;}public interface Status {void update(String stage,String message)throws Exception;}
 public static final class Incomplete extends IOException {public final String reason;public Incomplete(String reason){super(reason);this.reason=reason;}}
 public static final class Step {final String value;final Exception error;public Step(String value,Exception error){this.value=value;this.error=error;}}
 public static Runnable beforeRequest,onInput;public static int loads,closes;public static final List<SuryaImageSize.Task> calls=new ArrayList<>();public static final Deque<Step> steps=new ArrayDeque<>();
 public static void reset(){beforeRequest=onInput=null;loads=closes=SuryaDetector.calls=0;calls.clear();steps.clear();}
 public static void reply(String text){steps.add(new Step(text,null));}public static void fail(String text,Exception error){steps.add(new Step(text,error));}
 public SuryaRuntime(Context c,File file,Check check)throws Exception{check.run();loads++;}public long loadMs(){return 1;}
 public JSONObject metrics(){return new JSONObject();}public void cancel(){}public void close(){closes++;}
 public String request(Bitmap image,String prompt,boolean structured,boolean table,int tokens,SuryaImageSize.Task task,Output output)throws Exception {
  return request(image,prompt,structured,table,tokens,task,output,null);
 }
 public String request(Bitmap image,String prompt,boolean structured,boolean table,int tokens,SuryaImageSize.Task task,Output output,Status status)throws Exception {
  calls.add(task);if(beforeRequest!=null)beforeRequest.run();Step step=steps.remove();
  if(status!=null){status.update("prepare","Подготавливаем изображение");status.update("input",SuryaInputProgress.message(null));status.update("input",SuryaInputProgress.message(new JSONObject().put("total",2000).put("processed",1000)));}
  if(onInput!=null)onInput.run();if(status!=null)status.update("text","Распознаём текст");if(output!=null)output.chunk(step.value);if(step.error!=null)throw step.error;return step.value;
 }
}
