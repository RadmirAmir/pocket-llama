package dev.pocketllama.core;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Bounded GGUF v2/v3 metadata reader. Large tokenizer arrays are skipped, never loaded. */
public final class GgufReader {
    public static GgufMetadata read(File file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            if (u32(f) != 0x46554747L) throw new IOException("Файл не GGUF");
            long version = u32(f);
            if (version != 2 && version != 3) throw new IOException("Нужна GGUF v2 или v3");
            long tensors = nonNegative(u64(f));
            long count = nonNegative(u64(f));
            if (count > 100_000 || tensors > 10_000_000) throw new IOException("Некорректный заголовок GGUF");
            Map<String, Object> result = new HashMap<>();
            for (long i = 0; i < count; i++) {
                String key = string(f, 16_384);
                int type = (int) u32(f);
                boolean wanted = key.equals("general.architecture") || key.equals("general.name") || key.equals("general.type") || key.equals("split.count")
                        || key.equals("general.file_type") || key.equals("tokenizer.ggml.pre")
                        || key.equals("clip.has_vision_encoder") || key.equals("clip.has_audio_encoder")
                        || key.endsWith(".block_count") || key.endsWith(".embedding_length")
                        || key.endsWith(".attention.head_count") || key.endsWith(".attention.head_count_kv")
                        || key.endsWith(".attention.key_length") || key.endsWith(".attention.value_length")
                        || key.endsWith(".full_attention_interval") || key.endsWith(".attention.recurrent_layers")
                        || key.endsWith(".ssm.conv_kernel") || key.endsWith(".ssm.inner_size")
                        || key.endsWith(".ssm.state_size") || key.endsWith(".ssm.group_count")
                        || key.endsWith(".nextn_predict_layers");
                Object v = value(f, type, wanted, 0);
                if (wanted && v != null) result.put(key, v);
            }
            if (!result.containsKey("general.architecture") || tensors == 0) throw new IOException("GGUF не содержит модель");
            GgufMetadata meta = new GgufMetadata(f.length(), result);
            if (meta.splitCount() != 1) throw new IOException("MVP поддерживает единый GGUF; объедините split-файлы через llama-gguf-split --merge");
            // Tensor payload is validated by upstream at load time.
            return meta;
        }
    }
    private static Object value(RandomAccessFile f, int type, boolean wanted, int depth) throws IOException {
        switch (type) {
            case 0: return f.readUnsignedByte();
            case 1: return f.readByte();
            case 2: return (long) (f.readUnsignedByte() | f.readUnsignedByte() << 8);
            case 3: return (long) (short) (f.readUnsignedByte() | f.readUnsignedByte() << 8);
            case 4: return u32(f);
            case 5: return (long) (int) u32(f);
            case 6: return Float.intBitsToFloat((int) u32(f));
            case 7: return f.readUnsignedByte() != 0;
            case 8:
                if (wanted) return string(f, 65_536);
                skip(f, nonNegative(u64(f))); return null;
            case 9:
                if (depth > 0) throw new IOException("Вложенные GGUF arrays не поддерживаются");
                int element = (int) u32(f);
                long n = nonNegative(u64(f));
                if (n > 16_777_216L) throw new IOException("Слишком большой metadata array");
                if (wanted && element == 7 && n <= 1024) {
                    boolean[] recurrent = new boolean[(int) n];
                    for (int i = 0; i < recurrent.length; i++) recurrent[i] = f.readUnsignedByte() != 0;
                    return recurrent;
                }
                int width = fixedWidth(element);
                if (width > 0) skip(f, Math.multiplyExact(n, width));
                else if (element == 8) for (long i = 0; i < n; i++) value(f, element, false, depth + 1);
                else throw new IOException("Неизвестный тип GGUF array");
                return null;
            case 10: return u64(f);
            case 11: return u64(f);
            case 12: return Double.longBitsToDouble(u64(f));
            default: throw new IOException("Неизвестный тип GGUF: " + type);
        }
    }
    private static int fixedWidth(int type) {
        if (type == 0 || type == 1 || type == 7) return 1;
        if (type == 2 || type == 3) return 2;
        if (type >= 4 && type <= 6) return 4;
        if (type >= 10 && type <= 12) return 8;
        return -1;
    }
    private static long nonNegative(long v) throws IOException {
        if (v < 0) throw new IOException("GGUF integer overflow");
        return v;
    }
    private static long u32(RandomAccessFile f) throws IOException { return Integer.toUnsignedLong(Integer.reverseBytes(f.readInt())); }
    private static long u64(RandomAccessFile f) throws IOException { return Long.reverseBytes(f.readLong()); }
    private static String string(RandomAccessFile f, int cap) throws IOException {
        long n = nonNegative(u64(f));
        if (n > cap || n > f.length() - f.getFilePointer()) throw new IOException("Некорректная GGUF string");
        byte[] bytes = new byte[(int) n]; f.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static void skip(RandomAccessFile f, long n) throws IOException {
        if (n < 0 || n > f.length() - f.getFilePointer()) throw new EOFException("Обрезанные GGUF metadata");
        f.seek(f.getFilePointer() + n);
    }
}
