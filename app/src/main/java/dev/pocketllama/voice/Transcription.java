package dev.pocketllama.voice;

import java.util.List;

/**
 * Sequential, bounded-memory ASR orchestration, independent of Android,
 * SQLite and the inference runtime. Feed persisted VAD regions in their
 * original order, including regions whose parts have already completed.
 *
 * Window geometry and part numbering are a persisted-checkpoint contract:
 * 12-second cores with 240 ms of context on each side at 16 kHz. Changing
 * them requires an explicit checkpoint version/migration, not a tuning flag.
 * A failed attempt must restart with a new instance and the same regions.
 */
public final class Transcription {
    private static final int SAMPLES_PER_MS = 16;
    private static final int CORE_SAMPLES = 192000;
    private static final int CONTEXT_SAMPLES = 3840;

    public interface Audio {
        float[] read(long startSample, int count) throws Exception;
    }

    public interface Checkpoints {
        boolean contains(int part) throws Exception;

        /** Save the words and completion marker atomically; filter to the core. */
        void commit(int part, List<SpeechRecognizer.Word> words,
                    long coreStartMs, long coreEndMs) throws Exception;
    }

    public interface Guard {
        void check() throws Exception;
    }

    public interface Progress {
        void update(long endSample, long totalSamples) throws Exception;
    }

    private final long totalSamples;
    private final Audio audio;
    private final SpeechRecognizer recognizer;
    private final Checkpoints checkpoints;
    private final Guard guard;
    private final Progress progress;
    private int nextPart;

    /** Resources remain owned by the caller; this class never closes them. */
    public Transcription(long totalSamples, Audio audio, SpeechRecognizer recognizer,
                         Checkpoints checkpoints, Guard guard, Progress progress) {
        if (totalSamples < 0) throw new IllegalArgumentException("Negative audio length");
        this.totalSamples = totalSamples;
        this.audio = audio;
        this.recognizer = recognizer;
        this.checkpoints = checkpoints;
        this.guard = guard;
        this.progress = progress;
    }

    public void region(long startSample, long endSample) throws Exception {
        if (startSample < 0 || endSample < startSample || endSample > totalSamples) {
            throw new IllegalArgumentException("Speech region outside the recording");
        }
        for (long core = startSample; core < endSample; core += CORE_SAMPLES) {
            guard.check();
            long coreEnd = Math.min(endSample, core + CORE_SAMPLES);
            if (!checkpoints.contains(nextPart)) {
                long left = Math.max(0, core - CONTEXT_SAMPLES);
                long right = Math.min(totalSamples, coreEnd + CONTEXT_SAMPLES);
                List<SpeechRecognizer.Word> words = recognizer.recognize(
                    audio.read(left, (int) (right - left)), left / SAMPLES_PER_MS);
                guard.check();
                checkpoints.commit(nextPart, words, core / SAMPLES_PER_MS,
                                   coreEnd / SAMPLES_PER_MS);
            }
            nextPart++;
            progress.update(coreEnd, totalSamples);
        }
    }
}
