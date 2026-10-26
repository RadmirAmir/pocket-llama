package dev.pocketllama.voice;

import java.util.List;

/**
 * Runtime-independent speech boundary. Input is mono, 16 kHz PCM; word times
 * are absolute milliseconds in the recording, including offsetMs.
 *
 * The worker owns the recognizer and closes it once. cancel() may be called
 * from another thread; it requests cancellation, not necessarily immediate
 * native interruption. The caller must check cancellation before committing.
 */
public interface SpeechRecognizer extends AutoCloseable {
    List<Word> recognize(float[] pcm, long offsetMs) throws Exception;

    void cancel();

    final class Word {
        public final String text;
        public final long startMs;
        public final long endMs;

        public Word(String text, long startMs, long endMs) {
            this.text = text;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }
}
