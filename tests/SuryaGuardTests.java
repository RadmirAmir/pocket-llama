import dev.pocketllama.photo.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

public final class SuryaGuardTests {
 static int checks;static void ok(boolean v,String name){if(!v)throw new AssertionError(name);checks++;}
 static void feed(SuryaOutputGuard guard,String s,int chunk)throws Exception {for(int i=0;i<s.length();i+=chunk)guard.append(s.substring(i,Math.min(i+chunk,s.length())));}
 static void repeats(String s,int chunk)throws Exception {try{feed(new SuryaOutputGuard(true),s,chunk);throw new AssertionError("loop accepted");}catch(SuryaOutputGuard.Repetition expected){checks++;}}
 public static void main(String[] args)throws Exception {
  String cycle="Одна и та же строка без нового содержания. ";
  for(int chunk:new int[]{1,7,59,100000})repeats("<div data-bbox='0 0 1000 1000'><p>"+cycle.repeat(100),chunk);
  repeats("<p>"+"a".repeat(2000)+"</p>",13);repeats("<p>"+cycle.repeat(100)+"</p>",100000);
  StringBuilder numbers=new StringBuilder("<table>");for(int i=0;i<180;i++)numbers.append("<tr><td>0</td><td>Нет</td><td>100.00</td></tr>");numbers.append("</table>");
  feed(new SuryaOutputGuard(true),numbers.toString(),7);ok(SafeOcrHtml.parse(numbers.toString()).getJSONArray("tables").getJSONObject(0).getInt("rowCount")==180,"repeated numeric/short text table rows survive");
  feed(new SuryaOutputGuard(false),"<p>"+" ".repeat(3000)+"</p>",1);checks++;
  StringBuilder blocks=new StringBuilder();for(int i=0;i<20;i++)blocks.append("<div data-label='Text' data-bbox='0 ").append(i*40).append(" 1000 ").append(i*40+30).append("'>").append(cycle).append("</div>");
  feed(new SuryaOutputGuard(true),blocks.toString(),1);ok(SuryaPageHtml.parse(blocks.toString(),1000,1000,true).length()==20,"same prose at distinct coordinates is legitimate");
  String one="<div data-label='Text' data-bbox='0 0 1000 200'><p>Готовый текст</p></div>";
  try{SuryaPageHtml.parse(one+one,1000,1000,true);throw new AssertionError("duplicate coordinates accepted");}catch(SuryaPageHtml.RepetitionException e){ok(e.completed.length()==1,"duplicate region is excluded from checkpoint");}
  try{SuryaPageHtml.parse(one+"<div data-label='Text' data-bbox='0 300 1000 900'>"+cycle.repeat(100)+"</div>",1000,1000,true);throw new AssertionError("completed loop accepted");}catch(SuryaPageHtml.RepetitionException e){ok(e.completed.length()==1,"loop inside a closed block never becomes complete");}
  JSONArray old=SuryaPageHtml.parse(one,1000,1000,true),layout=SuryaProtocol.parse("[{\"label\":\"Text\",\"bbox\":\"0 0 1000 200\"}]",false,1000,1000);old.getJSONObject(0).put("html","<p>"+cycle.repeat(100)+"</p>");SuryaPageHtml.reuse(layout,old);ok(!layout.getJSONObject(0).getBoolean("complete"),"old looping checkpoints are not reused as finished content");
  try{SuryaProtocol.parse("[{\"label\":\"Text\",\"bbox\":\"0 0 1000 1000\"},{\"label\":\"Text\",\"bbox\":\"0 0 1000 1000\"}]",false,1000,1000);throw new AssertionError("duplicate layout accepted");}catch(java.io.IOException expected){checks++;}
  AtomicInteger cancels=new AtomicInteger();CountDownLatch cancelled=new CountDownLatch(1);
  try(SuryaDeadline deadline=new SuryaDeadline(50,()->{cancels.incrementAndGet();cancelled.countDown();})){ok(cancelled.await(2,TimeUnit.SECONDS)&&deadline.expired(),"wall-clock bound fires without any stream callbacks");}
  CountDownLatch late=new CountDownLatch(1);SuryaDeadline finished=new SuryaDeadline(100,late::countDown);finished.close();ok(!late.await(160,TimeUnit.MILLISECONDS)&&!finished.expired(),"finished request cancels its watchdog");ok(cancels.get()==1,"watchdog cancels once");
  System.out.println("SuryaGuardTests: "+checks+" checks passed");
 }
}
