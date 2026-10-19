package dev.pocketllama.notes;

/** Coalesces typing without letting an older save acknowledge newer text. */
public final class NoteDraft {
    public static final class Snapshot {
        public final long sequence;
        public final String title, body;
        private Snapshot(long sequence, String title, String body) { this.sequence = sequence; this.title = title; this.body = body; }
    }
    private Snapshot current;
    private long saved;
    public NoteDraft(String title, String body) { current = new Snapshot(0, title, body); }
    public synchronized void stage(long sequence, String title, String body) {
        NoteText.validate(title, body);
        if (sequence <= current.sequence) return;
        current = new Snapshot(sequence, title, body);
    }
    public synchronized Snapshot pending() { return current.sequence > saved ? current : null; }
    public synchronized void acknowledge(long sequence) { saved = Math.max(saved, sequence); }
    public synchronized long savedSequence() { return saved; }
}
