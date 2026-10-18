package dev.pocketllama;

import java.util.ArrayDeque;

/** Shared in-process state: Activity may be destroyed while the Service owns inference. */
public final class RuntimeState {
    public static volatile boolean active;
    public static volatile boolean ready;
    public static volatile String status = "Сервер остановлен";
    public static volatile String memory = "";
    public static volatile String endpoint = "";
    public static volatile String cpuProfile = "";
    public static volatile String effectiveBackend = "";
    public static volatile dev.pocketllama.core.GenerationProfile chatProfile = dev.pocketllama.core.GenerationProfile.STANDARD;
    private static final ArrayDeque<String> logs = new ArrayDeque<>();
    public static synchronized void log(String line) {
        if (line.length() > 3000) line = line.substring(0, 3000);
        logs.addLast(line);
        while (logs.size() > 120) logs.removeFirst();
    }
    public static synchronized String logText() { return String.join("\n", logs); }
    public static synchronized void clearLogs() { logs.clear(); }
}
