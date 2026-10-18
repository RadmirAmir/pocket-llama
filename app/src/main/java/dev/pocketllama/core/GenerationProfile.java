package dev.pocketllama.core;

import java.util.Locale;

/** Sampling choices come from the loaded model, not its editable filename. */
public enum GenerationProfile {
    STANDARD("standard", 0.7, 0.9, 20),
    MINICPM5("minicpm5", 1.0, 0.95, 40);

    public final String id;
    public final double temperature, topP;
    public final int topK;
    GenerationProfile(String id, double temperature, double topP, int topK) {
        this.id=id;this.temperature=temperature;this.topP=topP;this.topK=topK;
    }
    public static GenerationProfile from(GgufMetadata metadata) {
        String pre=String.valueOf(metadata.values.getOrDefault("tokenizer.ggml.pre", ""));
        String name=String.valueOf(metadata.values.getOrDefault("general.name", "")).toLowerCase(Locale.ROOT);
        return pre.equals("minicpm5") || name.contains("minicpm5") ? MINICPM5 : STANDARD;
    }
}
