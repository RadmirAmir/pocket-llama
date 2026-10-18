package dev.pocketllama.photo;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.FrameLayout;
import dev.pocketllama.*;
import dev.pocketllama.voice.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Local document shell. Authenticated private image routes; no image-to-JavaScript bridge. */
public final class PhotoActivity extends dev.pocketllama.security.SecureActivity {
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();private WebView web;private volatile PhotoStore store;private DocumentStore docs;private volatile boolean destroyed,ready;private String selected="",importDocument="",exportDocument="",exportFormat="";private volatile boolean importing;
    private final String imageToken=UUID.randomUUID().toString();
    @Override protected void onSecureCreate(Bundle state){getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);selected=state==null?getIntent().getStringExtra("id"):state.getString("id","");if(state!=null){importDocument=state.getString("importDocument","");exportDocument=state.getString("exportDocument","");exportFormat=state.getString("exportFormat","");}
        FrameLayout root=new FrameLayout(this);web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);UiHost.attach(this,root,web,this::image);web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);web.getSettings().setSupportZoom(true);web.getSettings().setBuiltInZoomControls(true);web.getSettings().setDisplayZoomControls(false);web.getSettings().setSaveFormData(false);web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);web.addJavascriptInterface(new Bridge(),"Photos");web.loadUrl(UiHost.ORIGIN+"photos.html");}
    public final class Bridge {
        @JavascriptInterface public String appearance(){return UiHost.appearance(PhotoActivity.this);}
        @JavascriptInterface public void ready(){if(!dev.pocketllama.security.GateSession.isOpen())return;ready=true;io.execute(()->{try{store=new PhotoStore(PhotoActivity.this);docs=new DocumentStore(PhotoActivity.this,DistributionConfig.surya(PhotoActivity.this)?"surya":"paddle");store.recover();docs.recover();if(selected!=null&&!selected.isEmpty())selected=docs.resolve(selected);emit("boot",object("id",selected==null?"":selected,"token",imageToken,"model",new SuryaAssets(PhotoActivity.this).status()));}catch(Exception e){error(e);}});}
        @JavascriptInterface public void call(String request,String action,String payload){if(!dev.pocketllama.security.GateSession.isOpen()){reject(request,new IOException("Введите код входа"));return;}if(destroyed||request==null||request.length()>80||payload==null||payload.length()>1600000)return;io.execute(()->{try{if(store==null||docs==null)throw new IOException("Хранилище ещё открывается");reply(request,act(action,new JSONObject(payload)));}catch(Exception e){reject(request,e);}catch(OutOfMemoryError e){reject(request,new IOException("Недостаточно памяти для страницы"));}catch(LinkageError e){reject(request,new IOException("Библиотека сканера недоступна. Установите APK поверх текущей версии"));}});}
    }
    private JSONObject act(String action,JSONObject a)throws Exception {
        String id=a.optString("id"),page=a.optString("page");switch(action){
            case "list":store.recover();docs.recover();return docs.list(a.optString("query").substring(0,Math.min(200,a.optString("query").length())),a.optInt("limit",100));
            case "detail":selected=docs.resolve(id);store.recover();docs.recover();return docs.detail(selected).put("model",new SuryaAssets(this).status());
            case "page":member(id,page,false);JSONObject p=store.get(page);int[] dims=ScanImages.dimensions(p);return p.put("renderWidth",dims[0]).put("renderHeight",dims[1]);
            case "rename":docs.rename(id,a.optString("title"));return docs.detail(id);
            case "engine":idle(id);if(a.optString("engine").equals("surya")&&!DistributionConfig.surya(this))throw new IOException("Расширенный OCR недоступен в этой сборке");docs.engine(id,a.optString("engine"));return docs.detail(id);
            case "reorder":idle(id);docs.reorder(id,a.getJSONArray("pages"));return docs.detail(id);
            case "edit":member(id,page,true);store.edit(page,a.optString("text"));return object("ok",true);
            case "rotate":member(id,page,true);store.rotate(page);docs.dirty(id);return object("ok",true);
            case "scan":member(id,page,true);store.scan(page,a.getJSONArray("corners"),a.optString("filter","color"));docs.dirty(id);return object("ok",true);
            case "detect":member(id,page,true);Bitmap b=PhotoImages.decode(store.image(page,false),900);try{double[] q=DocumentEdges.detect(b);return object("found",q!=null,"corners",ScanGeometry.json(q==null?ScanGeometry.full():q));}finally{b.recycle();}
            case "deletePage":member(id,page,true);docs.removePage(id,page);return docs.detail(id);
            case "delete":idle(id);docs.delete(id);selected="";return object("ok",true);
            case "process":case "details":{
                idle(id);if(importing)throw new IOException("Дождитесь импорта страниц");try(VoiceStore voices=new VoiceStore(this)){if(voices.anyBusy()||VoiceState.busy)throw new IOException("Сначала завершите запись или обработку речи");}JSONObject d=docs.get(id);JSONArray pages=d.getJSONArray("pages");if(pages.length()==0)throw new IOException("Добавьте страницы документа");
                boolean details=action.equals("details")||(!a.optBoolean("restart")&&d.optString("state").equals("interrupted")&&d.optString("task").equals("details"));String target=action.equals("details")?page:d.optString("taskPage");
                if(details){member(id,target,false);JSONObject candidate=store.get(target),analysis=candidate.optJSONObject("analysis");if(!candidate.optString("engine").equals("surya")||analysis==null||analysis.optBoolean("pagePending")||analysis.optJSONArray("blocks")==null)throw new IOException("Сначала завершите распознавание страницы в Surya");JSONArray blocks=analysis.getJSONArray("blocks");for(int i=0;i<blocks.length();i++)if(!blocks.getJSONObject(i).optBoolean("complete"))throw new IOException("Сначала завершите распознавание областей");}
                else if(!a.optBoolean("replaceEdited"))for(int i=0;i<pages.length();i++)if(store.get(pages.getString(i)).optBoolean("edited"))throw new IOException("Подтвердите замену ручных исправлений перед распознаванием");
                store.reserveJob(details?target:pages.getString(0));try{if(!details&&a.optBoolean("restart"))for(int i=0;i<pages.length();i++)store.resetRecognition(pages.getString(i));docs.claim(id,details?"details":"ocr",target);}catch(Exception e){store.finishJob();throw e;}
                main.post(()->{try{startForegroundService(new Intent(this,OcrService.class).setAction(OcrService.START).putExtra("id",id));}catch(Exception e){io.execute(()->{try{store.finishJob();docs.progress(id,"interrupted","Не удалось начать OCR. Повторите распознавание.",0);}catch(Exception ignored){}error(e);});}});return docs.detail(id);
            }
            case "cancel":main.post(()->startService(new Intent(this,OcrService.class).setAction(OcrService.CANCEL)));break;
            case "camera":if(!id.isEmpty())idle(id);main.post(()->startActivityForResult(new Intent(this,PhotoCameraActivity.class).putExtra("id",id),30));break;
            case "import":if(!id.isEmpty())idle(id);importDocument=id;main.post(()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf"}).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).putExtra(Intent.EXTRA_LOCAL_ONLY,true),31));break;
            case "export":{
                idle(id);String format=a.optString("format");if(!Arrays.asList("pdf","txt","md","json","xlsx").contains(format))throw new IOException("Неизвестный формат экспорта");exportDocument=id;exportFormat=format;String title=docs.get(id).optString("title","Документ").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");String mime=format.equals("pdf")?"application/pdf":format.equals("xlsx")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":format.equals("json")?"application/json":"text/plain";main.post(()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,title+"."+format),32));break;
            }
            case "copy":{
                String value;if(page.isEmpty()){JSONObject d=docs.get(id);StringBuilder s=new StringBuilder();JSONArray pages=d.getJSONArray("pages");for(int i=0;i<pages.length();i++){if(s.length()>0)s.append("\n\n");s.append(store.get(pages.getString(i)).optString("text"));if(s.length()>1000000)throw new IOException("Текст слишком большой для буфера. Экспортируйте TXT");}value=s.toString();}else{member(id,page,false);value=store.get(page).optString("text");}
                final String text=value;main.post(()->{if(!dev.pocketllama.security.GateSession.isOpen())return;ClipData clip=ClipData.newPlainText("Pocket · документ",text);if(Build.VERSION.SDK_INT>=33){PersistableBundle extras=new PersistableBundle();extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true);clip.getDescription().setExtras(extras);}getSystemService(ClipboardManager.class).setPrimaryClip(clip);});break;
            }
            case "back":main.post(this::finish);break;
            default:throw new IOException("Неизвестное действие");
        }return object("ok",true);
    }
    private void idle(String id)throws Exception {if(docs.get(id).optString("state").equals("processing"))throw new IOException("Сначала остановите распознавание документа");}
    private void member(String id,String page,boolean edit)throws Exception {JSONObject d=docs.get(id);if(edit)idle(id);JSONArray pages=d.getJSONArray("pages");for(int i=0;i<pages.length();i++)if(pages.getString(i).equals(page))return;throw new IOException("Страница не относится к документу");}
    private WebResourceResponse image(String url){try{
        String prefix=UiHost.ORIGIN+"private/"+imageToken+"/";if(!dev.pocketllama.security.GateSession.isOpen()||!url.startsWith(prefix)||store==null)throw new IOException("Закрыто");Matcher m=Pattern.compile("([a-f0-9-]{36})/(thumb|photo|original)\\.jpg(?:\\?.*)?").matcher(url.substring(prefix.length()));if(!m.matches())throw new IOException("Не найдено");String id=m.group(1),kind=m.group(2);JSONObject p=store.get(id);byte[] bytes;
        if(kind.equals("original"))bytes=PhotoImages.preview(store.image(id,false),0,1800);else{Bitmap b=ScanImages.render(store.image(id,kind.equals("thumb")),p,kind.equals("thumb")?480:1800);try{bytes=ScanImages.jpeg(b,88);}finally{b.recycle();}}
        Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store, max-age=0");headers.put("Pragma","no-cache");headers.put("X-Content-Type-Options","nosniff");return new WebResourceResponse("image/jpeg",null,200,"OK",headers,new ByteArrayInputStream(bytes));
    }catch(Exception|OutOfMemoryError|LinkageError e){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(new byte[0]));}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(deferResult(()->onActivityResult(request,result,data)))return;if(result!=RESULT_OK||data==null)return;
        if(request==30){String id=data.getStringExtra("id");if(id!=null){selected=id;emit("open",object("id",id));}}
        if(request==31){List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){ClipData clip=data.getClipData();for(int i=0;i<Math.min(51,clip.getItemCount());i++)uris.add(clip.getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());String target=importDocument;importing=true;emit("importing",object("active",true));io.execute(()->{String doc=target;try{if(doc.isEmpty())doc=docs.create();idle(doc);int[] count={docs.get(doc).getJSONArray("pages").length()};for(Uri uri:uris){if(count[0]>=50)throw new IOException("Добавлены первые 50 страниц. Остальные можно импортировать в новый документ");String mime=getContentResolver().getType(uri);if("application/pdf".equals(mime))importPdf(doc,uri,count);else{String page=PhotoImages.importUri(this,uri);docs.add(doc,page);count[0]++;}emit("importing",object("active",true,"count",count[0]));}}catch(Exception e){error(e);}catch(OutOfMemoryError e){error(new IOException("Недостаточно памяти. Уже импортированные страницы сохранены"));}finally{importing=false;selected=doc;emit("importing",object("active",false));if(!doc.isEmpty())emit("open",object("id",doc));}});}
        if(request==32&&data.getData()!=null){Uri uri=data.getData();String id=exportDocument,format=exportFormat;emit("exporting",object("active",true));io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Не удалось открыть файл");idle(id);ScanExports.write(this,id,format,out);emit("exported",object("format",format));}catch(Exception|OutOfMemoryError e){try{getContentResolver().delete(uri,null,null);}catch(Exception ignored){}error(new IOException("Экспорт не завершён. Повторите после освобождения памяти или выберите другой файл"));}finally{emit("exporting",object("active",false));}});}
    }
    private void importPdf(String doc,Uri uri,int[] count)throws Exception {try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r")){if(fd==null)throw new IOException("PDF недоступен");if(fd.getStatSize()>128L*1024*1024)throw new IOException("PDF больше 128 МБ. Разделите его на несколько документов");try(PdfRenderer pdf=new PdfRenderer(fd)){for(int i=0;i<pdf.getPageCount();i++){if(count[0]>=50)throw new IOException("Добавлены первые 50 страниц. Разделите PDF на несколько документов");try(PdfRenderer.Page page=pdf.openPage(i)){double scale=Math.min(3,Math.min(4096./Math.max(page.getWidth(),page.getHeight()),Math.sqrt(10000000./((double)page.getWidth()*page.getHeight()))));Bitmap b=Bitmap.createBitmap(Math.max(1,(int)Math.round(page.getWidth()*scale)),Math.max(1,(int)Math.round(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);try{b.eraseColor(Color.WHITE);page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);String id=PhotoImages.save(this,ScanImages.jpeg(b,95));docs.add(doc,id);count[0]++;}finally{b.recycle();}}emit("importing",object("active",true,"count",count[0]));}}}}
    private static JSONObject object(Object... p){JSONObject j=new JSONObject();try{for(int i=0;i<p.length;i+=2)j.put((String)p[i],p[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return j;}
    private void reply(String request,JSONObject value){emit("reply",object("request",request,"value",value));}private void reject(String request,Exception e){emit("reply",object("request",request,"error",useful(e)));}private void error(Exception e){emit("error",object("message",useful(e)));}private static String useful(Exception e){return e instanceof IOException||e instanceof IllegalArgumentException?e.getMessage():"Не удалось открыть защищённые данные. Повторите действие; не удаляйте приложение.";}
    private void emit(String type,JSONObject data){if(destroyed||!ready)return;String json=object("type",type,"data",data).toString();main.post(()->{if(!destroyed)web.evaluateJavascript("window.PhotoEvents&&window.PhotoEvents("+json+")",null);});}
    @Override protected void onResume(){super.onResume();if(!secureInitialized()||!dev.pocketllama.security.GateSession.isOpen())return;if(web!=null)web.onResume();emit("resume",new JSONObject());}
    @Override protected void onPause(){if(!secureInitialized()){super.onPause();return;}emit("pause",new JSONObject());if(web!=null)web.onPause();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("id",selected);b.putString("importDocument",importDocument);b.putString("exportDocument",exportDocument);b.putString("exportFormat",exportFormat);}
    @Override public void onBackPressed(){if(web==null||!dev.pocketllama.security.GateSession.isOpen()){lockNow();return;}web.evaluateJavascript("window.PhotoBack?window.PhotoBack():false",v->{if(!"true".equals(v))finish();});}
    @Override protected void onDestroy(){destroyed=true;io.shutdown();if(web!=null){web.removeJavascriptInterface("Photos");web.clearCache(true);web.destroy();}super.onDestroy();}
}
