package dev.pocketllama.security;
import android.app.*;
import android.content.*;
import android.os.*;
public final class PocketApp extends Application {
    private int started;
    @Override public void onCreate(){super.onCreate();dev.pocketllama.photo.LegacyScannerCache.clean(getFilesDir());dev.pocketllama.I18n.initialize(this);System.loadLibrary("sqlcipher");net.zetetic.database.Logger.setTarget(new net.zetetic.database.NoopTarget());
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){public void onActivityCreated(Activity a,Bundle b){a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);}public void onActivityStarted(Activity a){started++;}public void onActivityStopped(Activity a){started=Math.max(0,started-1);if(started==0&&!a.isChangingConfigurations())GateSession.lock();}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}});
        BroadcastReceiver off=new BroadcastReceiver(){public void onReceive(Context c,Intent i){GateSession.lock();}};
        if(Build.VERSION.SDK_INT>=33)registerReceiver(off,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(off,new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
}
