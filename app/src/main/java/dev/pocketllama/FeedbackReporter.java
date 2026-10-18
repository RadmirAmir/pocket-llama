package dev.pocketllama;

import android.app.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import org.json.*;

/** Reports are sent only after the user reviews an editable excerpt and presses Send. */
public final class FeedbackReporter {
    private FeedbackReporter(){}
    public static void show(Activity activity,Executor io,String messageId)throws Exception {
        dev.pocketllama.security.GateSession.requireOpen();
        String endpoint=DistributionConfig.get(activity).optString("reportingUrl");
        if(endpoint.isEmpty()){new AlertDialog.Builder(activity).setTitle(dev.pocketllama.I18n.t("Обратная связь")).setMessage(dev.pocketllama.I18n.t("В этой тестовой сборке отправка обращений ещё не настроена.")).setPositiveButton(dev.pocketllama.I18n.t("Понятно"),null).show();return;}
        URL url=new URL(endpoint);if(!url.getProtocol().equals("https")||url.getUserInfo()!=null)throw new IOException(dev.pocketllama.I18n.t("Адрес обратной связи требует HTTPS"));
        JSONObject history=new JSONObject(ChatStore.read(activity));JSONArray messages=history.getJSONArray("messages");String answer=null;
        for(int i=0;i<messages.length();i++){JSONObject m=messages.getJSONObject(i);if(m.optString("id").equals(messageId)&&m.optString("role").equals("assistant")){answer=m.optString("content");break;}}
        if(answer==null||answer.isEmpty())throw new IOException(dev.pocketllama.I18n.t("Ответ не найден в сохранённой переписке"));
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*activity.getResources().getDisplayMetrics().density);layout.setPadding(pad,pad,pad,pad);
        TextView explanation=new TextView(activity);explanation.setText(dev.pocketllama.I18n.t("Проверьте текст перед отправкой. Разработчику отправятся этот фрагмент ответа и ваш комментарий."));layout.addView(explanation);
        Spinner category=new Spinner(activity);category.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{dev.pocketllama.I18n.t("Неприемлемый ответ"),dev.pocketllama.I18n.t("Опасный или вводящий в заблуждение ответ"),dev.pocketllama.I18n.t("Другая проблема")}));layout.addView(category);
        EditText excerpt=new EditText(activity);excerpt.setText(answer.substring(0,Math.min(answer.length(),12000)));excerpt.setMinLines(3);excerpt.setMaxLines(6);excerpt.setContentDescription(dev.pocketllama.I18n.t("Фрагмент ответа для отправки"));layout.addView(excerpt);
        EditText comment=new EditText(activity);comment.setHint(dev.pocketllama.I18n.t("Комментарий · необязательно"));comment.setMaxLines(3);comment.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});layout.addView(comment);
        TextView status=new TextView(activity);layout.addView(status);
        ScrollView scroll=new ScrollView(activity);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(dev.pocketllama.I18n.t("Сообщить о проблеме")).setView(scroll).setNegativeButton(dev.pocketllama.I18n.t("Отмена"),null).setPositiveButton(dev.pocketllama.I18n.t("Отправить"),null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            if(!dev.pocketllama.security.GateSession.isOpen())return;
            String text=excerpt.getText().toString().trim(),note=comment.getText().toString().trim();if(text.isEmpty()||text.length()>12000){status.setText(dev.pocketllama.I18n.t("Оставьте фрагмент ответа длиной до 12 000 символов."));return;}
            int selected=category.getSelectedItemPosition();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);status.setText(dev.pocketllama.I18n.t("Отправляем…"));
            io.execute(()->{String error="";try{JSONObject body=new JSONObject().put("category",selected).put("answer",text).put("comment",note).put("appVersion",DistributionConfig.version(activity));post(url,body.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){error=dev.pocketllama.I18n.t("Не удалось отправить обращение. Текст сохранён в этом окне; можно попробовать ещё раз.");}
                final String failure=error;new Handler(Looper.getMainLooper()).post(()->{if(activity.isFinishing()||activity.isDestroyed())return;if(failure.isEmpty()){dialog.dismiss();Toast.makeText(activity,dev.pocketllama.I18n.t("Обращение отправлено"),Toast.LENGTH_LONG).show();}else{status.setText(failure);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}});
            });
        }));dialog.show();
    }
    private static void post(URL url,byte[] bytes)throws Exception {
        if(bytes.length>64000)throw new IOException(dev.pocketllama.I18n.t("Отчёт слишком большой"));HttpURLConnection c=(HttpURLConnection)url.openConnection();
        try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("Report HTTP "+status);}finally{c.disconnect();}
    }
}
