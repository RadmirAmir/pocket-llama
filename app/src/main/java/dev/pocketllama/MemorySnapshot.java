package dev.pocketllama;

import android.app.ActivityManager;
import android.content.Context;
import dev.pocketllama.core.MemoryPolicy;
import java.io.FileReader;
import java.util.Locale;

/** Real system memory: the Java heap is unrelated to native llama-server capacity. */
public final class MemorySnapshot {
    public final long totalBytes, availableBytes, androidAvailableBytes, thresholdBytes;
    public final boolean lowMemory;
    public final String source;

    private MemorySnapshot(ActivityManager.MemoryInfo info, long procAvailable) {
        totalBytes = info.totalMem;
        androidAvailableBytes = info.availMem;
        boolean valid = procAvailable > 0 && procAvailable <= totalBytes;
        availableBytes = valid ? procAvailable : info.availMem;
        thresholdBytes = info.threshold;
        lowMemory = info.lowMemory;
        source = valid ? "MemAvailable" : "Android availMem";
    }
    public static MemorySnapshot capture(Context context) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(info);
        long available = -1;
        try (FileReader reader = new FileReader("/proc/meminfo")) { available = MemoryPolicy.readMemAvailable(reader); }
        catch (Exception ignored) { }
        return new MemorySnapshot(info, available);
    }
    public String describe() {
        return String.format(Locale.US, "Источник RAM: %s; Android availMem %.0f MiB; lowMemory=%s",
                source, androidAvailableBytes / (double) MemoryPolicy.MIB, lowMemory);
    }
}
