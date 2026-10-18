package dev.pocketllama.video;
import android.media.MediaDataSource;
import dev.pocketllama.security.ChunkedFile;
import java.io.*;
import javax.crypto.SecretKey;

/** Random access to a downloaded container, decrypted one block at a time. */
public final class EncryptedMediaSource extends MediaDataSource {
    private final ChunkedFile file;
    public EncryptedMediaSource(File path,SecretKey key)throws IOException{file=new ChunkedFile(path,false,key);}
    @Override public int readAt(long position,byte[] buffer,int offset,int size)throws IOException{return file.read(position,buffer,offset,size);}
    @Override public long getSize(){return file.length();}
    @Override public void close()throws IOException{file.close();}
}
