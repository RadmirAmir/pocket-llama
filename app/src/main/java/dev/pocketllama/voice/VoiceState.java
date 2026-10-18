package dev.pocketllama.voice;
public final class VoiceState {
    private VoiceState(){}
    public static volatile boolean recording,busy,paused;
    public static volatile float level;
    public static volatile long samples;
    public static volatile String id="";
}
