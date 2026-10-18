package dev.pocketllama.security;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** PIN input is native only; it never crosses a WebView, logging, saved instance state or network. */
public final class LockActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){dev.pocketllama.I18n.initialize(base);super.attachBaseContext(dev.pocketllama.I18n.wrap(base));}
    private final ExecutorService io=Executors.newSingleThreadExecutor();private EditText input;private TextView title,hint;private Button submit;
    private PinStore pins;private char[] first;private int stage;private boolean busy,resumed,changing,verifiedCurrent;private volatile boolean gone;
    @Override public void onCreate(Bundle state){super.onCreate(state);GateSession.gateOpened();getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);pins=new PinStore(this);changing=getIntent().getBooleanExtra("change",false)&&GateSession.isOpen();stage=pins.exists()||Vault.ready(this)?0:1;
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(28),dp(54),dp(28),dp(30));root.setBackgroundColor(Color.rgb(247,246,242));scroll.setBackgroundColor(Color.rgb(247,246,242));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        TextView brand=new TextView(this);brand.setText("Pocket.");brand.setTextSize(30);brand.setTextColor(Color.rgb(37,42,39));brand.setTypeface(null,Typeface.BOLD);root.addView(brand);title=new TextView(this);title.setTextSize(26);title.setTypeface(null,Typeface.BOLD);title.setTextColor(Color.rgb(37,42,39));title.setPadding(0,dp(42),0,dp(12));root.addView(title);
        hint=new TextView(this);hint.setTextSize(15);hint.setTextColor(Color.rgb(90,98,89));hint.setPadding(0,0,0,dp(22));root.addView(hint);
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(30);input.setGravity(Gravity.CENTER);input.setTextColor(Color.rgb(37,42,39));input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});input.setSaveEnabled(false);input.setLongClickable(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);input.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setContentDescription(dev.pocketllama.I18n.t("Код входа, шесть цифр"));root.addView(input,new LinearLayout.LayoutParams(-1,dp(70)));
        submit=new Button(this);submit.setTextColor(Color.rgb(247,246,242));submit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(37,42,39)));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(60));bp.topMargin=dp(24);root.addView(submit,bp);submit.setOnClickListener(v->submit());input.setOnEditorActionListener((v,a,event)->{if(a==EditorInfo.IME_ACTION_DONE){submit();return true;}return false;});
        TextView privacy=new TextView(this);privacy.setText(dev.pocketllama.I18n.t("Код остаётся на телефоне. Сбросить забытый код с сохранением записей нельзя.\n\nПосле включения защиты прежние записи будут перенесены автоматически. Не удаляйте приложение."));privacy.setTextSize(12);privacy.setTextColor(Color.rgb(90,98,89));privacy.setPadding(0,dp(26),0,0);root.addView(privacy);setContentView(scroll);render();
    }
    private boolean canUnlock(long epoch){return resumed&&!isFinishing()&&GateSession.epoch()==epoch&&getSystemService(PowerManager.class).isInteractive();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void render(){title.setText(stage==1?dev.pocketllama.I18n.t("Придумайте код"):stage==2?dev.pocketllama.I18n.t("Повторите код"):changing?dev.pocketllama.I18n.t("Текущий код"):dev.pocketllama.I18n.t("С возвращением"));hint.setText(stage==0?dev.pocketllama.I18n.t("Введите 6 цифр, чтобы открыть Pocket."):dev.pocketllama.I18n.t("Шесть цифр для доступа к вашим записям, фото и чату."));submit.setText(stage==2?dev.pocketllama.I18n.t("Сохранить код"):stage==0?dev.pocketllama.I18n.t("Открыть"):dev.pocketllama.I18n.t("Продолжить"));input.setEnabled(!busy);submit.setEnabled(!busy);}
    private void submit(){if(busy)return;char[] pin=input.getText().toString().toCharArray();input.setText("");if(pin.length!=6){Arrays.fill(pin,'\0');hint.setText(dev.pocketllama.I18n.t("Введите ровно шесть цифр"));return;}
        if(stage==1){if(first!=null)Arrays.fill(first,'\0');first=pin;stage=2;render();input.requestFocus();return;}
        if(stage==2&&!Arrays.equals(first,pin)){Arrays.fill(pin,'\0');Arrays.fill(first,'\0');first=null;stage=1;render();hint.setText(dev.pocketllama.I18n.t("Коды не совпали. Придумайте код ещё раз"));return;}
        busy=true;render();hint.setText(stage==0?dev.pocketllama.I18n.t("Проверяем код…"):dev.pocketllama.I18n.t("Сохраняем защиту…"));input.clearFocus();getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(),0);
        final int action=stage;final long attemptEpoch=GateSession.epoch();io.execute(()->{try{
            if(action==0){if(!pins.verify(pin)){runOnUiThread(()->{if(gone)return;busy=false;render();hint.setText(dev.pocketllama.I18n.t("Неверный код. После повторных ошибок включается задержка."));});return;}
                if(changing&&!verifiedCurrent){runOnUiThread(()->{if(gone)return;busy=false;if(!canUnlock(attemptEpoch)){verifiedCurrent=false;stage=0;render();return;}verifiedCurrent=true;GateSession.unlock();stage=1;render();input.requestFocus();getSystemService(InputMethodManager.class).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);});return;}
            }else{pins.set(pin);if(changing){runOnUiThread(()->{if(!gone){Toast.makeText(this,dev.pocketllama.I18n.t("Код изменён"),Toast.LENGTH_SHORT).show();finish();}});return;}}
            VaultMigration.run(this,text->runOnUiThread(()->{if(!gone)hint.setText(dev.pocketllama.I18n.status(text));}));
            runOnUiThread(()->{if(gone)return;busy=false;if(!canUnlock(attemptEpoch)){stage=0;render();return;}GateSession.unlock();if(!getIntent().getBooleanExtra("return",false))startActivity(new Intent(this,dev.pocketllama.voice.VoiceActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});
        }catch(Exception e){runOnUiThread(()->{if(gone)return;busy=false;stage=pins.exists()||Vault.ready(this)?0:1;verifiedCurrent=false;render();hint.setText(e instanceof java.io.IOException||e instanceof IllegalArgumentException?dev.pocketllama.I18n.status(e.getMessage()):dev.pocketllama.I18n.t("Не удалось открыть защиту. Данные сохранены. Повторите попытку; не удаляйте приложение."));});}
        finally{Arrays.fill(pin,'\0');if(action==2&&first!=null){Arrays.fill(first,'\0');first=null;}}});
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(!busy&&changing&&verifiedCurrent&&!GateSession.isOpen()){verifiedCurrent=false;clearFirst();stage=0;render();}}
    private void clearFirst(){if(first!=null){Arrays.fill(first,'\0');first=null;}}
    @Override protected void onPause(){resumed=false;if(input!=null)input.setText("");if(!busy){clearFirst();if(stage==2)stage=1;render();}super.onPause();}
    @Override public void onBackPressed(){if(!busy){clearFirst();if(changing&&GateSession.isOpen())finish();else{verifiedCurrent=false;stage=pins.exists()||Vault.ready(this)?0:1;render();moveTaskToBack(true);}}}
    @Override protected void onDestroy(){gone=true;GateSession.gateClosed();io.shutdown();if(first!=null)Arrays.fill(first,'\0');super.onDestroy();}
}
