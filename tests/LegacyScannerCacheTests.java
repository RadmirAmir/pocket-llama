import dev.pocketllama.photo.LegacyScannerCache;
import java.nio.file.*;
import java.util.Comparator;

public final class LegacyScannerCacheTests {
    private static int checks;
    private static void ok(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("retired-scanner-cache");
        try {
            Path files = Files.createDirectories(root.resolve("files"));
            LegacyScannerCache.clean(files.toFile());
            ok(!Files.exists(files.resolve("surya-2")), "fresh installs create no retired cache");
            Path detector = Files.createDirectories(files.resolve("surya-2/detector"));
            for (String name : new String[]{"line-detector.onnx", "line-detector.onnx.part", "line-detector.onnx.verified", "line-detector.onnx.verified.tmp"})
                Files.write(detector.resolve(name), new byte[]{1});
            Path model = files.resolve("surya-2/surya-2.gguf");
            Path projector = files.resolve("surya-2/mmproj.gguf");
            Path document = Files.createDirectories(files.resolve("documents")).resolve("page.enc");
            for (Path p : new Path[]{model, projector, document}) Files.write(p, new byte[]{7, 8, 9});
            LegacyScannerCache.clean(files.toFile());
            ok(!Files.exists(detector), "upgrades remove the model, interrupted download and verification stamps");
            for (Path p : new Path[]{model, projector, document})
                ok(java.util.Arrays.equals(Files.readAllBytes(p), new byte[]{7, 8, 9}), "model and encrypted user data remain unchanged: " + p.getFileName());
            Files.createDirectories(detector);
            Path unknown = detector.resolve("user-note.txt");
            Files.write(unknown, new byte[]{2});
            LegacyScannerCache.clean(files.toFile());
            LegacyScannerCache.clean(files.toFile());
            ok(Files.exists(unknown), "cleanup is repeatable and preserves unexpected files");
            Path outside = Files.createDirectory(root.resolve("outside"));
            Path target = outside.resolve("line-detector.onnx");
            Files.write(target, new byte[]{3});
            Files.createSymbolicLink(detector.resolve("line-detector.onnx"), target);
            LegacyScannerCache.clean(files.toFile());
            ok(Files.exists(target) && !Files.exists(detector.resolve("line-detector.onnx")), "file links are removed without deleting their targets");
            Files.delete(unknown); Files.delete(detector);
            Files.createSymbolicLink(detector, outside);
            LegacyScannerCache.clean(files.toFile());
            ok(Files.exists(target), "cleanup never follows a linked detector directory");
            Files.delete(detector);
            Files.move(files.resolve("surya-2"), files.resolve("saved-models"));
            Files.createSymbolicLink(files.resolve("surya-2"), root);
            LegacyScannerCache.clean(files.toFile());
            ok(Files.exists(target) && Files.exists(files.resolve("saved-models/surya-2.gguf")), "cleanup never follows a linked model directory");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
            }
        }
        System.out.println("LegacyScannerCacheTests: " + checks + " checks passed");
    }
}
