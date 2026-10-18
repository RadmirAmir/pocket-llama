package dev.pocketllama.security;
import android.app.*;
import android.content.Intent;
import android.os.*;
import java.util.*;
/** Process-local UI authorization. Never persisted; background services keep their existing work. */
public final class GateSession {
    private static volatile boolean open,showing;private static final Set<SecureActivity> screens=Collections.newSetFromMap(new WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicLong generation=new java.util.concurrent.atomic.AtomicLong();
    public static long epoch(){return generation.get();}
    public static boolean isOpen(){return open;}
    public static void requireOpen(){if(!open)throw new SecurityException("Введите код входа");}
    static void register(SecureActivity a){synchronized(screens){screens.add(a);}}
    public static void unlock(){open=true;showing=false;}
    public static void lock(){generation.incrementAndGet();open=false;synchronized(screens){for(SecureActivity a:new ArrayList<>(screens))a.runOnUiThread(a::hideForLock);}}
    static void show(Activity a){if(showing||a.isFinishing())return;showing=true;a.startActivity(new Intent(a,LockActivity.class).putExtra("return",true));}
    static void gateOpened(){showing=true;}
    static void gateClosed(){showing=false;}
    private GateSession(){}
}
