package dev.pocketllama.security;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import java.util.*;
/** Native opaque gate covers every sensitive screen before any store or WebView is initialized. */
public abstract class SecureActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){dev.pocketllama.I18n.initialize(base);super.attachBaseContext(dev.pocketllama.I18n.wrap(base));}
    private boolean initialized,resumed;private Bundle restored;private final List<Runnable> deferred=new ArrayList<>();
    @Override public final void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);restored=state;GateSession.register(this);}
    protected abstract void onSecureCreate(Bundle state);
    protected final boolean secureInitialized(){return initialized;}
    @Override protected void onResume(){super.onResume();resumed=true;
        if(!GateSession.isOpen()||!Vault.ready(this)){hideForLock();GateSession.show(this);return;}
        if(!initialized){onSecureCreate(restored);initialized=true;restored=null;}
        View content=findViewById(android.R.id.content);if(content!=null)content.setVisibility(View.VISIBLE);
        ArrayList<Runnable> work; synchronized(deferred){work=new ArrayList<>(deferred);deferred.clear();}for(Runnable r:work)r.run();
    }
    @Override protected void onPause(){resumed=false;super.onPause();}
    protected void onVaultLocked(){}
    public final void hideForLock(){onVaultLocked();View content=findViewById(android.R.id.content);if(content!=null)content.setVisibility(View.INVISIBLE);}
    protected final boolean deferResult(Runnable r){if(initialized&&GateSession.isOpen())return false;synchronized(deferred){deferred.add(r);}return true;}
    public final void lockNow(){GateSession.lock();if(resumed)GateSession.show(this);}
}
