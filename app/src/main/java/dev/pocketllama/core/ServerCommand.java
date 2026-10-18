package dev.pocketllama.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ServerCommand {
    public static List<String> build(File executable, File model, File apiKeyFile, ServerConfig c) {
        return build(executable, model, apiKeyFile, c, false);
    }
    public static List<String> build(File executable, File model, File apiKeyFile, ServerConfig c, boolean summary) {
        c.validate();
        boolean quantizedV = !c.cacheV.equals("f16");
        List<String> cmd = new ArrayList<>(Arrays.asList(executable.getAbsolutePath(),
                "--model", model.getAbsolutePath(), "--alias", "mobile-llama",
                "--host", c.lan ? "0.0.0.0" : "127.0.0.1", "--port", String.valueOf(c.port),
                "--api-key-file", apiKeyFile.getAbsolutePath(),
                "--ctx-size", String.valueOf(c.contextPerSlot * c.slots),
                "--parallel", String.valueOf(c.slots),
                "--kv-unified-per-slot", String.valueOf(c.contextPerSlot),
                "--threads", String.valueOf(c.threads), "--threads-batch", String.valueOf(c.threads),
                "--batch-size", summary ? "128" : "256", "--ubatch-size", summary ? "32" : "64",
                "--cache-ram", "0", "--no-cache-idle-slots", "--ctx-checkpoints", "1",
                "--cache-type-k", c.cacheK, "--cache-type-v", c.cacheV,
                "--flash-attn", c.backend.equals("vulkan") && c.vulkanCompatibility ? "off" : quantizedV ? "on" : "auto",
                "--load-mode", c.mmap ? "mmap" : "none",
                "--no-webui", "--no-slots", "--metrics", "--offline", "--log-colors", "off", "--log-verbosity", "3"));
        if (c.backend.equals("cpu")) cmd.addAll(Arrays.asList("--device", "none"));
        else cmd.addAll(Arrays.asList("--gpu-layers", String.valueOf(c.gpuLayers)));
        if (!c.projectorName.isEmpty()) {
            File projector = new File(new File(model.getParentFile().getParentFile(), "projectors"), c.projectorName);
            cmd.addAll(Arrays.asList("--mmproj", projector.getAbsolutePath(), "--no-mmproj-offload"));
        }
        // Budget decisions belong to Android: never let desktop GPU auto-fit change context.
        cmd.addAll(Arrays.asList("--fit", "off"));
        // Supported by the exact native revision pinned in llama.lock.
        cmd.add("--jinja");
        if (summary) cmd.addAll(Arrays.asList("--reasoning-budget", "0"));
        return cmd;
    }
}
