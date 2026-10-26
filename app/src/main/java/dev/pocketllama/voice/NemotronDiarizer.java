package dev.pocketllama.voice;

import java.io.File;
import java.io.IOException;

/** Native portable CPU runtime. Owned and closed only by the speech worker. */
public final class NemotronDiarizer implements SpeakerDiarization.Engine, AutoCloseable {
    private long handle;
    public NemotronDiarizer(File model) throws IOException {
        System.loadLibrary("pocket_nemotron");
        handle = open(model.getAbsolutePath(), 2);
        if (handle == 0) throw new IOException("Не удалось загрузить Nemotron");
    }
    @Override public void push(float[] audio) throws IOException { check(); push(handle, audio); }
    @Override public double[] finish() throws IOException { check(); return finish(handle); }
    private void check() throws IOException { if (handle == 0) throw new IOException("Diarizer closed"); }
    @Override public void close() { if (handle != 0) { destroy(handle); handle = 0; } }
    private static native long open(String path, int threads) throws IOException;
    private static native void push(long handle, float[] audio) throws IOException;
    private static native double[] finish(long handle) throws IOException;
    private static native void destroy(long handle);
}
