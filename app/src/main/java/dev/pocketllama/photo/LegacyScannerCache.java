package dev.pocketllama.photo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Removes only the retired model's known cache files; never document data. */
public final class LegacyScannerCache {
    private LegacyScannerCache() {}
    public static void clean(File applicationFiles) {
        Path parent = applicationFiles.toPath().resolve("surya-2");
        Path directory = parent.resolve("detector");
        if (Files.isSymbolicLink(parent) || Files.isSymbolicLink(directory)) return;
        for (String name : new String[]{"line-detector.onnx", "line-detector.onnx.part",
                "line-detector.onnx.verified", "line-detector.onnx.verified.tmp"}) {
            Path file = directory.resolve(name);
            try {
                if (Files.isRegularFile(file) || Files.isSymbolicLink(file)) Files.deleteIfExists(file);
            } catch (IOException ignored) { /* A cache cleanup failure must not block the vault. */ }
        }
        try { Files.deleteIfExists(directory); } catch (IOException ignored) { }
    }
}
