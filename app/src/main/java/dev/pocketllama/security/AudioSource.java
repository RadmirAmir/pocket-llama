package dev.pocketllama.security;
import android.media.MediaDataSource;
import dev.pocketllama.voice.PcmFile;
import java.io.*;
/** Android player sees a virtual WAV; only requested ciphertext blocks are decrypted in memory. */
public final class AudioSource extends MediaDataSource {
    private final PcmFile audio;private final byte[] header;
    public AudioSource(PcmFile audio){this.audio=audio;header=audio.wavHeader();}
    @Override public synchronized int readAt(long position,byte[] buffer,int offset,int size)throws IOException {
        if(position<0)return -1;if(size==0)return 0;long total=getSize();if(position>=total)return -1;int count=(int)Math.min(size,total-position),done=0;
        if(position<44){int n=(int)Math.min(count,44-position);System.arraycopy(header,(int)position,buffer,offset,n);position+=n;done+=n;}
        if(done<count){int n=audio.readBytes(position-44,buffer,offset+done,count-done);if(n>0)done+=n;}return done;
    }
    @Override public long getSize(){return 44+audio.samples()*2;}
    @Override public void close()throws IOException{audio.close();}
}
