import dev.pocketllama.voice.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class VoiceCoreTests {
    static int passed;
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);passed++;}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("hold")){try(ModelLease lease=new ModelLease(new File(args[1]))){Files.writeString(Path.of(args[1],"ready"),"ready");Thread.sleep(30000);}return;}
        javax.crypto.SecretKey key=new javax.crypto.spec.SecretKeySpec(new byte[32],"AES");
        Path dir=Files.createTempDirectory("voice-core-");File wav=dir.resolve("recover.wav").toFile();
        short[] a=new short[16000];for(int i=0;i<a.length;i++)a[i]=(short)(i-8000);
        try(PcmFile f=new PcmFile(wav,true,key)){f.append(a,a.length);f.checkpoint();f.append(a,a.length);}
        check(PcmFile.repair(wav,key)==32000,"Crash recovery must keep audio written after the last header checkpoint");
        try(PcmFile f=new PcmFile(wav,false,key)){check(f.samples()==32000,"Recovered WAV length");float[] x=f.read(15999,3);check(Math.abs(x[0]-7999/32768f)<1e-7&&Math.abs(x[1]+8000/32768f)<1e-7,"Little-endian audio survives repair");}
        for(int rate:new int[]{8000,16000,44100,48000}){
            ArrayList<Float> out=new ArrayList<>();SincResampler r=new SincResampler(rate,out::add);
            for(int i=0;i<rate;i++)r.accept((float)(0.5*Math.sin(2*Math.PI*1000*i/rate)));r.finish();
            check(Math.abs(out.size()-16000)<=1,"Resampling must preserve duration: "+rate);
            double energy=0;for(int i=100;i<out.size()-100;i++)energy+=out.get(i)*out.get(i);double rms=Math.sqrt(energy/(out.size()-200));check(Math.abs(rms-Math.sqrt(0.125))<0.02,"Speech-band level is preserved: "+rate);
        }
        double[] energy={0};int[] count={0};SincResampler antiAlias=new SincResampler(48000,x->{if(count[0]++>100)energy[0]+=x*x;});for(int i=0;i<48000;i++)antiAlias.accept((float)Math.sin(2*Math.PI*18000*i/48000));antiAlias.finish();check(Math.sqrt(energy[0]/(count[0]-100))<0.01,"Downsampling rejects high-frequency aliasing");
        File lockDir=dir.resolve("lease").toFile();lockDir.mkdir();try(ModelLease lease=new ModelLease(lockDir)){try{new ModelLease(lockDir);throw new AssertionError("Duplicate lease accepted");}catch(IOException expected){passed++;}}
        try(ModelLease again=new ModelLease(lockDir)){passed++;}
        Process child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-XX:-UsePerfData","-cp",System.getProperty("java.class.path"),"VoiceCoreTests","hold",lockDir.getAbsolutePath()).redirectErrorStream(true).start();
        try{long limit=System.nanoTime()+5_000_000_000L;while(!new File(lockDir,"ready").exists()){if(System.nanoTime()>limit)throw new AssertionError("Child failed to acquire lease");Thread.sleep(20);}try{new ModelLease(lockDir);throw new AssertionError("Cross-process overlap accepted");}catch(IOException expected){passed++;}}finally{child.destroyForcibly();child.waitFor();}
        try(ModelLease recovered=new ModelLease(lockDir)){passed++;}
        SpeakerClusters clusters=new SpeakerClusters(2);check(clusters.add(new float[]{3,0,0})==1,"First speaker");check(clusters.add(new float[]{0,5,0})==2,"Distinct speaker");check(clusters.match(new float[]{0.1f,1,0}).speaker==2,"Centroid reassignment");check(clusters.match(new float[]{0,0,1}).speaker==0,"Unknown voice is not forced into a known identity");
        try{SpeakerClusters.normalize(new float[]{0,0});throw new AssertionError();}catch(IllegalArgumentException expected){passed++;}
        check(new SignalFeatures(false).frames(16000)==99,"GigaAM center=false frame count");check(new SignalFeatures(true).frames(16000)==98,"CAM++ snip-edges frame count");
        System.out.println("Voice core checks passed: "+passed);
    }
}
