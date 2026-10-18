package dev.pocketllama.installer;

import android.content.*;
import android.content.pm.PackageInstaller;

/** Only the explicit, installer-owned PendingIntent can reach this receiver. */
public final class InstallResult extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent result){
        int status=result.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm=result.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm!=null){try{context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}catch(Exception e){save(context,"Android ждёт подтверждение установки. Вернитесь в установщик и повторите попытку.\n"+e.getMessage());return;}}
        }
        String message=result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String blocker=result.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME);
        int legacy=result.getIntExtra("android.content.pm.extra.LEGACY_STATUS",0);
        String text=status==PackageInstaller.STATUS_SUCCESS?"Pocket Community установлен. Можно нажать «Открыть Pocket».":"Android отказал в установке community.\nСтатус: "+status+"; системный код: "+legacy+"\n"+(message==null?"Без подробного сообщения":message)+(blocker==null?"":"\nСвязанное приложение: "+blocker)+"\n\nНажмите «Копировать результат» и отправьте текст разработчику. Старые приложения и данные сохранены.";
        save(context,text);
    }
    private void save(Context context,String text){context.getSharedPreferences(InstallActivity.PREFS,0).edit().putString("message",text).apply();}
}
