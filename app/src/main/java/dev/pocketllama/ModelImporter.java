package dev.pocketllama;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import dev.pocketllama.core.GgufReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

public final class ModelImporter {
    public interface Progress { void update(long copied, long total); }
    public static File copy(Context context, Uri uri, Progress progress) throws IOException {
        return copy(context, uri, progress, false);
    }
    public static File copy(Context context, Uri uri, Progress progress, boolean projector) throws IOException {
        ConfigStore store = new ConfigStore(context);
        File dir = projector ? store.projectorsDir() : store.modelsDir();
        String name = "model.gguf"; long expected = -1;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME), s = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n);
                if (s >= 0 && !cursor.isNull(s)) expected = cursor.getLong(s);
            }
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".gguf")) throw new IOException("Выберите файл .gguf");
        long free = new StatFs(dir.getAbsolutePath()).getAvailableBytes();
        if (expected > 0 && expected + 256L * 1024 * 1024 > free) throw new IOException("Недостаточно места для копии GGUF");
        String base = name.substring(0, name.length() - 5).replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 80) base = base.substring(0, 80);
        String unique = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        File pending = new File(dir, unique + ".part"), dest = new File(dir, unique + ".gguf");
        boolean completed = false;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(pending)) {
                if (in == null) throw new IOException("Не удалось открыть GGUF");
                byte[] buf = new byte[1024 * 1024]; long copied = 0, lastUpdate = 0;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Импорт отменён");
                    copied += n;
                    if (copied > free - 128L * 1024 * 1024) throw new IOException("Недостаточно места");
                    out.write(buf, 0, n);
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 500) { progress.update(copied, expected); lastUpdate = now; }
                }
                if(expected>0 && copied!=expected)throw new IOException("GGUF скопирован не полностью: "+copied+" из "+expected+" байт. Повторите импорт исходного файла.");
                out.getFD().sync();
            }
            dev.pocketllama.core.GgufMetadata meta = GgufReader.read(pending);
            boolean isProjector = meta.architecture().equals("clip") || Boolean.TRUE.equals(meta.values.get("clip.has_vision_encoder"))
                    || "projector".equals(meta.values.get("general.type")) || "mmproj".equals(meta.values.get("general.type"));
            if (projector && !isProjector) throw new IOException("Это не mmproj. Выберите совместимый GGUF для обработки изображений.");
            if (!projector && isProjector) throw new IOException("Это mmproj. Импортируйте его кнопкой mmproj, а основную модель — отдельно.");
            if (!pending.renameTo(dest)) throw new IOException("Нельзя завершить импорт");
            completed = true;
            return dest;
        } finally { if (!completed) pending.delete(); }
    }
}
