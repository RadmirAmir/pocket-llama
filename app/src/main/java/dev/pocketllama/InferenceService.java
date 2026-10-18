package dev.pocketllama;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import dev.pocketllama.core.GgufReader;
import dev.pocketllama.core.BackendEnvironment;
import dev.pocketllama.core.MemoryPolicy;
import dev.pocketllama.core.ServerCommand;
import dev.pocketllama.core.ServerConfig;
import dev.pocketllama.core.SummaryProfile;
import dev.pocketllama.core.LoopbackHttp;
import dev.pocketllama.core.AndroidServerCommand;
import dev.pocketllama.core.ServerRecovery;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class InferenceService extends Service {
    public static volatile boolean exists;
    public static final String START = "dev.pocketllama.START", STOP = "dev.pocketllama.STOP";
    private static final String CHANNEL = "inference";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService logWorker = Executors.newSingleThreadExecutor();
    private volatile boolean stopping;
    private boolean summaryMode;
    private dev.pocketllama.voice.ModelLease modelLease;
    private volatile Process child;
    private volatile int childPid;
    private volatile Future<?> task;
    private volatile PowerManager.WakeLock wakeLock;
    private volatile boolean foreground;

    @Override public void onCreate() {
        super.onCreate(); exists = true;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Локальный LLM API", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopping = true;
            RuntimeState.status = "Остановка сервера…";
            Process p = child;
            if (p != null) p.destroy();
            if (task == null || task.isDone()) finish("Сервер остановлен");
            return START_NOT_STICKY;
        }
        if(!dev.pocketllama.security.Vault.ready(this)){stopSelf();return START_NOT_STICKY;}
        if (task != null && !task.isDone()) return START_NOT_STICKY;
        summaryMode = intent.getBooleanExtra("voice_summary", false);
        try (dev.pocketllama.voice.VoiceStore voices = new dev.pocketllama.voice.VoiceStore(this)) {
            if (dev.pocketllama.photo.PhotoStore.busy(this) || voices.anyBusy() && !summaryMode) {
                RuntimeState.status = "Сначала завершите запись, распознавание или конспект";
                stopSelf(); return START_NOT_STICKY;
            }
        }
        stopping = false; childPid = 0;
        RuntimeState.clearLogs(); RuntimeState.memory = ""; RuntimeState.endpoint = ""; RuntimeState.cpuProfile = ""; RuntimeState.effectiveBackend = "";
        RuntimeState.status = "Подготовка…"; RuntimeState.active = true; RuntimeState.ready = false;
        Notification notification = notification("Подготовка модели…");
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, notification);
        foreground = true;
        task = worker.submit(this::runServer);
        return START_NOT_STICKY; // Android cannot repeatedly reload a large model after killing us.
    }
    private void runServer() {
        String finalStatus = "Сервер остановлен";
        try {
            ConfigStore store = new ConfigStore(this);
            modelLease = new dev.pocketllama.voice.ModelLease(getFilesDir());
            ServerConfig c = store.load();
            if (summaryMode) SummaryProfile.apply(c);
            c.validate();
            File model = new File(store.modelsDir(), c.modelName);
            File projector = c.projectorName.isEmpty() ? null : new File(store.projectorsDir(), c.projectorName);
            if (!model.isFile()) throw new IllegalStateException("Модель не найдена");
            if (projector != null && !projector.isFile()) throw new IllegalStateException("mmproj не найден. Импортируйте его или выберите Без изображений.");
            dev.pocketllama.core.GgufMetadata meta = GgufReader.read(model);
            RuntimeState.chatProfile=dev.pocketllama.core.GenerationProfile.from(meta);
            RuntimeState.status="Проверяем GGUF…";updateNotification();
            String fingerprint=store.modelFingerprint(model,()->stopping);
            if (stopping) return;
            if (c.stayAwake) {
                PowerManager pm = getSystemService(PowerManager.class);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketLlama:API");
                wakeLock.setReferenceCounted(false); wakeLock.acquire(15 * 60 * 1000L);
            }
            File appProcess = new File("/system/bin/app_process64");
            File jni = new File(getApplicationInfo().nativeLibraryDir, "libpocket_vulkan.so");
            boolean artAvailable = appProcess.isFile() && appProcess.canExecute() && jni.isFile();
            final String selectedBackend = c.backend;
            ServerRecovery.Result result = ServerRecovery.run(selectedBackend, artAvailable,
                    () -> stopping || Thread.currentThread().isInterrupted(),
                    mode -> runAttempt(store, c, model, projector, meta, fingerprint, mode, appProcess, jni));
            finalStatus = stopping ? "Сервер остановлен" : result.status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            finalStatus = "Ошибка: " + e.getMessage(); RuntimeState.log(finalStatus);
        } finally {
            terminate(child);
            child = null; childPid = 0;
            dev.pocketllama.voice.ModelLease lease = modelLease; modelLease = null;
            if (lease != null) try { lease.close(); } catch (Exception ignored) { }
            releaseWakeLock(); finish(finalStatus);
        }
    }

    private ServerRecovery.Result runAttempt(ConfigStore store, ServerConfig c, File model, File projector,
            dev.pocketllama.core.GgufMetadata meta, String fingerprint,
            ServerRecovery.Mode mode, File appProcess, File jni) throws Exception {
        if (mode == ServerRecovery.Mode.CPU) {
            c.backend = "cpu";
            RuntimeState.log("Vulkan недоступен: резервный запуск CPU. Настройка вычислений сохранена.");
        } else if (mode == ServerRecovery.Mode.VULKAN_ART) {
            RuntimeState.log("Ошибка компиляции Vulkan: повторный запуск через Android ART/JNI.");
        }
        RuntimeState.ready = false; RuntimeState.cpuProfile = ""; RuntimeState.effectiveBackend = c.backend;
        RuntimeState.status = mode == ServerRecovery.Mode.CPU ? "Vulkan недоступен · загрузка CPU…"
                : mode == ServerRecovery.Mode.VULKAN_ART ? "Повторный запуск Vulkan через Android…" : "Загрузка GGUF…";
        updateNotification();
        File executable = new File(getApplicationInfo().nativeLibraryDir, "libllama_" + c.backend + ".so");
        if (!executable.isFile() || !executable.canExecute()) throw new IllegalStateException("Backend не включён в APK: " + c.backend);
        MemorySnapshot initialMemory = MemorySnapshot.capture(this);
        MemoryPolicy.Plan plan = MemoryPolicy.plan(c, meta, initialMemory.totalBytes, initialMemory.availableBytes,
                projector == null ? 0 : projector.length(), mode == ServerRecovery.Mode.VULKAN_ART);
        String modelDetails = "Pocket Llama 0.8.0\nМодель: " + c.modelName + "\nАрхитектура: " + meta.architecture()
                    + " · " + c.backend + " · контекст " + c.contextPerSlot + " · слоты " + c.slots
                    + "\nЗапуск: " + (mode == ServerRecovery.Mode.VULKAN_ART ? "Android ART/JNI" : "native")
                    + (mode == ServerRecovery.Mode.CPU ? " · резерв после ошибки Vulkan" : "")
                    + "\nmmproj: " + (projector == null ? "без изображений" : c.projectorName)
                    + "\nGGUF SHA256: " + fingerprint + "\nКопия: " + dev.pocketllama.core.ModelFingerprint.reference(fingerprint)
                    + "\nЧат: " + RuntimeState.chatProfile.id + " · Jinja · KV K="+c.cacheK+" / V="+c.cacheV+" · mmap="+c.mmap
                    + "\nТокенизатор: " + meta.values.getOrDefault("tokenizer.ggml.pre","не указан");
        RuntimeState.memory = modelDetails + "\n" + plan.describe();
        RuntimeState.log(RuntimeState.memory); RuntimeState.log(initialMemory.describe());
        RuntimeState.log("Устройство: " + Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE + " · аппаратная платформа " + Build.HARDWARE);
        RuntimeState.log("Страница памяти: "+android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)+" байт");
        if (c.backend.equals("vulkan")) RuntimeState.log("Vulkan совместимость: "
                + (c.vulkanCompatibility ? "включена: DMMV shared memory; без cooperative matrices, integer-dot и Flash Attention" : "выключена: штатные ядра"));
        String blocked = plan.blockReason(c);
        if (blocked != null) throw new IllegalStateException(blocked);
        if (initialMemory.lowMemory) throw new IllegalStateException("Android уже сообщает о нехватке памяти");
        if (!plan.fits()) RuntimeState.log("Предупреждение: оценка больше доступной RAM. Пробуем загрузку с контролем фактической памяти.");
        if (stopping) return new ServerRecovery.Result("Сервер остановлен", ServerRecovery.Failure.NONE, false);
        File keyFile = store.keyFile();
        List<String> args = ServerCommand.build(executable, model, keyFile, c, summaryMode);
        if (mode == ServerRecovery.Mode.VULKAN_ART) args = AndroidServerCommand.build(appProcess, jni, args);
        ProcessBuilder pb = new ProcessBuilder(args).redirectErrorStream(true).directory(getFilesDir());
        pb.environment().put("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir);
        pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
        pb.environment().put("GGML_OPENCL_KERNEL_CACHE_DIR", new File(getCacheDir(), "opencl").getAbsolutePath());
        BackendEnvironment.apply(pb.environment(), c);
        pb.environment().remove("LLAMA_API_KEY");
        pb.environment().remove("CLASSPATH");
        pb.environment().put("POCKET_LLAMA_PARENT_PID", String.valueOf(android.os.Process.myPid()));
        if (mode == ServerRecovery.Mode.VULKAN_ART) pb.environment().put("CLASSPATH", getApplicationInfo().sourceDir);
        Process running = null;
        Future<?> logs = null;
        boolean ready = false, resourceStop = false;
        String finalStatus = "Сервер остановлен";
        final java.util.concurrent.atomic.AtomicBoolean pipelineFailed = new java.util.concurrent.atomic.AtomicBoolean();
        String secret = store.apiKey();
        final String backend = c.backend;
        try {
            running = pb.start(); child = running; childPid = 0;
            final Process logged = running;
            logs = logWorker.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(logged.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("POCKET_LLAMA_PID=")) {
                            try { childPid = Integer.parseInt(line.substring("POCKET_LLAMA_PID=".length()).trim()); }
                            catch (NumberFormatException ignored) { }
                        } else {
                            if (line.startsWith("Pocket Llama CPU: fast")) RuntimeState.cpuProfile = "ускоренный";
                            else if (line.startsWith("Pocket Llama CPU: generic")) RuntimeState.cpuProfile = "обычный";
                            RuntimeState.log(line.replace(secret, "[redacted]"));
                            if (backend.equals("vulkan") && BackendEnvironment.pipelineFailure(line)) {
                                pipelineFailed.set(true);
                                RuntimeState.ready = false;
                                RuntimeState.status = "Vulkan: ошибка компиляции ядра…";
                                updateNotification();
                            }
                        }
                    }
                } catch (Exception e) { if (!stopping) RuntimeState.log("Log reader: " + e.getClass().getSimpleName()); }
            });
            if (stopping) return new ServerRecovery.Result("Сервер остановлен", ServerRecovery.Failure.NONE, false);
            store.writeKeyTo(running);
            RuntimeState.endpoint = "http://127.0.0.1:" + c.port + "/v1";
            long started = System.nanoTime();
            MemoryPolicy.PressureGate pressure = new MemoryPolicy.PressureGate();
            while (!stopping && running.isAlive()) {
                MemorySnapshot currentMemory = MemorySnapshot.capture(this);
                long rss = rssBytes(childPid);
                RuntimeState.memory = modelDetails + (c.backend.equals("cpu") ? "\nCPU-ядра: " + RuntimeState.cpuProfile : "")
                        + "\n" + plan.describe() + String.format(java.util.Locale.US,
                        "\nСейчас доступно: %.0f MiB; RSS сервера: %s", currentMemory.availableBytes / (double) MemoryPolicy.MIB,
                        rss >= 0 ? String.format(java.util.Locale.US, "%.0f MiB", rss / (double) MemoryPolicy.MIB) : "недоступен");
                long floor = Math.max(currentMemory.thresholdBytes, c.systemReserveMiB * MemoryPolicy.MIB / 2);
                if (pressure.shouldStop(currentMemory.availableBytes, floor, currentMemory.lowMemory, rss, plan.budgetBytes)) {
                    String reason = currentMemory.lowMemory ? "Android lowMemory" : currentMemory.availableBytes < floor ? "доступная RAM ниже резерва" : "RSS выше лимита";
                    finalStatus = "Модель выгружена: " + reason;
                    resourceStop = true;
                    RuntimeState.log(finalStatus + "\n" + RuntimeState.memory + "\n" + currentMemory.describe()); break;
                }
                PowerManager pm = getSystemService(PowerManager.class);
                if (pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) {
                    finalStatus = "Модель выгружена: сильный перегрев. Дайте телефону остыть"; resourceStop = true; break;
                }
                if (c.stayAwake && wakeLock != null) wakeLock.acquire(15 * 60 * 1000L);
                if (!ready && !pipelineFailed.get() && childPid > 0 && health(c.port, secret) && !pipelineFailed.get()) {
                    // Confirm our PID and authenticated API rather than another server's public health.
                    ready = true;
                    RuntimeState.ready = true;
                    RuntimeState.status = "API работает · " + c.backend.toUpperCase(java.util.Locale.ROOT)
                            + (c.backend.equals("cpu") ? " · " + RuntimeState.cpuProfile : "");
                    if (mode == ServerRecovery.Mode.CPU) RuntimeState.status += " · Vulkan недоступен";
                    updateNotification();
                }
                if (pipelineFailed.get()) {
                    RuntimeState.ready = false;
                    finalStatus = "Vulkan: драйвер не смог скомпилировать вычислительное ядро";
                    break;
                }
                if (!ready && System.nanoTime() - started > TimeUnit.MINUTES.toNanos(10)) {
                    finalStatus = "Загрузка не завершилась за 10 минут. Проверьте логи"; break;
                }
                TimeUnit.SECONDS.sleep(2);
            }
            if (!stopping && !running.isAlive()) finalStatus = "Сервер завершился: код " + running.exitValue() + ". Проверьте логи";
        } catch (java.io.IOException error) {
            finalStatus = "Не удалось запустить сервер: " + error.getMessage();
            RuntimeState.log(finalStatus);
        } finally {
            RuntimeState.ready = false;
            terminate(running);
            if (logs != null) {
                try { logs.get(5, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                catch (Exception error) { logs.cancel(true); if (running != null) try { running.getInputStream().close(); } catch (Exception ignored) { } }
            }
            child = null; childPid = 0;
        }
        if (stopping) return new ServerRecovery.Result("Сервер остановлен", ServerRecovery.Failure.NONE, ready);
        ServerRecovery.Failure failure = resourceStop ? ServerRecovery.Failure.RESOURCE
                : pipelineFailed.get() ? ServerRecovery.Failure.PIPELINE : ServerRecovery.Failure.STARTUP;
        if (failure == ServerRecovery.Failure.PIPELINE) finalStatus = "Vulkan: драйвер не смог скомпилировать вычислительное ядро";
        RuntimeState.log(finalStatus);
        return new ServerRecovery.Result(finalStatus, failure, ready);
    }
    private static void terminate(Process process) {
        if (process == null) return;
        process.destroy();
        try { if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(); } }
        catch (InterruptedException error) {
            process.destroyForcibly();
            while (process.isAlive()) try { process.waitFor(); } catch (InterruptedException ignored) { }
            Thread.currentThread().interrupt();
        }
    }
    private static long rssBytes(int pid) {
        if (pid <= 0) return -1;
        try (BufferedReader r = new BufferedReader(new java.io.FileReader("/proc/" + pid + "/status"))) {
            String line;
            while ((line = r.readLine()) != null) if (line.startsWith("VmRSS:")) return Long.parseLong(line.trim().split("\\s+")[1]) * 1024;
        } catch (Exception ignored) { }
        return -1; // Do not invent a reading on Android builds that restrict /proc.
    }
    private static boolean health(int port, String apiKey) {
        HttpURLConnection connection = null;
        try {
            connection = LoopbackHttp.open(port,"/v1/models",apiKey,750,750);
            return connection.getResponseCode() == 200;
        } catch (Exception ignored) { return false; }
        finally { if (connection != null) connection.disconnect(); }
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, InferenceService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_llama).setContentTitle("Pocket Llama · локальный API")
                .setContentText(text).setContentIntent(open).setVisibility(Notification.VISIBILITY_SECRET).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Остановить", stop).build()).build();
    }
    private void updateNotification() { getSystemService(NotificationManager.class).notify(1, notification(RuntimeState.status)); }
    private void releaseWakeLock() {
        PowerManager.WakeLock w = wakeLock; wakeLock = null;
        if (w != null && w.isHeld()) w.release();
    }
    private void finish(String status) {
        RuntimeState.active = false; RuntimeState.ready = false; RuntimeState.status = status;
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
        stopSelf();
    }
    @Override public void onDestroy() {
        stopping = true;
        Process p = child; if (p != null) p.destroyForcibly();
        releaseWakeLock(); worker.shutdownNow(); logWorker.shutdownNow();
        RuntimeState.active = false; RuntimeState.ready = false;
        exists = false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
