package ai.moonshine.voice;

/** Minimal STT-only surface of Moonshine Voice 0.1.5's MIT JNI ABI.
 * No microphone capture, downloader or network API is exposed to Pocket. */
public final class JNI {
    static {System.loadLibrary("moonshine-jni");}
    private JNI() {}
    public static native int moonshineLoadTranscriberFromFiles(String path,int modelArch,Object[] options);
    public static native void moonshineFreeTranscriber(int handle);
    public static native Transcript moonshineTranscribeWithoutStreaming(int handle,float[] audio,int sampleRate,int flags);
    public static native String moonshineErrorToString(int error);
}
