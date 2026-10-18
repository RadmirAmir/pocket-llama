package dev.pocketllama.core;

import java.util.function.BooleanSupplier;

/** Bounded recovery before the API accepts a request; never replays generation. */
public final class ServerRecovery {
    public enum Mode { NATIVE, VULKAN_ART, CPU }
    public enum Failure { NONE, PIPELINE, STARTUP, RESOURCE }
    public static final class Result {
        public final String status;
        public final Failure failure;
        public final boolean wasReady;
        public Result(String status, Failure failure, boolean wasReady) {
            this.status = status; this.failure = failure; this.wasReady = wasReady;
        }
    }
    public interface Attempt { Result run(Mode mode) throws Exception; }
    private ServerRecovery() { }

    public static Result run(String backend, boolean artAvailable, BooleanSupplier cancelled, Attempt attempt) throws Exception {
        Mode mode = Mode.NATIVE;
        Result result = new Result("Сервер остановлен", Failure.NONE, false);
        while (!cancelled.getAsBoolean()) {
            result = attempt.run(mode);
            if (cancelled.getAsBoolean() || result.wasReady || !"vulkan".equals(backend)) return result;
            if (mode == Mode.NATIVE && result.failure == Failure.PIPELINE)
                mode = artAvailable ? Mode.VULKAN_ART : Mode.CPU;
            else if (mode == Mode.VULKAN_ART && (result.failure == Failure.PIPELINE || result.failure == Failure.STARTUP))
                mode = Mode.CPU;
            else return result;
        }
        return result;
    }
}
