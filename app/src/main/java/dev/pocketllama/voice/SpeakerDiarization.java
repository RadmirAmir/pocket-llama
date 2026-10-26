package dev.pocketllama.voice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** One bounded-memory attempt. No raw audio or incomplete speaker map is persisted. */
public final class SpeakerDiarization {
    public static final String NEMOTRON = "nemotron-q8-v1", LEGACY = "cam++";
    private static final int BLOCK = 16000;
    private SpeakerDiarization() {}

    public static boolean supported(String engine) {
        return NEMOTRON.equals(engine) || LEGACY.equals(engine);
    }
    public static final class Segment {
        public final long startMs, endMs;
        public final int speaker;
        public Segment(long startMs, long endMs, int speaker) {
            if (startMs < 0 || endMs <= startMs || speaker < 1 || speaker > 8)
                throw new IllegalArgumentException("Invalid speaker segment");
            this.startMs = startMs; this.endMs = endMs; this.speaker = speaker;
        }
    }
    public interface Audio { float[] read(long start, int count) throws Exception; }
    public interface Engine {
        void push(float[] samples) throws Exception;
        /** Triples of start seconds, end seconds, ZERO-based Sortformer channel. */
        double[] finish() throws Exception;
    }
    public interface Guard { void check() throws Exception; }
    public interface Progress { void update(long samples, long total) throws Exception; }

    public static List<Segment> run(long total, Audio audio, Engine engine,
                                    Guard guard, Progress progress) throws Exception {
        if (total <= 0 || total > 2000000000L) throw new IOException("Invalid audio duration");
        for (long at = 0; at < total; at += BLOCK) {
            guard.check();
            int count = (int)Math.min(BLOCK, total - at);
            float[] block = audio.read(at, count);
            try {
                if (block == null || block.length != count) throw new IOException("Incomplete audio block");
                guard.check(); engine.push(block); guard.check();
            } finally { if (block != null) Arrays.fill(block, 0f); }
            progress.update(at + count, total);
        }
        guard.check();
        double[] raw = engine.finish();
        guard.check();
        List<Segment> result = segments(raw, total);
        guard.check();
        return result;
    }

    /** C++ channels start at zero; Pocket reserves zero for an unknown speaker. */
    public static List<Segment> segments(double[] raw, long samples) throws IOException {
        if (raw == null || raw.length % 3 != 0 || samples <= 0 || samples > 2000000000L)
            throw new IOException("Invalid diarization result");
        long durationMs = samples / 16;
        List<Segment> result = new ArrayList<>();
        for (int i = 0; i < raw.length; i += 3) {
            double start = raw[i], end = raw[i+1], channel = raw[i+2];
            if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(channel)
                    || start < 0 || end <= start || channel != Math.rint(channel) || channel < 0 || channel > 7
                    || start > samples / 16000.0 + 1 || end > samples / 16000.0 + 1)
                throw new IOException("Invalid diarization segment");
            long from = Math.max(0, Math.min(durationMs, Math.round(start * 1000)));
            long to = Math.max(from, Math.min(durationMs, Math.round(end * 1000)));
            if (to > from) result.add(new Segment(from, to, (int)channel + 1));
        }
        result.sort(Comparator.comparingLong((Segment s) -> s.startMs).thenComparingInt(s -> s.speaker));
        return Collections.unmodifiableList(result);
    }
}
