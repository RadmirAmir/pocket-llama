package dev.pocketllama.core;

import java.util.Collections;
import java.util.Map;

public final class GgufMetadata {
    public final long fileBytes;
    public final Map<String, Object> values;
    public GgufMetadata(long fileBytes, Map<String, Object> values) {
        this.fileBytes = fileBytes;
        this.values = Collections.unmodifiableMap(values);
    }
    public String architecture() { return String.valueOf(values.getOrDefault("general.architecture", "unknown")); }
    public long number(String suffix, long fallback) {
        Object v = values.get(architecture() + "." + suffix);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }
    public long splitCount() {
        Object v = values.get("split.count");
        return v instanceof Number ? ((Number) v).longValue() : 1;
    }
}
