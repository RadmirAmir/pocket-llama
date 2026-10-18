import dev.pocketllama.photo.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
public final class ScannerCoreTests {
 static int checks;interface Work{void run()throws Exception;}static void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}static void fails(Work w)throws Exception{try{w.run();throw new AssertionError("must fail");}catch(IOException|IllegalArgumentException|JSONException e){checks++;}}
 public static void main(String[] args)throws Exception {
  ScanGeometry.validate(ScanGeometry.full());ok(Math.abs(ScanGeometry.area(ScanGeometry.full())-1)<1e-12,"full area");ok(Arrays.equals(ScanGeometry.size(ScanGeometry.full(),3000,4000),new int[]{3000,4000}),"full size");ok(ScanGeometry.size(ScanGeometry.full(),12000,16000)[1]==4000,"pixel bound");
  double[] q={.1,.1,.9,.15,.85,.9,.15,.85};ScanGeometry.validate(q);double[] unordered={q[4],q[5],q[0],q[1],q[6],q[7],q[2],q[3]};ok(ScanGeometry.distance(ScanGeometry.order(unordered),q)<1e-12,"clockwise ordering");
  fails(()->ScanGeometry.validate(new double[]{0,0,1,1,1,0,0,1}));fails(()->ScanGeometry.validate(new double[]{0,0,-.1,0,1,1,0,1}));fails(()->ScanGeometry.validate(new double[]{0,0,Double.NaN,0,1,1,0,1}));fails(()->ScanGeometry.read(new JSONArray("[0,0,1]")));
  JSONArray layout=SuryaProtocol.parse("[{\"label\":\"Table\",\"bbox\":\"10 20 900 800\",\"count\":120}]",false,2000,1000);JSONObject b=layout.getJSONObject(0);ok(b.getJSONArray("bbox").getDouble(0)==20&&b.getJSONArray("bbox").getDouble(3)==800,"coordinate projection");ok(b.getJSONArray("box").length()==8&&!b.getBoolean("complete"),"quadrilateral checkpoint");
  fails(()->SuryaProtocol.parse("[{\"label\":\"Text\",\"bbox\":\"-1 0 10 10\"}]",false,20,20));fails(()->SuryaProtocol.parse("[{\"label\":\"Text\",\"bbox\":\"0 0 1001 50\"}]",false,20,20));fails(()->SuryaProtocol.parse("[{\"label\":\"Text\",\"bbox\":\"10 10 1 1\"}]",false,20,20));fails(()->SuryaProtocol.parse("[{\"label\":\"script\",\"bbox\":\"0 0 10 10\"}]",false,20,20));fails(()->SuryaProtocol.parse("[{\"label\":\"Text\",\"bbox\":\"0 0 10 10\"}]",true,20,20));
  fails(()->SafeOcrHtml.parse("<div>".repeat(200)+"x"+"</div>".repeat(200)));
  ok(SuryaProtocol.tokenBudget(0)==256&&SuryaProtocol.tokenBudget(9999)==4096,"bounded first-pass token budget");
  ok(SuryaProtocol.schema(false).getJSONObject("items").getJSONArray("required").length()==3,"strict layout schema");ok(SuryaProtocol.visual("Diagram")&&!SuryaProtocol.visual("Table"),"visual skipping");
  JSONObject html=SafeOcrHtml.parse("<h2 onclick='steal()'>Счёт № 7</h2><script>secret</script><img src='https://bad/' onerror='steal()'><a href='javascript:steal()'>Текст</a><table><tr><th rowspan='2'>Товар</th><th colspan='2'>Цена</th></tr><tr><td>500 ₽</td><td>=HYPERLINK(\"https://bad\")</td></tr></table><input checked><input>");
  ok(!html.getString("html").contains("onclick")&&!html.getString("html").contains("javascript:")&&!html.getString("html").contains("https://bad/"),"no executable HTML or image URL");ok(!html.getString("text").contains("secret")&&html.getString("text").contains("☑☐"),"script removed and checkboxes preserved");JSONObject table=html.getJSONArray("tables").getJSONObject(0);ok(table.getInt("rowCount")==2&&table.getInt("colCount")==3,"table shape");ok(table.getJSONArray("cells").getJSONObject(0).getInt("rowspan")==2,"rowspan retained");ok(table.getJSONArray("cells").getJSONObject(1).getInt("colspan")==2,"colspan retained");
  fails(()->SafeOcrHtml.parse("<table><tr><td rowspan=200>x</td></tr>"+"<tr><td>y</td></tr>".repeat(200)+"</table>"));
  String pageHtml="<div data-bbox='10 20 900 800' data-label='Table'>"+html.getString("html")+"</div>";
  JSONArray full=SuryaPageHtml.parse(pageHtml,2000,1000,true);JSONObject fullBlock=full.getJSONObject(0);
  ok(fullBlock.getBoolean("complete")&&fullBlock.getJSONArray("bbox").getDouble(0)==20,"full-page coordinates and completion");
  ok(fullBlock.getJSONArray("tables").getJSONObject(0).getJSONArray("cells").getJSONObject(0).getInt("rowspan")==2,"full-page table spans survive sanitation");
  ok(!fullBlock.getString("html").contains("data-bbox")&&!fullBlock.getString("html").contains("onclick"),"layout attributes are parsed separately from safe content");
  String partial=pageHtml+"<div data-label='Text' data-bbox='0 800 1000 1000'>not finished";
  ok(SuryaPageHtml.parse(partial,2000,1000,false).length()==1,"stream checkpoint omits an incomplete final block");fails(()->SuryaPageHtml.parse(partial,2000,1000,true));
  ok(SuryaPageHtml.parse("<div data-label='Text' data-bbox='0 0 1000 1000'>unfinished",900,1200,false).length()==0,"HTML repair is never mistaken for completion");
  fails(()->SuryaPageHtml.parse("<div>unattributed</div>",900,1200,true));fails(()->SuryaPageHtml.parse("unexpected"+pageHtml,900,1200,true));fails(()->SuryaPageHtml.parse(pageHtml+"unexpected",900,1200,true));
  fails(()->SuryaPageHtml.parse(pageHtml.replace("10 20 900 800","0 0 1001 1000"),900,1200,true));fails(()->SuryaPageHtml.parse("",900,1200,true));
  ok(SuryaPageHtml.parse("```html\n"+pageHtml+"\n```",900,1200,true).length()==1,"full-page markdown fence");
  ok(SuryaPageHtml.parse("<div data-bbox='[0,0,1000,1000]' data-label='SectionHeader'><div><p>Заголовок</p></div></div>",900,1200,true).getJSONObject(0).getString("label").equals("Section-Header"),"nested HTML and canonical label alias");
  ok(SuryaPageHtml.parse("<div data-bbox='0 0 1000 1000' data-label='BlankPage'></div>",900,1200,true).getJSONObject(0).getString("text").isEmpty(),"explicit blank page");
  JSONArray matching=SuryaProtocol.parse("[{\"label\":\"Table\",\"bbox\":\"10 20 900 800\",\"count\":200}]",false,2000,1000);SuryaPageHtml.reuse(matching,full);ok(matching.getJSONObject(0).getBoolean("complete"),"fallback reuses identical completed geometry");
  JSONArray separate=SuryaProtocol.parse("[{\"label\":\"Text\",\"bbox\":\"10 20 900 800\",\"count\":200}]",false,2000,1000);SuryaPageHtml.reuse(separate,full);ok(!separate.getJSONObject(0).getBoolean("complete"),"fallback does not reuse a different semantic type");
  for(int[] size:new int[][]{{4000,3000},{80,20},{1000000,1},{1,1000000}}){int[] fit=SuryaImageSize.fit(size[0],size[1],SuryaImageSize.Task.PAGE);ok(fit[0]>=28&&fit[1]>=28&&fit[0]%28==0&&fit[1]%28==0&&(long)fit[0]*fit[1]<=2097152&&Math.max(fit[0],fit[1])<=8192,"bounded adaptive image: "+Arrays.toString(size));}
  int[] tiny=SuryaImageSize.fit(610,70,SuryaImageSize.Task.BLOCK);ok((long)tiny[0]*tiny[1]<100000,"small crop avoids a page-sized image");
  ok(SuryaInputProgress.message(null).equals("Обрабатываем изображение"),"no invented progress without native data");
  ok(SuryaInputProgress.message(new JSONObject().put("total",2000).put("processed",500)).contains("25%"),"real native input percentage");
  ok(SuryaInputProgress.message(new JSONObject().put("total",2000).put("processed",2000)).contains("100% подготовки"),"100 percent input is preparation, not finished OCR");
  for(JSONObject bad:Arrays.asList(new JSONObject(),new JSONObject().put("total",0).put("processed",1),new JSONObject().put("total",10).put("processed",20),new JSONObject().put("total",10).put("processed",-1)))ok(!SuryaInputProgress.message(bad).contains("%"),"invalid input count does not produce a percentage");
  Path out=Path.of(args[0]);Files.createDirectories(out);try(OutputStream f=Files.newOutputStream(out.resolve("tables.xlsx"))){ScanXlsx.write(f,Arrays.asList(table));}
  byte[] jpeg=Files.readAllBytes(Path.of(args[1])),font=Files.readAllBytes(Path.of(args[2]));JSONArray lines=new JSONArray().put(new JSONObject().put("text","Счёт № 7\nБумага: 500 рублей\nInvoice / Test").put("box",new JSONArray("[50,100,800,100,800,500,50,500]")));
  try(OutputStream f=Files.newOutputStream(out.resolve("document.pdf"))){new ScanPdf(f,font).write(new ScanPdf.Source(){public int count(){return 2;}public ScanPdf.Page page(int i){ScanPdf.Page p=new ScanPdf.Page();p.jpeg=jpeg;p.width=900;p.height=1200;p.coordinateWidth=900;p.coordinateHeight=1200;p.lines=lines;return p;}});}
  ok(Files.size(out.resolve("document.pdf"))>jpeg.length*2,"stream PDF");System.out.println("ScannerCoreTests: "+checks+" checks passed");
 }
}
