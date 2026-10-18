package dev.pocketllama.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/** Advisory estimates and a separate, stable limit for live resident memory. */
public final class MemoryPolicy {
    public static final long MIB = 1024L * 1024;
    private static final Set<String> STANDARD_KV = new HashSet<>(Arrays.asList("llama", "qwen2", "qwen3", "gemma", "gemma2", "gemma3", "phi3", "mistral"));

    public static final class Plan {
        public final long estimateBytes, budgetBytes, availableBudgetBytes, kvBytes;
        public final long fileBytes, weightBytes, workBytes, totalBytes, availableBytes;
        public final boolean approximateKv;
        public final String cacheDescription;

        Plan(long estimate, long budget, long availableBudget, long kv, boolean approximate,
                long file, long weights, long work, long total, long available, String description) {
            estimateBytes = estimate; budgetBytes = budget; availableBudgetBytes = availableBudget;
            kvBytes = kv; approximateKv = approximate; fileBytes = file; weightBytes = weights;
            workBytes = work; totalBytes = total; availableBytes = available; cacheDescription = description;
        }
        public boolean fits() { return estimateBytes <= Math.min(budgetBytes, availableBudgetBytes); }
        public String blockReason(ServerConfig config) {
            if (fileBytes > budgetBytes) return "Файл весов больше лимита памяти сервера. Уменьшите модель или измените бюджет.";
            if (config.strictMemory && !fits()) return "Строгая проверка: оценка превышает доступную память или лимит сервера.";
            return null;
        }
        public String describe() {
            return String.format(Locale.US,
                "GGUF %.0f MiB; оценка процесса %.0f MiB\nВеса и запас backend %.0f MiB; рабочий резерв %.0f MiB\nKV / состояние %.0f MiB (%s)\nRAM всего %.0f MiB; доступно сейчас %.0f MiB\nДоступно после резерва %.0f MiB; лимит RSS %.0f MiB",
                fileBytes / (double) MIB, estimateBytes / (double) MIB, weightBytes / (double) MIB,
                workBytes / (double) MIB, kvBytes / (double) MIB, cacheDescription,
                totalBytes / (double) MIB, availableBytes / (double) MIB,
                availableBudgetBytes / (double) MIB, budgetBytes / (double) MIB);
        }
    }

    public static Plan plan(ServerConfig config, GgufMetadata model, long totalBytes, long availableBytes) {
        return plan(config, model, totalBytes, availableBytes, 0);
    }
    public static Plan plan(ServerConfig config, GgufMetadata model, long totalBytes, long availableBytes, long projectorBytes) {
        return plan(config, model, totalBytes, availableBytes, projectorBytes, false);
    }
    public static Plan plan(ServerConfig config, GgufMetadata model, long totalBytes, long availableBytes, long projectorBytes, boolean androidRuntime) {
        config.validate();
        if (totalBytes <= 0 || availableBytes < 0 || model.fileBytes <= 0 || projectorBytes < 0) throw new IllegalArgumentException("Некорректные данные памяти");
        long totalContext = Math.multiplyExact((long) config.contextPerSlot, config.slots);
        long layers = model.number("block_count", 0), kvHeads = model.number("attention.head_count_kv", 0);
        long heads = model.number("attention.head_count", 0), embed = model.number("embedding_length", 0);
        long dim = heads > 0 ? embed / heads : 0;
        long keyDim = model.number("attention.key_length", dim), valueDim = model.number("attention.value_length", dim);
        boolean dimensionsValid = layers > 0 && kvHeads > 0 && keyDim > 0 && valueDim > 0;
        boolean approximate = !STANDARD_KV.contains(model.architecture()) || !dimensionsValid;
        String description = approximate ? "ручной резерв для неизвестной архитектуры" : "attention, оценка по GGUF";
        double kvEstimate = approximate ? config.unknownKvMiB * (double) MIB
                : attentionBytes(layers, kvHeads, totalContext, keyDim, valueDim, config);

        if ((model.architecture().equals("qwen35") || model.architecture().equals("qwen35moe")) && dimensionsValid) {
            long inner = model.number("ssm.inner_size", 0), state = model.number("ssm.state_size", 0);
            long conv = model.number("ssm.conv_kernel", 0), groups = model.number("ssm.group_count", 0);
            long trunk = layers - model.number("nextn_predict_layers", 0);
            long attentionLayers = countAttentionLayers(model, trunk);
            if (inner > 0 && state > 0 && conv > 0 && groups > 0 && trunk > 0 && attentionLayers >= 0) {
                long paddedContext = ((totalContext + 255) / 256) * 256;
                double elements = state * (double) inner + (conv - 1) * (inner + 2.0 * groups * state);
                // Pinned llama-hparams.cpp n_embd_r/n_embd_s; recurrent state is F32.
                // Include active state, rollback/transient state and one host checkpoint per slot.
                double recurrentBytes = (trunk - attentionLayers) * elements * 4.0 * config.slots * 3;
                kvEstimate = attentionBytes(attentionLayers, kvHeads, paddedContext, keyDim, valueDim, config) + recurrentBytes;
                approximate = false;
                description = "Qwen 3.5: attention + recurrent + запас состояния";
            }
        }
        if (!Double.isFinite(kvEstimate) || kvEstimate < 0 || kvEstimate > Long.MAX_VALUE / 4.0) throw new IllegalArgumentException("Некорректные размеры KV в GGUF");
        long kv = (long) Math.ceil(kvEstimate * 1.12);
        double weightFactor = config.backend.equals("cpu") ? 1.0 : 1.20;
        long allFiles = Math.addExact(model.fileBytes, projectorBytes);
        long weights = Math.addExact((long) Math.ceil(model.fileBytes * weightFactor), projectorBytes);
        long work = (768L + config.threads * 16L + config.slots * 64L + (projectorBytes > 0 ? 256L : 0)
                + (androidRuntime ? 192L : 0)) * MIB;
        long estimate = Math.addExact(weights, Math.addExact(work, kv));
        long reserve = config.systemReserveMiB * MIB;
        long availableBudget = Math.max(0, Math.min(totalBytes, availableBytes) - reserve);
        long physicalCeiling = Math.max(0, totalBytes - reserve);
        // A free-RAM snapshot is useful for a warning, not as the process's permanent RSS cap.
        long requestedCap = config.budgetMiB == 0 ? (long) (totalBytes * 0.75) : config.budgetMiB * MIB;
        return new Plan(estimate, Math.min(physicalCeiling, requestedCap), availableBudget, kv, approximate,
                allFiles, weights, work, totalBytes, Math.min(totalBytes, availableBytes), description);
    }

    private static double attentionBytes(long layers, long heads, long context, long keyDim, long valueDim, ServerConfig c) {
        return layers * (double) heads * context * (keyDim * bytesPerElement(c.cacheK) + valueDim * bytesPerElement(c.cacheV));
    }
    private static long countAttentionLayers(GgufMetadata model, long layers) {
        if (layers <= 0 || layers > 1024) return -1;
        Object mask = model.values.get(model.architecture() + ".attention.recurrent_layers");
        if (mask != null) {
            if (!(mask instanceof boolean[]) || ((boolean[]) mask).length < layers) return -1;
            long result = 0;
            for (int i = 0; i < layers; i++) if (!((boolean[]) mask)[i]) result++;
            return result;
        }
        long interval = model.number("full_attention_interval", 4);
        return interval > 0 ? layers / interval : -1;
    }
    private static double bytesPerElement(String type) { return type.equals("q8_0") ? 34.0 / 32.0 : 2.0; }

    /** MemAvailable includes reclaimable cache. Never substitute MemTotal or free disk space. */
    public static long readMemAvailable(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader ? (BufferedReader) source : new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("MemAvailable:")) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3 || !parts[2].equals("kB")) return -1;
            try {
                long value = Long.parseLong(parts[1]);
                return value > 0 ? Math.multiplyExact(value, 1024L) : -1;
            } catch (NumberFormatException | ArithmeticException e) { return -1; }
        }
        return -1;
    }

    /** Three samples avoid transient cache dips; lowMemory=true acts immediately. */
    public static final class PressureGate {
        private int lowSamples, overBudgetSamples;
        public boolean shouldStop(long availableBytes, long floorBytes, boolean lowMemory, long rssBytes, long budgetBytes) {
            lowSamples = availableBytes < floorBytes ? lowSamples + 1 : 0;
            overBudgetSamples = rssBytes > 0 && rssBytes > budgetBytes ? overBudgetSamples + 1 : 0;
            return lowMemory || lowSamples >= 3 || overBudgetSamples >= 3;
        }
    }
}
