package dev.pocketllama.installer;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local installer for the exact public APK; no network or vault access. */
public final class InstallActivity extends Activity {
    static final String EXPECTED_SHA="8cfccbfed0c38e2c53d8f05ae1ecf2dc66522ba7252221d2f133d17c18cc8302";
    static final long EXPECTED_BYTES=640818190L;
    static final AtomicBoolean COPYING=new AtomicBoolean();
    static final String PREFS="installer-result";
    private TextView status;
    private Button choose;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=(int)(24*getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);scroll.addView(box);
        TextView title=new TextView(this);title.setText("Pocket Community");title.setTextSize(28);box.addView(title);
        TextView intro=new TextView(this);intro.setText("Установка и точная диагностика\n\nВыберите уже скачанный PocketLlama-0.9.0-community-arm64.apk. Сначала проверим целостность файла, затем Android предложит установку.\n\nУстановщик не читает записи и переписку, не удаляет приложения и не отправляет данные в сеть.");intro.setTextSize(16);intro.setPadding(0,pad,0,pad);box.addView(intro);
        choose=new Button(this);choose.setText("Выбрать community APK");choose.setOnClickListener(v->select());box.addView(choose);
        status=new TextView(this);status.setTextSize(15);status.setTextIsSelectable(true);status.setPadding(0,pad,0,pad);box.addView(status);
        Button copy=new Button(this);copy.setText("Копировать результат");copy.setOnClickListener(v->{android.content.ClipboardManager clip=getSystemService(android.content.ClipboardManager.class);clip.setPrimaryClip(ClipData.newPlainText("Pocket installation",report()));Toast.makeText(this,"Результат скопирован",Toast.LENGTH_SHORT).show();});box.addView(copy);
        Button open=new Button(this);open.setText("Открыть Pocket");open.setOnClickListener(v->{Intent intent=getPackageManager().getLaunchIntentForPackage("dev.pocketllama");if(intent==null)Toast.makeText(this,"Community пока не установлен",Toast.LENGTH_SHORT).show();else startActivity(intent);});box.addView(open);
        setContentView(scroll);render();
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void render(){if(status!=null){String text=report();if(!text.contentEquals(status.getText()))status.setText(text);}if(choose!=null)choose.setEnabled(!COPYING.get());}
    private String report(){return getSharedPreferences(PREFS,0).getString("message","Готов к проверке. Нажмите «Выбрать community APK».")+"\n\n"+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT+installed();}
    @SuppressWarnings("deprecation") private String installed(){
        try {
            PackageInfo info=getPackageManager().getPackageInfo("dev.pocketllama",Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
            android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;
            StringBuilder certificate=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()))certificate.append(String.format(Locale.ROOT,"%02x",b&255));
            long version=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
            return "\nУже установлен dev.pocketllama: "+info.versionName+" ("+version+").\nПодпись установленного APK: "+certificate+"\nОжидаемая подпись community: ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0";
        }catch(PackageManager.NameNotFoundException missing){return "\nПакет community dev.pocketllama пока не установлен.";}catch(Exception e){return "\nНе удалось прочитать сведения об установленной версии: "+e.getMessage();}
    }
    private void select(){
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("Разрешение Android").setMessage("Разрешите установку приложений для Pocket Installer. Затем вернитесь сюда и снова нажмите «Выбрать community APK».").setPositiveButton("Открыть настройки",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())))).setNegativeButton("Отмена",null).show();return;
        }
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        pick.putExtra(DocumentsContract.EXTRA_INITIAL_URI,Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"));
        startActivityForResult(pick,10);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==10&&result==RESULT_OK&&data!=null&&data.getData()!=null&&COPYING.compareAndSet(false,true)){
            Uri uri=data.getData();message("Проверяем APK и передаём его Android. Оставьте установщик открытым…");
            new Thread(()->install(uri),"pocket-apk-install").start();
        }
    }
    void message(String text){getSharedPreferences(PREFS,0).edit().putString("message",text).apply();}
    private void install(Uri uri){
        PackageInstaller installer=getPackageManager().getPackageInstaller();int id=-1;
        try {
            PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName("dev.pocketllama");params.setAppLabel("Pocket Community");params.setSize(EXPECTED_BYTES);
            if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            if(Build.VERSION.SDK_INT>=33)params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE);
            id=installer.createSession(params);
            try(PackageInstaller.Session session=installer.openSession(id)){
                MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0,last=0;
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=session.openWrite("base.apk",0,EXPECTED_BYTES)){
                    if(in==null)throw new IOException("Не удалось открыть выбранный файл");
                    byte[] buffer=new byte[1024*1024];int n;
                    while((n=in.read(buffer))!=-1){count+=n;if(count>EXPECTED_BYTES)throw new IOException("Выбран другой файл: размер превышает размер community 0.9.0");out.write(buffer,0,n);digest.update(buffer,0,n);long now=SystemClock.elapsedRealtime();if(now-last>700){message("Проверяем community APK · "+(count*100/EXPECTED_BYTES)+"%\nОставьте установщик открытым…");last=now;}}
                    session.fsync(out);
                }
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
                if(count!=EXPECTED_BYTES||!EXPECTED_SHA.contentEquals(hash))throw new IOException("Файл отличается от опубликованного community 0.9.0. Скачайте его заново с GitHub.\nРазмер: "+count+"; ожидается "+EXPECTED_BYTES+".\nSHA-256: "+hash);
                message("Целостность APK подтверждена. Ожидаем подтверждение и результат Android…");
                Intent result=new Intent(this,InstallResult.class).setAction("dev.pocketllama.installer.RESULT");
                int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                session.commit(PendingIntent.getBroadcast(this,id,result,flags).getIntentSender());id=-1;
            }
        }catch(Exception e){message("Подготовка установки не завершена:\n"+e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{if(id>=0)try{installer.abandonSession(id);}catch(Exception ignored){}COPYING.set(false);}
    }
}
