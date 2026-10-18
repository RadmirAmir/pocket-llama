package dev.pocketllama.security;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
/** Read-only legacy access, exclusively for migration. */
public final class LegacyWav implements AutoCloseable {
    private final RandomAccessFile file;public final long bytes;
    public LegacyWav(File path)throws IOException {file=new RandomAccessFile(path,"r");try{byte[] h=new byte[44];file.readFully(h);ByteBuffer b=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);if(!new String(h,0,4,StandardCharsets.US_ASCII).equals("RIFF")||!new String(h,8,4,StandardCharsets.US_ASCII).equals("WAVE")||!new String(h,36,4,StandardCharsets.US_ASCII).equals("data")||b.getShort(20)!=1||b.getShort(22)!=1||b.getInt(24)!=16000||b.getShort(34)!=16)throw new IOException("Старая запись повреждена. Оригинал сохранён");bytes=(file.length()-44)&~1L;}catch(IOException e){file.close();throw e;}}
    public int read(byte[] b)throws IOException{long left=44+bytes-file.getFilePointer();return left<=0?-1:file.read(b,0,(int)Math.min(b.length,left));}
    public void close()throws IOException{file.close();}
}
