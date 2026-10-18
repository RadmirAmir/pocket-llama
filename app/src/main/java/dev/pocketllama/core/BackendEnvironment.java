package dev.pocketllama.core;

import java.util.Map;

/** Applies the selected profile only to the child process, before Vulkan initialization. */
public final class BackendEnvironment {
    private static final String[] COMPATIBILITY_FLAGS = {
            "GGML_VK_FORCE_DMMV_SHMEM",
            "GGML_VK_DISABLE_COOPMAT", "GGML_VK_DISABLE_COOPMAT2",
            "GGML_VK_DISABLE_INTEGER_DOT_PRODUCT"
    };
    private BackendEnvironment() { }
    public static void apply(Map<String, String> env, ServerConfig config) {
        for (String flag : COMPATIBILITY_FLAGS) env.remove(flag);
        env.remove("POCKET_LLAMA_CPU_MODE");
        if ("cpu".equals(config.backend)) env.put("POCKET_LLAMA_CPU_MODE", config.cpuAcceleration ? "auto" : "generic");
        if ("vulkan".equals(config.backend) && config.vulkanCompatibility)
            for (String flag : COMPATIBILITY_FLAGS) env.put(flag, "1");
    }
    public static boolean pipelineFailure(String message) {
        if (message == null) return false;
        String text = message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("createcomputepipeline") || text.contains("compute pipeline creation failed");
    }
}
