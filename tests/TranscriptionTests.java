import dev.pocketllama.voice.SpeechRecognizer;
import dev.pocketllama.voice.Transcription;
import java.io.IOException;
import java.util.*;

/** Real orchestration with fake I/O: no Android, ONNX, JNI or model download. */
public final class TranscriptionTests {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static final class Fixture implements SpeechRecognizer, Transcription.Checkpoints {
        final Set<Integer> done = new HashSet<>();
        final List<String> reads = new ArrayList<>(), commits = new ArrayList<>();
        final List<Long> offsets = new ArrayList<>(), progress = new ArrayList<>();
        final List<Word> words = Collections.singletonList(new Word("речь", 17, 99));
        boolean stopped, stopInModel, failRead, failCommit, empty;
        int failCall = -1, calls, closes;

        Transcription session() {
            return new Transcription(416000, (start, count) -> {
                if (failRead) throw new IOException("read failed");
                reads.add(start + ":" + count);
                return new float[count];
            }, this, this, () -> {
                if (stopped) throw new InterruptedException("stopped");
            }, (end, total) -> {
                check(total == 416000, "progress uses recording length");
                progress.add(end);
            });
        }

        @Override public List<Word> recognize(float[] pcm, long offset) throws IOException {
            offsets.add(offset);
            if (++calls == failCall) throw new IOException("model failed");
            if (stopInModel) stopped = true;
            return empty ? Collections.emptyList() : words;
        }
        @Override public boolean contains(int part) { return done.contains(part); }
        @Override public void commit(int part, List<Word> result, long start, long end) throws IOException {
            if (failCommit) throw new IOException("disk full");
            check(empty ? result.isEmpty() : result == words, "word contents/times pass unchanged to store");
            check(done.add(part), "completed part cannot be committed twice");
            commits.add(part + ":" + start + ":" + end);
        }
        @Override public void cancel() { stopped = true; }
        @Override public void close() { closes++; }
    }

    private interface Operation { void run() throws Exception; }

    private static void fails(Class<? extends Exception> expected, Operation operation) throws Exception {
        try { operation.run(); throw new AssertionError("Expected " + expected.getSimpleName()); }
        catch (Exception error) { check(expected.isInstance(error), "original failure propagates: " + error); }
    }

    private static void regions(Transcription session) throws Exception {
        session.region(0, 400000);
        session.region(410000, 416000);
    }

    public static void main(String[] args) throws Exception {
        Fixture normal = new Fixture();
        regions(normal.session());
        check(normal.reads.equals(Arrays.asList("0:195840", "188160:199680", "380160:23680", "406160:9840")),
              "12 s cores, 240 ms context, final partial core, silence gap and EOF clipping");
        check(normal.offsets.equals(Arrays.asList(0L, 11760L, 23760L, 25385L)), "absolute PCM offsets in milliseconds");
        check(normal.commits.equals(Arrays.asList("0:0:12000", "1:12000:24000", "2:24000:25000", "3:25625:26000")),
              "stable checkpoint IDs across regions; only core bounds passed to store");
        check(normal.progress.equals(Arrays.asList(192000L, 384000L, 400000L, 416000L)), "progress follows committed cores");
        check(normal.closes == 0, "caller retains recognizer ownership");

        Fixture resumed = new Fixture();
        resumed.done.addAll(Arrays.asList(0, 2));
        regions(resumed.session());
        check(resumed.reads.equals(Arrays.asList("188160:199680", "406160:9840")), "completed parts read no PCM and run no inference");
        check(resumed.commits.equals(Arrays.asList("1:12000:24000", "3:25625:26000")), "skipping checkpoints does not renumber later parts");
        check(resumed.progress.equals(normal.progress), "resumed progress includes completed parts");

        Fixture failed = new Fixture();
        failed.failCall = 2;
        fails(IOException.class, () -> regions(failed.session()));
        check(failed.done.equals(Collections.singleton(0)), "model failure leaves only previous committed checkpoint");
        check(failed.progress.equals(Collections.singletonList(192000L)), "failed inference cannot advance progress");
        failed.failCall = -1;
        regions(failed.session());
        check(failed.commits.equals(normal.commits), "new attempt resumes after native failure without duplicating words");

        Fixture cancelled = new Fixture();
        cancelled.stopped = true;
        cancelled.done.add(0);
        fails(InterruptedException.class, () -> regions(cancelled.session()));
        check(cancelled.reads.isEmpty() && cancelled.progress.isEmpty(), "cancellation also stops checkpoint-only work");

        Fixture inFlight = new Fixture();
        inFlight.stopInModel = true;
        fails(InterruptedException.class, () -> regions(inFlight.session()));
        check(inFlight.calls == 1 && inFlight.done.isEmpty() && inFlight.progress.isEmpty(), "late native result after cancellation is never committed");
        inFlight.stopInModel = false;
        inFlight.stopped = false;
        regions(inFlight.session());
        check(inFlight.commits.equals(normal.commits), "cancelled core is retried on resume");

        Fixture disk = new Fixture();
        disk.failCommit = true;
        fails(IOException.class, () -> regions(disk.session()));
        check(disk.done.isEmpty() && disk.progress.isEmpty(), "failed storage does not complete or report a core");
        disk.failCommit = false;
        regions(disk.session());
        check(disk.commits.equals(normal.commits), "failed commit can be retried with unchanged checkpoint IDs");

        Fixture read = new Fixture();
        read.failRead = true;
        fails(IOException.class, () -> regions(read.session()));
        check(read.calls == 0 && read.done.isEmpty(), "unreadable encrypted PCM never reaches model or checkpoint");

        Fixture empty = new Fixture();
        empty.empty = true;
        regions(empty.session());
        regions(empty.session());
        check(empty.calls == 4 && empty.done.size() == 4, "valid empty transcript is checkpointed, not repeatedly recognized");

        Fixture invalid = new Fixture();
        Transcription session = invalid.session();
        session.region(0, 0);
        session.region(416000, 416000);
        check(invalid.calls == 0 && invalid.progress.isEmpty(), "empty regions do no work");
        fails(IllegalArgumentException.class, () -> session.region(-1, 5));
        fails(IllegalArgumentException.class, () -> session.region(5, 4));
        fails(IllegalArgumentException.class, () -> session.region(0, 416001));
        session.region(17, 33);
        check(invalid.commits.equals(Collections.singletonList("0:1:2")), "sample-to-ms truncation and checkpoint index remain compatible");
        System.out.println("PASS: " + checks + " transcription boundary, resume and failure checks");
    }
}
