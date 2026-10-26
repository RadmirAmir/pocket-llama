import dev.pocketllama.voice.*;
import dev.pocketllama.security.ChunkedFile;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.KeyGenerator;

/** The production JNI and encrypted PCM reader against a public AMI meeting. */
public final class NemotronNativeSmoke {
    public static void main(String[] args)throws Exception {
        Path fixture=Path.of(args[1]),encrypted=Files.createTempFile("pocket-diar-", ".pcm");
        byte[] wav=Files.readAllBytes(fixture);
        ByteBuffer b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if(b.getInt(24)!=16000||b.getShort(22)!=1||b.getShort(34)!=16)throw new AssertionError("Expected 16k mono PCM16 WAV");
        int offset=12,bytes=0;
        while(offset+8<=wav.length){int length=b.getInt(offset+4);if(b.getInt(offset)==0x61746164){bytes=length;offset+=8;break;}offset+=8+length+(length&1);}
        if(bytes<=0||offset+bytes>wav.length)throw new AssertionError("Missing WAV data");
        KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);var key=generator.generateKey();
        try {
            try(PcmFile audio=new PcmFile(encrypted.toFile(),true,key)){
                for(int at=offset;at<offset+bytes;){int count=Math.min(16000,(offset+bytes-at)/2);short[] block=new short[count];for(int i=0;i<count;i++)block[i]=b.getShort(at+2*i);audio.append(block,count);Arrays.fill(block,(short)0);at+=count*2;}audio.checkpoint();
            }
            long began=System.nanoTime();List<SpeakerDiarization.Segment> segments;
            try(PcmFile audio=new PcmFile(encrypted.toFile(),false,key);NemotronDiarizer engine=new NemotronDiarizer(new File(args[0]))){
                segments=SpeakerDiarization.run(audio.samples(),audio::read,engine,()->{},(done,total)->{});
            }
            long elapsed=(System.nanoTime()-began)/1000000;Set<Integer> speakers=new TreeSet<>();
            for(var s:segments){speakers.add(s.speaker);if(s.startMs<0||s.endMs>bytes/32||s.endMs<=s.startMs)throw new AssertionError("Invalid output interval");}
            if(segments.size()<3||speakers.size()<2||!speakers.contains(1))throw new AssertionError("Missing meeting speakers: "+speakers);
            System.out.println("{\"platform\":\""+System.getProperty("os.name")+" "+System.getProperty("os.arch")+"\",\"engine\":\"nemotron-q8-v1\",\"samples\":"+bytes/2+",\"elapsedMs\":"+elapsed+",\"speakers\":\""+speakers+"\",\"segments\":"+segments.size()+"}");
            for(var s:segments)System.out.println(s.startMs+"\t"+s.endMs+"\t"+s.speaker);
            // A fresh stream must not retain the previous recording's channel cache.
            try(NemotronDiarizer silence=new NemotronDiarizer(new File(args[0]))){for(int i=0;i<3;i++)silence.push(new float[16000]);if(!SpeakerDiarization.segments(silence.finish(),48000).isEmpty())throw new AssertionError("Silent recording produced speakers");}
            System.out.println("PASS: real JNI, encrypted PCM, valid one-based speakers, streaming tail and fresh silent stream");
        }finally{Files.deleteIfExists(encrypted);Arrays.fill(wav,(byte)0);}
    }
}
