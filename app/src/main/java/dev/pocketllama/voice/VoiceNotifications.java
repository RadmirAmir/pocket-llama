package dev.pocketllama.voice;
import android.app.*;
import android.content.*;
import dev.pocketllama.R;
public final class VoiceNotifications {
    private VoiceNotifications(){}
    public static Notification build(Context c,String text,Class<?> service,String action){
        NotificationManager nm=c.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("voice",dev.pocketllama.I18n.t("Локальный диктофон"),NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(c,42,new Intent(c,VoiceActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(c,43,new Intent(c,service).setAction(action),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c,"voice").setSmallIcon(R.drawable.ic_llama).setContentTitle(dev.pocketllama.I18n.t("Pocket Llama · диктофон")).setContentText(dev.pocketllama.I18n.status(text)).setContentIntent(open).setVisibility(Notification.VISIBILITY_SECRET).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,dev.pocketllama.I18n.t("Остановить"),stop).build()).build();
    }
}
