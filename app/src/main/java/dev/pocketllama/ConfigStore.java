package dev.pocketllama;

import android.content.Context;
import android.util.Base64;
import dev.pocketllama.core.ServerConfig;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class ConfigStore {
    private static final java.util.concurrent.atomic.AtomicBoolean cleanedImports = new java.util.concurrent.atomic.AtomicBoolean();
    private final Context context;
    private final dev.pocketllama.security.EncryptedPreferences prefs;
    public ConfigStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = new dev.pocketllama.security.EncryptedPreferences(this.context);
        if (cleanedImports.compareAndSet(false, true)) {
            for (String folder : new String[]{"models", "projectors"}) {
                File[] incomplete = new File(this.context.getFilesDir(), folder).listFiles((dir, name) -> name.endsWith(".part"));
                if (incomplete != null) for (File file : incomplete) file.delete();
            }
        }
    }
    public ServerConfig load() {
        prefs.reload();
        ServerConfig c = new ServerConfig();
        c.modelName = prefs.getString("model", "");
        c.projectorName = prefs.getString("projector", "");
        c.backend = prefs.getString("backend", "cpu");
        c.contextPerSlot = prefs.getInt("context", 2048); c.slots = prefs.getInt("slots", 1);
        c.threads = prefs.getInt("threads", 4); c.gpuLayers = prefs.getInt("gpuLayers", 99);
        c.budgetMiB = prefs.getInt("budget", 0); c.systemReserveMiB = prefs.getInt("reserve", 1536);
        c.unknownKvMiB = prefs.getInt("unknownKv", 1024); c.port = prefs.getInt("port", 8080);
        c.cacheK = prefs.getString("cacheK", "f16"); c.cacheV = prefs.getString("cacheV", "f16");
        c.mmap = prefs.getBoolean("mmap", true); c.lan = prefs.getBoolean("lan", false);
        c.stayAwake = prefs.getBoolean("awake", true);
        c.strictMemory = prefs.getBoolean("strictMemory", false);
        c.vulkanCompatibility = prefs.getBoolean("vulkanCompatibility", true);
        c.cpuAcceleration = prefs.getBoolean("cpuAcceleration", true);
        return c;
    }
    public void save(ServerConfig c) {
        prefs.edit().putString("model", c.modelName).putString("projector", c.projectorName).putString("backend", c.backend)
                .putInt("context", c.contextPerSlot).putInt("slots", c.slots).putInt("threads", c.threads)
                .putInt("gpuLayers", c.gpuLayers).putInt("budget", c.budgetMiB).putInt("reserve", c.systemReserveMiB)
                .putInt("unknownKv", c.unknownKvMiB).putInt("port", c.port)
                .putString("cacheK", c.cacheK).putString("cacheV", c.cacheV)
                .putBoolean("mmap", c.mmap).putBoolean("lan", c.lan).putBoolean("awake", c.stayAwake)
                .putBoolean("strictMemory", c.strictMemory)
                .putBoolean("vulkanCompatibility", c.vulkanCompatibility)
                .putBoolean("cpuAcceleration", c.cpuAcceleration).apply();
    }
    public File modelsDir() {
        File dir = new File(context.getFilesDir(), "models");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Нельзя создать папку моделей");
        return dir;
    }
    public synchronized String apiKey() {
        String key = prefs.getString("apiKey", null);
        if (key == null) {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            key = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (!prefs.edit().putString("apiKey", key).commit()) throw new IllegalStateException("Нельзя сохранить API key");
        }
        return key;
    }
    public File projectorsDir() {
        File dir = new File(context.getFilesDir(), "projectors");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Нельзя создать папку mmproj");
        return dir;
    }
    public String modelFingerprint(File file,java.util.function.BooleanSupplier cancelled) throws IOException {
        String key="modelHash:"+file.getName(),stamp=file.length()+":"+file.lastModified()+":";
        String cached=prefs.getString(key,"");
        if(cached.startsWith(stamp) && cached.substring(stamp.length()).matches("[a-f0-9]{64}"))return cached.substring(stamp.length());
        String hash=dev.pocketllama.core.ModelFingerprint.sha256(file,cancelled);
        prefs.edit().putString(key,stamp+hash).apply();return hash;
    }
    /** Native server reads its API credential from stdin once; no plaintext key file or argv. */
    public File keyFile(){return new File("/proc/self/fd/0");}
    public void writeKeyTo(Process process)throws IOException {
        byte[] bytes=(apiKey()+"\n").getBytes(StandardCharsets.UTF_8);
        try(OutputStream output=process.getOutputStream()){output.write(bytes);output.flush();}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
    }
}
