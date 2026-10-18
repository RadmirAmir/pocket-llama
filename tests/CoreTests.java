import dev.pocketllama.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CoreTests {
    private static int assertions;
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Throwing action, String message) throws Exception {
        boolean failed = false; try { action.run(); } catch (IOException | IllegalArgumentException e) { failed = true; }
        check(failed, message);
    }
    private interface Throwing { void run() throws Exception; }
    private static void u32(OutputStream o, long n) throws IOException { for (int i = 0; i < 4; i++) o.write((int)(n >>> (8 * i)) & 255); }
    private static void u64(OutputStream o, long n) throws IOException { for (int i = 0; i < 8; i++) o.write((int)(n >>> (8 * i)) & 255); }
    private static void str(OutputStream o, String s) throws IOException { byte[] b = s.getBytes(StandardCharsets.UTF_8); u64(o, b.length); o.write(b); }
    private static File fixture(File dir, long split, boolean cutArray) throws IOException {
        File f = new File(dir, UUID.randomUUID()+".gguf");
        try (OutputStream o = new FileOutputStream(f)) {
            u32(o, 0x46554747); u32(o, 3); u64(o, 1); u64(o, 8);
            str(o,"general.architecture");u32(o,8);str(o,"qwen3");
            str(o,"tokenizer.ggml.pre");u32(o,8);str(o,"qwen2");
            str(o,"qwen3.block_count");u32(o,4);u32(o,32);
            str(o,"qwen3.embedding_length");u32(o,4);u32(o,2048);
            str(o,"qwen3.attention.head_count");u32(o,4);u32(o,16);
            str(o,"qwen3.attention.head_count_kv");u32(o,4);u32(o,4);
            str(o,"split.count");u32(o,2);o.write((int)split);o.write(0);
            str(o,"tokenizer.ggml.tokens");u32(o,9);u32(o,8);u64(o,3);
            str(o,"a");str(o,"русский");if (!cutArray) str(o,"ignored-token");
        }
        return f;
    }
    private static File qwenFixture(File dir, boolean[] mask) throws IOException {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("general.architecture", "qwen35");
        values.put("qwen35.block_count", 32);
        values.put("qwen35.embedding_length", 2560);
        values.put("qwen35.attention.head_count", 16);
        values.put("qwen35.attention.head_count_kv", 4);
        values.put("qwen35.attention.key_length", 256);
        values.put("qwen35.attention.value_length", 256);
        values.put("qwen35.full_attention_interval", 4);
        values.put("qwen35.ssm.inner_size", 4096);
        values.put("qwen35.ssm.state_size", 128);
        values.put("qwen35.ssm.conv_kernel", 4);
        values.put("qwen35.ssm.group_count", 16);
        if (mask != null) values.put("qwen35.attention.recurrent_layers", mask);
        File f = new File(dir, "qwen-" + UUID.randomUUID() + ".gguf");
        try (OutputStream o = new FileOutputStream(f)) {
            u32(o,0x46554747);u32(o,3);u64(o,1);u64(o,values.size());
            for (Map.Entry<String,Object> e : values.entrySet()) {
                str(o,e.getKey());
                if (e.getValue() instanceof String) { u32(o,8);str(o,(String)e.getValue()); }
                else if (e.getValue() instanceof boolean[]) {
                    boolean[] bits = (boolean[]) e.getValue();u32(o,9);u32(o,7);u64(o,bits.length);
                    for (boolean bit : bits) o.write(bit ? 1 : 0);
                } else { u32(o,4);u32(o,((Number)e.getValue()).longValue()); }
            }
        }
        return f;
    }
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]); dir.mkdirs();
        GgufMetadata parsed = GgufReader.read(fixture(dir,1,false));
        check(parsed.architecture().equals("qwen3"),"Architecture");
        check(parsed.number("block_count",0)==32,"Little-endian uint32");
        check(!parsed.values.containsKey("tokenizer.ggml.tokens"),"Do not retain tokenizer array");
        check(parsed.values.get("tokenizer.ggml.pre").equals("qwen2"),"Keep bounded pre-tokenizer identity for model profile");
        check(GenerationProfile.from(parsed)==GenerationProfile.STANDARD,"Qwen does not inherit MiniCPM settings");
        check(GenerationProfile.from(new GgufMetadata(1,Map.of("general.architecture","llama","tokenizer.ggml.pre","minicpm5")))==GenerationProfile.MINICPM5,"Detect MiniCPM5 through GGUF tokenizer even with renamed file");
        File fingerprintFile=new File(dir,"fingerprint.bin");java.nio.file.Files.write(fingerprintFile.toPath(),"abc".getBytes(StandardCharsets.UTF_8));
        check(ModelFingerprint.sha256(fingerprintFile,()->false).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"Model fingerprint matches published SHA256 test vector");
        rejects(()->ModelFingerprint.sha256(fingerprintFile,()->true),"Stop model hashing without starting inference");
        check(ModelFingerprint.reference("unknown").contains("не означает повреждение"),"Unknown model versions are not mislabeled corrupt");
        rejects(()->GgufReader.read(fixture(dir,2,false)),"Reject split model");
        rejects(()->GgufReader.read(fixture(dir,1,true)),"Reject truncated string array");
        File malicious = new File(dir,"bad.gguf");
        try(OutputStream o=new FileOutputStream(malicious)){u32(o,0x46554747);u32(o,3);u64(o,1);u64(o,1);u64(o,Long.MAX_VALUE);}
        rejects(()->GgufReader.read(malicious),"Bound untrusted string length");
        ServerConfig c = new ServerConfig();c.modelName="test.gguf";
        GgufMetadata model = new GgufMetadata(2L*1024*MemoryPolicy.MIB,new HashMap<>(parsed.values));
        MemoryPolicy.Plan plan = MemoryPolicy.plan(c,model,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB);
        check(plan.fits(),"Small model fits budget");
        check(!plan.approximateKv,"Conventional GQA metadata estimate");
        // 32 layers, 4 KV heads, 128 dimensions each, 2048 tokens, two F16 caches, 12% padding.
        check(plan.kvBytes==(long)Math.ceil(128*MemoryPolicy.MIB*1.12),"KV size has both K and V");
        c.slots=2;
        MemoryPolicy.Plan two = MemoryPolicy.plan(c,model,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB);
        check(two.kvBytes==2*plan.kvBytes || two.kvBytes==2*plan.kvBytes-1,"Slots multiply total context");
        c.cacheK="q8_0";
        MemoryPolicy.Plan quant = MemoryPolicy.plan(c,model,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB);
        check(quant.kvBytes<two.kvBytes,"Quantized K lowers KV estimate");
        c.slots=1;
        check(!MemoryPolicy.plan(c,model,12L*1024*MemoryPolicy.MIB,1536L*MemoryPolicy.MIB).fits(),"Reserve prevents unsafe load");
        c.budgetMiB=1024;
        check(!MemoryPolicy.plan(c,model,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB).fits(),"Respect requested cap");
        c.budgetMiB=0;
        Map<String,Object> hybrid=new HashMap<>(model.values);hybrid.put("general.architecture","qwen3next");
        check(MemoryPolicy.plan(c,new GgufMetadata(model.fileBytes,hybrid),12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB).approximateKv,"Hybrid uses explicit fallback");
        c.cacheK="f16";
        GgufMetadata qwenParsed=GgufReader.read(qwenFixture(dir,null));
        check(qwenParsed.number("ssm.inner_size",0)==4096,"Read Qwen recurrent dimensions");
        GgufMetadata qwen=new GgufMetadata(4_480_000_000L,qwenParsed.values);
        MemoryPolicy.Plan qwenPlan=MemoryPolicy.plan(c,qwen,12L*1024*MemoryPolicy.MIB,3L*1024*MemoryPolicy.MIB);
        check(!qwenPlan.approximateKv,"Qwen 3.5 does not use unknown KV reserve");
        check(qwenPlan.kvBytes<512L*MemoryPolicy.MIB,"Qwen 4B short context has bounded attention and state");
        check(!qwenPlan.fits(),"Limited available RAM is reported as a warning");
        check(qwenPlan.blockReason(c)==null,"Advisory estimate permits controlled native load");
        check(qwenPlan.budgetBytes>qwenPlan.availableBudgetBytes,"RSS budget is independent of startup free RAM");
        MemoryPolicy.Plan moreFree=MemoryPolicy.plan(c,qwen,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB);
        check(moreFree.budgetBytes==qwenPlan.budgetBytes,"Background apps do not change automatic RSS ceiling");
        c.strictMemory=true;
        check(qwenPlan.blockReason(c)!=null,"Opt-in strict mode blocks a low-memory estimate");
        c.strictMemory=false;c.budgetMiB=1024;
        check(MemoryPolicy.plan(c,qwen,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB).blockReason(c)!=null,"Manual cap below weights cannot be bypassed");
        c.budgetMiB=0;
        check(MemoryPolicy.plan(c,qwen,2L*1024*MemoryPolicy.MIB,MemoryPolicy.MIB).blockReason(c)!=null,"Physically too-large weights are rejected");
        c.unknownKvMiB=16384;
        check(MemoryPolicy.plan(c,qwen,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB).kvBytes==qwenPlan.kvBytes,"Manual fallback cannot inflate known Qwen memory");
        c.unknownKvMiB=1024;c.contextPerSlot=4096;
        check(MemoryPolicy.plan(c,qwen,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB).kvBytes>qwenPlan.kvBytes,"Qwen attention memory grows with context");
        c.contextPerSlot=2048;c.slots=2;
        MemoryPolicy.Plan qwenTwo=MemoryPolicy.plan(c,qwen,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB);
        check(Math.abs(qwenTwo.kvBytes-2*qwenPlan.kvBytes)<=1,"Qwen recurrent state scales with slots");
        c.slots=1;
        boolean[] mask=new boolean[32];Arrays.fill(mask,true);
        GgufMetadata masked=GgufReader.read(qwenFixture(dir,mask));
        check(masked.values.get("qwen35.attention.recurrent_layers") instanceof boolean[],"Read bounded recurrent layer mask");
        check(!MemoryPolicy.plan(c,new GgufMetadata(qwen.fileBytes,masked.values),12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB).approximateKv,"Explicit recurrent masks are supported");
        masked=GgufReader.read(qwenFixture(dir,new boolean[3]));
        check(MemoryPolicy.plan(c,new GgufMetadata(qwen.fileBytes,masked.values),12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB).approximateKv,"Malformed mask falls back instead of underestimating");
        check(MemoryPolicy.readMemAvailable(new StringReader("MemTotal: 12000000 kB\nMemAvailable: 3500000 kB\n"))==3_584_000_000L,"Use reclaimable MemAvailable, not MemTotal");
        check(MemoryPolicy.readMemAvailable(new StringReader("MemFree: 1000 kB\n"))==-1,"Missing available memory remains unknown");
        check(MemoryPolicy.readMemAvailable(new StringReader("MemAvailable: -1 kB\n"))==-1,"Negative memory is invalid");
        check(MemoryPolicy.readMemAvailable(new StringReader("MemAvailable: 99999999999999999 kB\n"))==-1,"Overflowed memory is invalid");
        check(MemoryPolicy.readMemAvailable(new StringReader("MemAvailable: 12 MB\n"))==-1,"Memory units are checked");
        c.cacheV="q8_0";
        List<String> command=ServerCommand.build(new File("/safe/native/libllama_cpu.so"),new File("/safe/model with spaces.gguf"),new File("/safe/server.key"),c);
        check(command.get(command.indexOf("--ctx-size")+1).equals("2048"),"Explicit context");
        check(command.get(command.indexOf("--flash-attn")+1).equals("on"),"Quantized V requires flash attention");
        check(command.contains("/safe/model with spaces.gguf"),"Path remains one argv item");
        check(!command.contains("--api-key"),"Secret not placed in argv");
        check(command.get(command.indexOf("--device")+1).equals("none"),"CPU mode disables GPU");
        check(!command.contains("--gpu-layers"),"CPU does not pass GPU-only option and emit a misleading warning");
        check(command.get(command.indexOf("--cache-ram")+1).equals("0"),"Mobile prompt cache is disabled");
        check(command.get(command.indexOf("--ctx-checkpoints")+1).equals("1"),"Recurrent checkpoints are bounded");
        check(command.contains("--no-cache-idle-slots"),"No idle-slot cache with disabled RAM cache");
        check(!command.contains("--reasoning-budget")&&command.get(command.indexOf("--batch-size")+1).equals("256"),"Chat/API keeps its existing reasoning and batch configuration");
        ServerConfig summaryConfig=new ServerConfig();summaryConfig.modelName="test.gguf";summaryConfig.backend="vulkan";summaryConfig.projectorName="vision.gguf";summaryConfig.contextPerSlot=32768;summaryConfig.slots=2;summaryConfig.threads=8;summaryConfig.lan=true;summaryConfig.stayAwake=false;
        SummaryProfile.apply(summaryConfig);summaryConfig.validate();
        check(summaryConfig.contextPerSlot==4096&&summaryConfig.slots==1&&summaryConfig.backend.equals("cpu")&&summaryConfig.projectorName.isEmpty()&&!summaryConfig.lan&&summaryConfig.stayAwake,"Summary profile is bounded, text-only and local");
        check(summaryConfig.threads==4&&SummaryProfile.context(512)==2048&&SummaryProfile.context(2048)==2048,"Summary context and CPU limits stay consistent");
        List<String> summaryCommand=ServerCommand.build(new File("/safe/native/libllama_cpu.so"),new File("/safe/test.gguf"),new File("/safe/key"),summaryConfig,true);
        check(summaryCommand.get(summaryCommand.indexOf("--reasoning-budget")+1).equals("0")&&summaryCommand.contains("--jinja"),"Summary native reasoning is disabled");
        check(summaryCommand.get(summaryCommand.indexOf("--batch-size")+1).equals("128")&&summaryCommand.get(summaryCommand.indexOf("--ubatch-size")+1).equals("32"),"Summary uses smaller compute batches");
        Map<String,String> env = new HashMap<>();env.put("LD_LIBRARY_PATH","/safe/lib");
        env.put("GGML_VK_FORCE_DMMV_SHMEM","1");
        BackendEnvironment.apply(env,c);
        check(!env.containsKey("GGML_VK_FORCE_DMMV_SHMEM"),"CPU never inherits Vulkan compatibility flags");
        check(env.get("LD_LIBRARY_PATH").equals("/safe/lib"),"Profile preserves process library path");
        check(env.get("POCKET_LLAMA_CPU_MODE").equals("auto"),"CPU acceleration is selected before native start");
        c.cpuAcceleration=false;BackendEnvironment.apply(env,c);
        check(env.get("POCKET_LLAMA_CPU_MODE").equals("generic"),"CPU comparison uses the explicit generic profile");c.cpuAcceleration=true;
        c.backend="vulkan";
        rejects(c::validate,"Compatibility rejects quantized V instead of silently changing cache");
        c.cacheV="f16";
        List<String> compatible=ServerCommand.build(new File("/safe/libllama_vulkan.so"),new File("/safe/test.gguf"),new File("/safe/key"),c);
        check(compatible.get(compatible.indexOf("--flash-attn")+1).equals("off"),"Compatibility disables Flash Attention");
        check(compatible.get(compatible.indexOf("--gpu-layers")+1).equals("99"),"Compatibility retains actual GPU offload");
        BackendEnvironment.apply(env,c);
        check(!env.containsKey("POCKET_LLAMA_CPU_MODE"),"Vulkan does not inherit a CPU profile");
        check(env.get("GGML_VK_FORCE_DMMV_SHMEM").equals("1"),"Native shared-memory variant explicitly selected");
        check(env.containsKey("GGML_VK_DISABLE_COOPMAT") && env.containsKey("GGML_VK_DISABLE_COOPMAT2") && env.containsKey("GGML_VK_DISABLE_INTEGER_DOT_PRODUCT"),"Profile excludes optimized matrix and int-dot kernels");
        c.vulkanCompatibility=false;BackendEnvironment.apply(env,c);
        check(env.size()==1,"Turning compatibility off clears all its flags");
        c.cacheV="q8_0";
        check(ServerCommand.build(new File("/safe/libllama_vulkan.so"),new File("/safe/test.gguf"),new File("/safe/key"),c).contains("on"),"Normal Vulkan still permits quantized V and Flash Attention");
        check(BackendEnvironment.pipelineFailure("decode() failed: vk::Device::createComputePipeline: ErrorUnknown"),"Exact reported driver failure is recognized");
        check(!BackendEnvironment.pipelineFailure("out of memory") && !BackendEnvironment.pipelineFailure(null),"Memory failure is not diagnosed as shader compilation");
        c.backend="cpu";c.vulkanCompatibility=true;
        c.projectorName="mmproj-test.gguf";
        List<String> visionCommand=ServerCommand.build(new File("/app/lib/libllama_cpu.so"),new File("/app/files/models/model.gguf"),new File("/app/files/key"),c);
        check(visionCommand.get(visionCommand.indexOf("--mmproj")+1).equals("/app/files/projectors/mmproj-test.gguf"),"Projector uses private, bounded path");
        check(visionCommand.contains("--no-mmproj-offload"),"Image encoder uses CPU for mobile compatibility");
        MemoryPolicy.Plan visionPlan=MemoryPolicy.plan(c,qwen,12L*1024*MemoryPolicy.MIB,8L*1024*MemoryPolicy.MIB,700L*MemoryPolicy.MIB);
        check(visionPlan.fileBytes==qwen.fileBytes+700L*MemoryPolicy.MIB,"Memory plan includes projector weights");
        check(visionPlan.estimateBytes>moreFree.estimateBytes,"Projector requires encoder work reserve");
        c.projectorName="../../bad.gguf";rejects(c::validate,"No projector traversal");c.projectorName="";
        check(TextAttachment.decode("Привет, мир!".getBytes(StandardCharsets.UTF_8)).equals("Привет, мир!"),"UTF-8 text attachment");
        byte[] utf16="Привет".getBytes(StandardCharsets.UTF_16LE);
        byte[] marked=new byte[utf16.length+2];marked[0]=(byte)0xFF;marked[1]=(byte)0xFE;System.arraycopy(utf16,0,marked,2,utf16.length);
        check(TextAttachment.decode(marked).equals("Привет"),"UTF-16 BOM text attachment");
        check(TextAttachment.decode("Русский текст".getBytes(java.nio.charset.Charset.forName("windows-1251"))).equals("Русский текст"),"Windows-1251 attachment");
        rejects(()->TextAttachment.decode(new byte[]{65,0,66}),"Binary NUL is not sent as text");
        rejects(()->TextAttachment.decode("%PDF-1.7".getBytes(StandardCharsets.UTF_8)),"PDF is not fabricated into text");
        rejects(()->TextAttachment.decode(new byte[]{'P','K',3,4}),"DOCX/ZIP container is not text");
        rejects(()->TextAttachment.decode(new byte[TextAttachment.MAX_BYTES+1]),"Bound text attachment size");
        rejects(()->TextAttachment.readLimited(new ByteArrayInputStream(new byte[32]),31),"Bound streaming file read");
        check(TextAttachment.readLimited(new ByteArrayInputStream(new byte[32]),32).length==32,"Exact file read boundary");
        SseReader sse=new SseReader(new StringReader(": ping\r\nevent: chunk\r\ndata: first\r\ndata: second\r\n\r\ndata: [DONE]\r\n\r\n"));
        check(sse.next().equals("first\nsecond"),"SSE joins multiline data and skips comments");
        check(sse.next().equals("[DONE]"),"SSE completion sentinel");
        check(sse.next()==null,"SSE EOF");
        check(new SseReader(new StringReader("data: last")).next().equals("last"),"SSE trailing event without blank separator");
        rejects(()->new SseReader(new StringReader("data: {\"choices\":["),true).next(),"Strict HTTP SSE reports a truncated frame as EOF");
        check(new SseReader(new StringReader("data: [DONE]"),true).next().equals("[DONE]"),"Strict SSE accepts explicit terminal sentinel at EOF");
        rejects(()->new SseReader(new StringReader("data: "+String.join("",Collections.nCopies(140000,"x"))+"\n\n")).next(),"Bound SSE event size");
        c.modelName="../test.gguf";rejects(c::validate,"No model path traversal");c.modelName="test.gguf";
        c.slots=3;rejects(c::validate,"Bound server concurrency");c.slots=1;
        MemoryPolicy.PressureGate gate = new MemoryPolicy.PressureGate();
        check(!gate.shouldStop(100,200,false,-1,1000),"One low-memory sample");
        check(!gate.shouldStop(500,200,false,-1,1000),"Reset pressure sample");
        check(!gate.shouldStop(100,200,false,-1,1000),"First sustained sample");
        check(!gate.shouldStop(100,200,false,-1,1000),"Second sustained sample");
        check(gate.shouldStop(100,200,false,-1,1000),"Third sustained sample stops");
        check(new MemoryPolicy.PressureGate().shouldStop(500,200,true,-1,1000),"Android lowMemory stops immediately");
        gate=new MemoryPolicy.PressureGate();
        check(!gate.shouldStop(500,200,false,1001,1000),"RSS cap first sample");
        check(!gate.shouldStop(500,200,false,1001,1000),"RSS cap second sample");
        check(gate.shouldStop(500,200,false,1001,1000),"RSS cap sustained sample");
        System.out.println("PASS: "+assertions+" assertions (GGUF parser, memory policy, argv, pressure gate)");
    }
}
