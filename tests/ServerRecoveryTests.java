import dev.pocketllama.core.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static dev.pocketllama.core.ServerRecovery.*;

public final class ServerRecoveryTests {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    private static Result result(Failure failure, boolean ready) { return new Result(failure.name(), failure, ready); }
    private static List<Mode> sequence(String backend, boolean art, Result... results) throws Exception {
        List<Mode> seen = new ArrayList<>();
        ServerRecovery.run(backend, art, () -> false, mode -> {
            seen.add(mode);
            if (seen.size() > results.length) throw new AssertionError("Unexpected extra attempt");
            return results[seen.size() - 1];
        });
        return seen;
    }
    public static void main(String[] args) throws Exception {
        check(sequence("vulkan", true, result(Failure.NONE,true)).equals(List.of(Mode.NATIVE)), "Working GPU is not restarted");
        check(sequence("vulkan", true, result(Failure.PIPELINE,false),result(Failure.NONE,true)).equals(List.of(Mode.NATIVE,Mode.VULKAN_ART)), "Known driver compilation failure tries ART once");
        check(sequence("vulkan", true, result(Failure.PIPELINE,false),result(Failure.PIPELINE,false),result(Failure.STARTUP,false)).equals(List.of(Mode.NATIVE,Mode.VULKAN_ART,Mode.CPU)), "Both failed GPU launches get one CPU attempt with no loop");
        check(sequence("vulkan", true, result(Failure.PIPELINE,false),result(Failure.STARTUP,false),result(Failure.NONE,true)).equals(List.of(Mode.NATIVE,Mode.VULKAN_ART,Mode.CPU)), "ART loader failure gets the same bounded fallback");
        check(sequence("vulkan", false, result(Failure.PIPELINE,false),result(Failure.NONE,true)).equals(List.of(Mode.NATIVE,Mode.CPU)), "Missing ART bridge skips unavailable stage");
        check(sequence("vulkan", true, result(Failure.STARTUP,false)).equals(List.of(Mode.NATIVE)), "Unrelated initial failures are not classified as GPU compiler bugs");
        check(sequence("vulkan", true, result(Failure.PIPELINE,true)).equals(List.of(Mode.NATIVE)), "GPU failure after API ready never replays a request");
        check(sequence("vulkan", true, result(Failure.PIPELINE,false),result(Failure.PIPELINE,true)).equals(List.of(Mode.NATIVE,Mode.VULKAN_ART)), "Working ART server cannot silently switch during inference");
        for (String backend : List.of("cpu","opencl"))
            check(sequence(backend,true,result(Failure.PIPELINE,false)).equals(List.of(Mode.NATIVE)), "No recovery policy for other backend: " + backend);
        check(sequence("vulkan",true,result(Failure.RESOURCE,false)).equals(List.of(Mode.NATIVE)), "Initial memory or thermal stop is terminal");
        check(sequence("vulkan",true,result(Failure.PIPELINE,false),result(Failure.RESOURCE,false)).equals(List.of(Mode.NATIVE,Mode.VULKAN_ART)), "ART memory or thermal stop cannot load another model");
        AtomicBoolean cancelled = new AtomicBoolean(true);
        Result stopped = ServerRecovery.run("vulkan",true,cancelled::get,mode -> { throw new AssertionError("Started after stop"); });
        check(!stopped.wasReady && stopped.failure == Failure.NONE, "Stop before launch starts no process");
        List<Mode> seen = new ArrayList<>();cancelled.set(false);
        ServerRecovery.run("vulkan",true,cancelled::get,mode -> { seen.add(mode);cancelled.set(true);return result(Failure.PIPELINE,false); });
        check(seen.equals(List.of(Mode.NATIVE)), "Stop arriving with shader error prevents the next launch");
        boolean threw = false;
        try { ServerRecovery.run("vulkan",true,()->false,mode -> { throw new IOException("Memory plan refused"); }); }
        catch (IOException expected) { threw = true; }
        check(threw,"Preparation errors propagate without another allocation");
        List<String> server = List.of("/app/lib/libllama_vulkan.so","--model","/app/models/model with spaces.gguf","--api-key-file","/proc/self/fd/0");
        List<String> command = AndroidServerCommand.build(new File("/system/bin/app_process64"),new File("/app/lib/libpocket_vulkan.so"),server);
        check(command.subList(6,command.size()).equals(server),"JNI preserves argv boundaries and the stdin key path");
        check(command.get(0).equals("/system/bin/app_process64") && command.get(4).equals("dev.pocketllama.AndroidServerMain"),"ART starts the packaged Java entry point directly");
        check(command.contains("--application") && !command.contains("sh") && !command.contains("--api-key"),"No shell or credential in the launcher argv");
        check(BackendEnvironment.pipelineFailure("ggml_vulkan: Compute pipeline creation failed for mul_mat_vec_q4_k_f32_f32"),"Uppercase compiler diagnostics recognized");
        check(BackendEnvironment.pipelineFailure("vk::Device::createComputePipelines: ErrorUnknown"),"Plural Vulkan entry point recognized");
        check(!BackendEnvironment.pipelineFailure("libc++abi: terminating due to std::bad_alloc"),"Allocation failure remains distinct");
        ServerConfig config = new ServerConfig();config.modelName="test.gguf";config.backend="vulkan";
        GgufMetadata model = new GgufMetadata(1024L*MemoryPolicy.MIB,Map.of("general.architecture","llama"));
        MemoryPolicy.Plan raw = MemoryPolicy.plan(config,model,8L*1024*MemoryPolicy.MIB,6L*1024*MemoryPolicy.MIB);
        MemoryPolicy.Plan artPlan = MemoryPolicy.plan(config,model,8L*1024*MemoryPolicy.MIB,6L*1024*MemoryPolicy.MIB,0,true);
        check(artPlan.estimateBytes-raw.estimateBytes==192L*MemoryPolicy.MIB,"ART overhead is included in launch estimate");
        check(artPlan.budgetBytes==raw.budgetBytes,"ART does not raise the live RSS limit");
        System.out.println("PASS: "+assertions+" startup recovery assertions");
    }
}
