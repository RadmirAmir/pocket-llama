package dev.pocketllama.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Starts the JNI server in a supervised ART child with Android's Binder runtime. */
public final class AndroidServerCommand {
    private AndroidServerCommand() { }
    public static List<String> build(File appProcess, File library, List<String> server) {
        if (server.isEmpty()) throw new IllegalArgumentException("Empty server argv");
        List<String> command = new ArrayList<>(Arrays.asList(appProcess.getAbsolutePath(),
                "/system/bin", "--application", "--nice-name=PocketLlamaVulkan",
                "dev.pocketllama.AndroidServerMain", library.getAbsolutePath()));
        command.addAll(server);
        return command;
    }
}
