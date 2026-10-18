import dev.pocketllama.photo.SuryaAssets;
import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
public final class BundledSuryaAssetsTests {
    static int checks;static void check(boolean v,String why){if(!v)throw new AssertionError(why);checks++;}
    public static void main(String[] args)throws Exception {
        // The production asset loader runs with file-backed Android adapters and the actual pinned weights.
        URL.setURLStreamHandlerFactory(protocol->{throw new AssertionError("Bundled setup attempted network: "+protocol);});
        Path root=Files.createTempDirectory("pocket-bundled-surya-");
        try{
            Context c=new Context(root.toFile(),new File(args[0]));SuryaAssets assets=new SuryaAssets(c);
            check(assets.status().getBoolean("available")&&assets.status().getBoolean("bundled")&&!assets.status().getBoolean("ready"),"cold installed bundle is available offline");
            final int[] progress={0};assets.prepare((n,t,s)->{check(s.equals("Подготавливаем встроенный OCR"),"local setup stage");check(n<=t,"bounded progress");progress[0]++;});
            check(progress[0]>0&&assets.status().getBoolean("ready"),"actual GGUF and projector verified offline");
            long modelTime=assets.model().lastModified(),projectorTime=assets.projector().lastModified();
            assets.prepare((n,t,s)->{throw new AssertionError("Verified files were prepared again");});
            check(modelTime==assets.model().lastModified()&&projectorTime==assets.projector().lastModified(),"subsequent setup reuses existing models");
            dev.pocketllama.DistributionConfig.enabled=false;check(!assets.status().getBoolean("available")&&!assets.status().getBoolean("ready"),"public capability gate");
            try{assets.prepare((n,t,s)->{});throw new AssertionError("Restricted model enabled in public profile");}catch(IOException expected){checks++;}
            check(assets.model().isFile()&&assets.model().lastModified()==modelTime,"capability gate preserves old data");
            dev.pocketllama.DistributionConfig.enabled=true;Path second=Files.createDirectory(root.resolve("second"));SuryaAssets cancelled=new SuryaAssets(new Context(second.toFile(),new File(args[0])));
            try{cancelled.prepare((n,t,s)->cancelled.cancel());throw new AssertionError("Cancellation ignored");}catch(InterruptedException expected){checks++;}
            check(!cancelled.model().exists()&&!cancelled.status().getBoolean("ready"),"cancelled setup is never marked ready");
            System.out.println("PASS: "+checks+" bundled Surya assertions with actual pinned weights; network prohibited");
        }finally{try(var files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception ignored){}});}}
    }
}
