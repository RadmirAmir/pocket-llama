import dev.pocketllama.notes.NoteDraft;
import dev.pocketllama.notes.NoteText;

public final class NoteDraftTests {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) {
        NoteDraft draft = new NoteDraft("Original", "Body");
        check(draft.pending() == null, "opening a note is not an edit");
        draft.stage(1, "One", "First");
        NoteDraft.Snapshot inFlight = draft.pending();
        draft.stage(2, "Two", "Second");
        draft.acknowledge(inFlight.sequence);
        check(draft.pending().sequence == 2 && draft.pending().body.equals("Second"), "older save cannot acknowledge newer typing");
        draft.stage(1, "Stale", "Stale");
        check(draft.pending().title.equals("Two"), "late bridge edits cannot overwrite a newer snapshot");
        draft.acknowledge(2);
        check(draft.pending() == null && draft.savedSequence() == 2, "pause flush can acknowledge the last snapshot");
        for (int n=3; n<1000; n++) draft.stage(n, "Rapid", "Text " + n);
        check(draft.pending().body.equals("Text 999"), "rapid input coalesces to complete latest text");
        draft.acknowledge(999); draft.stage(1000, "", "");
        check(draft.pending().body.isEmpty(), "clearing a note is a real durable edit");
        check(NoteText.fold("ЁЖИК И ДРУЗЬЯ").equals(NoteText.fold("ежик и друзья")), "Russian search case and yo normalization");
        check(NoteText.fold("Cafe\u0301").equals(NoteText.fold("CAFÉ")), "canonical Unicode normalization");
        check(NoteText.query("  Слово  ").equals("слово"), "search trims boundary spaces");
        check(NoteText.query("%_[]' OR 1=1 --").equals("%_[]' or 1=1 --"), "punctuation stays literal");
        String body="Начало "+"текст ".repeat(60)+"нужная фраза "+"😀".repeat(100);
        String preview=NoteText.preview(body,"нужная фраза");
        check(preview.startsWith("…")&&preview.contains("нужная фраза"), "snippet includes a match beyond the first paragraph");
        check(!Character.isHighSurrogate(preview.charAt(preview.length()-2)), "snippet does not end with half an emoji");
        boolean rejected=false;try { draft.stage(1001,"", "x".repeat(200001)); }catch(IllegalArgumentException e){rejected=true;}
        check(rejected&&draft.pending().sequence==1000,"oversized input preserves previous accepted draft");
        rejected=false;try { NoteText.query("x".repeat(201)); }catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"search size is bounded");
        System.out.println("PASS: "+checks+" notes draft/search checks");
    }
}
