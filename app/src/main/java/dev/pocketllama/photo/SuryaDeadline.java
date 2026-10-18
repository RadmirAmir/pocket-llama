package dev.pocketllama.photo;

import java.util.concurrent.*;

/** A wall-clock bound independent of SSE progress/keepalive events. One worker per request. */
public final class SuryaDeadline implements AutoCloseable {
    private final ScheduledExecutorService timer;
    private boolean active=true;private volatile boolean expired;
    public SuryaDeadline(long milliseconds,Runnable cancel) {
        if(milliseconds<1)throw new IllegalArgumentException("deadline");
        timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"surya-deadline");t.setDaemon(true);return t;});
        timer.schedule(()->{synchronized(this){if(!active)return;expired=true;active=false;}cancel.run();},milliseconds,TimeUnit.MILLISECONDS);
    }
    public boolean expired(){return expired;}
    @Override public void close(){synchronized(this){active=false;}timer.shutdownNow();}
    public static long milliseconds(SuryaImageSize.Task task){return (task==SuryaImageSize.Task.PAGE?6:task==SuryaImageSize.Task.BLOCK?4:3)*60_000L;}
}
