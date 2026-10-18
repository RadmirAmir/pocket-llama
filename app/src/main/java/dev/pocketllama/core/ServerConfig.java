package dev.pocketllama.core;

public final class ServerConfig {
    public String modelName = "";
    public String projectorName = "";
    public String backend = "cpu";
    public int contextPerSlot = 2048;
    public int slots = 1;
    public int threads = 4;
    public int gpuLayers = 99;
    public int budgetMiB = 0; // 0: automatic launch budget
    public int systemReserveMiB = 1536;
    public int unknownKvMiB = 1024; // explicit fallback for hybrid/recurrent/unknown models
    public int port = 8080;
    public String cacheK = "f16";
    public String cacheV = "f16";
    public boolean mmap = true;
    public boolean lan = false;
    public boolean stayAwake = true;
    public boolean strictMemory = false; // Estimates warn by default; live memory monitoring remains active.
    public boolean vulkanCompatibility = true;
    public boolean cpuAcceleration = true;

    public void validate() {
        if (!modelName.matches("[A-Za-z0-9._-]+\\.gguf")) fail("Выберите импортированную GGUF");
        if (!projectorName.isEmpty() && !projectorName.matches("[A-Za-z0-9._-]+\\.gguf")) fail("Некорректный файл mmproj");
        if (!backend.equals("cpu") && !backend.equals("vulkan") && !backend.equals("opencl")) fail("Неизвестный backend");
        if (contextPerSlot < 512 || contextPerSlot > 32768) fail("Контекст: 512–32768 токенов");
        if (slots < 1 || slots > 2) fail("Число параллельных запросов: 1–2");
        if (threads < 1 || threads > 8) fail("Потоки CPU: 1–8");
        if (gpuLayers < 0 || gpuLayers > 999) fail("GPU layers: 0–999");
        if (budgetMiB != 0 && (budgetMiB < 512 || budgetMiB > 16384)) fail("Бюджет: 0 (авто) или 512–16384 MiB");
        if (systemReserveMiB < 512 || systemReserveMiB > 8192) fail("Резерв Android: 512–8192 MiB");
        if (unknownKvMiB < 256 || unknownKvMiB > 16384) fail("Резерв KV: 256–16384 MiB");
        if (port < 1024 || port > 65535) fail("Порт: 1024–65535");
        if (!validCache(cacheK) || !validCache(cacheV)) fail("KV cache: f16 или q8_0");
        if (backend.equals("vulkan") && vulkanCompatibility && !cacheV.equals("f16"))
            fail("Режим совместимости Vulkan требует F16 V. Выберите F16 или Q8 K / F16 V.");
    }
    private static boolean validCache(String value) { return "f16".equals(value) || "q8_0".equals(value); }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
