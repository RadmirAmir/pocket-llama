package dev.pocketllama.notes;

import java.text.Normalizer;
import java.util.Locale;

/** Literal Unicode search: SQL wildcards and markup have no special meaning. */
public final class NoteText {
    public static final int TITLE_LIMIT = 200, BODY_LIMIT = 200000, QUERY_LIMIT = 200;
    private NoteText() {}
    public static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
    public static void validate(String title, String body) {
        if (title == null || body == null || title.length() > TITLE_LIMIT || body.length() > BODY_LIMIT)
            throw new IllegalArgumentException("Заметка слишком большая");
    }
    public static String query(String value) {
        if (value == null || value.length() > QUERY_LIMIT) throw new IllegalArgumentException("Поисковый запрос слишком длинный");
        return fold(value.trim());
    }
    public static String preview(String body, String query) {
        String flat = body.replaceAll("\\s+", " ").trim();
        int at = query.isEmpty() ? -1 : fold(flat).indexOf(query);
        // Normalization can change offsets; clamp, and never split a surrogate pair.
        int start = Math.min(flat.length(), Math.max(0, at - 40));
        if (start > 0 && start < flat.length() && Character.isLowSurrogate(flat.charAt(start))) start--;
        int end = Math.min(flat.length(), start + 180);
        if (end > start && end < flat.length() && Character.isLowSurrogate(flat.charAt(end))) end--;
        return (start > 0 ? "…" : "") + flat.substring(start, end) + (end < flat.length() ? "…" : "");
    }
}
