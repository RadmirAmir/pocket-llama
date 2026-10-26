import dev.pocketllama.voice.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Runs the exact mobile frontend/ONNX wrappers on desktop ORT for numerical QA. */
public final class VoiceHostProbe {
    static void write(String name,float[] values)throws Exception{ByteBuffer b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float x:values)b.putFloat(x);Files.write(Path.of(name),b.array());}
    public static void main(String[] args)throws Exception{
        if(args[0].equals("features")){int n=16000;float[] x=new float[n];for(int i=0;i<n;i++)x[i]=(float)(0.2*Math.sin(i*0.31)+0.1*Math.cos(i*1.11)+0.03*Math.sin(i*i*0.00013));write(args[1]+"-input.f32",x);write(args[1]+"-asr.f32",new SignalFeatures(false).extract(x));write(args[1]+"-speaker.f32",new SignalFeatures(true).extract(x));return;}
        File models=new File(args[1]);try(PcmFile audio=new PcmFile(new File(args[2]),false)){
            if(args[0].equals("asr"))try(SpeechModels.Asr asr=new SpeechModels.Asr(models,2)){long started=System.nanoTime();for(long pos=0;pos<audio.samples();pos+=192000){for(SpeechRecognizer.Word w:asr.recognize(audio.read(pos,(int)Math.min(192000,audio.samples()-pos)),pos/16))System.out.println(w.startMs+"\t"+w.endMs+"\t"+w.text);}System.out.println("elapsed_ms="+(System.nanoTime()-started)/1_000_000);}
            if(args[0].equals("vad"))try(SpeechModels.Vad vad=new SpeechModels.Vad(models)){for(long pos=0;pos<audio.samples();pos+=512){float[] block=audio.read(pos,512);System.out.println(pos/16+"\t"+vad.probability(block,block.length));}}
            if(args[0].equals("speaker"))try(SpeechModels.Speaker sp=new SpeechModels.Speaker(models,2)){float[] a=sp.embed(audio.read(0,48000)),b=sp.embed(audio.read(48000,48000));System.out.println("same_speaker_similarity="+SpeakerClusters.dot(a,b));write(args[3],a);}
        }
    }
}
