package dev.pocketllama.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Plain-text attachments only; binary containers are never sent as fabricated text. */
public final class TextAttachment {
    public static final int MAX_BYTES = 256 * 1024;

    public static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Чтение отменено");
            if (n == 0) continue;
            if (n > limit - out.size()) throw new IOException("Файл слишком большой. Лимит " + limit / 1024 + " КиБ.");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
    public static String decode(byte[] data) throws IOException {
        if (data.length > MAX_BYTES) throw new IOException("Текстовый файл больше 256 КиБ");
        if (starts(data, new byte[]{'%', 'P', 'D', 'F'}) || starts(data, new byte[]{'P', 'K', 3, 4})
                || starts(data, new byte[]{'R', 'a', 'r', '!'}) || starts(data, new byte[]{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}))
            throw new IOException("Этот файл не текстовый. Поддерживаются TXT, Markdown, CSV, JSON, код и изображения; PDF/DOCX пока не разбираются.");
        String text;
        try {
            if (starts(data, new byte[]{(byte)0xFF, (byte)0xFE}))
                text = strictDecode(data, 2, StandardCharsets.UTF_16LE);
            else if (starts(data, new byte[]{(byte)0xFE, (byte)0xFF}))
                text = strictDecode(data, 2, StandardCharsets.UTF_16BE);
            else {
                int offset = starts(data, new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}) ? 3 : 0;
                try { text = strictDecode(data, offset, StandardCharsets.UTF_8); }
                catch (CharacterCodingException invalidUtf8) {
                    if (offset != 0) throw invalidUtf8;
                    text = strictDecode(data, 0, Charset.forName("windows-1251"));
                }
            }
        } catch (CharacterCodingException e) { throw new IOException("Не удалось прочитать кодировку. Сохраните файл как UTF-8.", e); }
        int controls = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0) throw new IOException("Файл содержит бинарные данные");
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t' && c != '\f') controls++;
        }
        if (controls > Math.max(2, text.length() / 100)) throw new IOException("Файл содержит бинарные данные");
        if (text.trim().isEmpty()) throw new IOException("Текстовый файл пуст");
        return text;
    }
    private static String strictDecode(byte[] data, int offset, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, offset, data.length - offset)).toString();
    }
    private static boolean starts(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }
}
