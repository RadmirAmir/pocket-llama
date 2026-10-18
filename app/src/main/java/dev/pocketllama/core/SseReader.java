package dev.pocketllama.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.EOFException;
import java.io.Reader;

/** SSE framing; JSON decoding is deliberately separate from UTF-8 stream framing. */
public final class SseReader {
    private final BufferedReader reader;
    private final boolean strict;
    private static final int MAX_EVENT_CHARS = 128 * 1024;
    public SseReader(Reader reader) { this(reader,false); }
    public SseReader(Reader reader,boolean strict) { this.reader = new BufferedReader(reader);this.strict=strict; }
    public String next() throws IOException {
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
            if (line.isEmpty()) {
                if (data.length() > 0) return data.toString();
                continue;
            }
            if (!line.startsWith("data:")) continue;
            String part = line.substring(5);
            if (part.startsWith(" ")) part = part.substring(1);
            if (data.length() + part.length() + 1 > MAX_EVENT_CHARS) throw new IOException("Слишком большой фрагмент ответа");
            if (data.length() > 0) data.append('\n');
            data.append(part);
        }
        if(strict&&data.length()>0&&!data.toString().equals("[DONE]"))throw new EOFException("Поток завершился внутри SSE-события");
        return data.length() == 0 ? null : data.toString();
    }
}
