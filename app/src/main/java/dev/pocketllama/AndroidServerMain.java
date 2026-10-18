package dev.pocketllama;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

/** app_process entry point. This VM and its native server live outside the UI process. */
public final class AndroidServerMain {
    private AndroidServerMain() { }
    private static native int run(String[] args);
    public static void main(String[] args) {
        // RuntimeInit can redirect System.err to logcat. Keep diagnostics on the parent's pipe.
        PrintStream errors = new PrintStream(new FileOutputStream(FileDescriptor.err), true);
        int code = 70;
        try {
            if (args.length < 2 || !new File(args[0]).getName().equals("libpocket_vulkan.so"))
                throw new IllegalArgumentException("Invalid Vulkan JNI library");
            System.load(args[0]);
            errors.println("Pocket Llama Vulkan: Android ART/JNI runtime");
            code = run(Arrays.copyOfRange(args, 1, args.length));
        } catch (Throwable error) {
            String detail = error.getMessage();
            errors.println("Pocket Llama Android runtime: " + error.getClass().getSimpleName()
                    + (detail == null ? "" : ": " + detail.substring(0, Math.min(1500, detail.length()))));
        }
        System.exit(code);
    }
}
