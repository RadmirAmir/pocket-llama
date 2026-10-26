import dev.pocketllama.voice.SpeakerDiarization;
import java.io.IOException;
import java.util.*;

public final class SpeakerDiarizationTests {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    interface Action { void run() throws Exception; }
    static void fails(Action action, String label) throws Exception {
        try { action.run(); } catch (IOException | InterruptedException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        var parsed = SpeakerDiarization.segments(new double[]{1, 3, 7, 0, 2, 0}, 32000);
        check(parsed.get(0).speaker == 1, "channel zero is speaker one, never unknown");
        check(parsed.get(1).speaker == 8, "last channel is speaker eight");
        check(parsed.get(1).endMs == 2000, "tail padding clipped to actual duration");
        check(parsed.get(0).endMs > parsed.get(1).startMs, "overlap preserved");
        check(SpeakerDiarization.segments(new double[0], 16000).isEmpty(), "silence is a valid empty result");
        for (double[] invalid : new double[][]{
                {0,1}, {0,1,-1}, {0,1,8}, {0,1,0.5}, {Double.NaN,1,0},
                {0,Double.POSITIVE_INFINITY,0}, {-1,1,0}, {2,1,0}, {0,50,0}})
            fails(() -> SpeakerDiarization.segments(invalid, 16000), "reject malformed or incompatible timeline");
        List<Integer> sizes = new ArrayList<>(); List<float[]> buffers = new ArrayList<>();
        int[] finished = {0}; long[] progress = {0};
        SpeakerDiarization.Engine engine = new SpeakerDiarization.Engine() {
            public void push(float[] audio) { sizes.add(audio.length); buffers.add(audio); }
            public double[] finish() { finished[0]++; return new double[]{0, 2.1, 0}; }
        };
        var result = SpeakerDiarization.run(32007, (at, count) -> { float[] b = new float[count]; Arrays.fill(b, .25f); return b; },
            engine, () -> {}, (at,total) -> { check(at > progress[0] && at <= total, "monotonic progress"); progress[0] = at; });
        check(sizes.equals(Arrays.asList(16000,16000,7)), "bounded blocks and exact partial tail");
        check(finished[0] == 1 && result.size() == 1 && progress[0] == 32007, "finish once after every sample");
        check(buffers.stream().allMatch(b -> { for(float x : b) if(x!=0) return false; return true; }), "decrypted input wiped after push");
        boolean[] cancel = {false};
        SpeakerDiarization.Engine cancelled = new SpeakerDiarization.Engine() {
            public void push(float[] audio) { cancel[0] = true; }
            public double[] finish() { throw new AssertionError("cancelled stream must not finish"); }
        };
        fails(() -> SpeakerDiarization.run(16000,(at,n)->new float[n],cancelled,
                ()->{if(cancel[0])throw new InterruptedException();},(at,total)->{}), "cancel during inference discards result");
        SpeakerDiarization.Engine late = new SpeakerDiarization.Engine() {
            public void push(float[] audio) {}
            public double[] finish() { cancel[0]=true; return new double[]{0,1,0}; }
        };
        cancel[0]=false;
        fails(() -> SpeakerDiarization.run(16000,(at,n)->new float[n],late,
                ()->{if(cancel[0])throw new InterruptedException();},(at,total)->{}), "cancel during finish discards late speaker map");
        fails(() -> SpeakerDiarization.run(16000,(at,n)->new float[n-1],engine,()->{},(at,total)->{}), "truncated decrypted audio rejected");
        check(SpeakerDiarization.supported("cam++") && SpeakerDiarization.supported("nemotron-q8-v1")
                && !SpeakerDiarization.supported("other"), "explicit versioned engine allowlist");
        System.out.println("Speaker diarization: " + checks + " checks passed");
    }
}
