package dev.pocketllama.voice;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.*;

/** Public model weights are bundled; private audio never leaves PcmFile. */
public final class DiarizationAssets {
    private DiarizationAssets() {}
    public static File prepare(Context context) throws Exception {
        JSONObject manifest;
        try (InputStream in = context.getAssets().open("diarization/manifest.json")) {
            ByteArrayOutputStream text = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) text.write(buffer, 0, n);
            manifest = new JSONObject(text.toString("UTF-8"));
        }
        if (!SpeakerDiarization.NEMOTRON.equals(manifest.getString("engine")))
            throw new IOException("Invalid Nemotron model package");
        JSONArray entries = manifest.getJSONArray("files");
        if (entries.length() != 1) throw new IOException("Invalid Nemotron model inventory");
        JSONObject item = entries.getJSONObject(0);
        String name = item.getString("name"), expected = item.getString("sha256");
        if (!name.matches("[A-Za-z0-9._-]+\\.gguf") || !expected.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid Nemotron model metadata");
        File dir = new File(context.getFilesDir(), "diarization-models-v1");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Нет места для модели спикеров");
        File target = new File(dir, name), marker = new File(dir, name + ".ok");
        if (target.length() == item.getLong("bytes") && marker.isFile()) return target;
        if (dir.getUsableSpace() < item.getLong("bytes") + 64L*1024*1024)
            throw new IOException("Недостаточно места для Nemotron (нужно около 170 МБ)");
        File part = new File(dir, name + ".part");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); long count = 0;
            try (InputStream in = context.getAssets().open("diarization/" + name);
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[128*1024]; int size;
                while ((size = in.read(buffer)) != -1) { out.write(buffer, 0, size); digest.update(buffer, 0, size); count += size; }
                out.getFD().sync();
            }
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            if (count != item.getLong("bytes") || !expected.equals(hash.toString()))
                throw new IOException("Проверка модели Nemotron не прошла");
            if (!part.renameTo(target)) throw new IOException("Не удалось сохранить модель Nemotron");
            try (FileOutputStream out = new FileOutputStream(marker)) { out.write(expected.getBytes(StandardCharsets.US_ASCII)); out.getFD().sync(); }
            return target;
        } finally { part.delete(); }
    }
}
