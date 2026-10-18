package dev.pocketllama.voice;
import dev.pocketllama.security.ChunkedFile;
import javax.crypto.SecretKey;
import java.io.*;
import java.nio.*;
/** Encrypted 16kHz mono PCM, with no plaintext fallback. */
public final class PcmFile implements AutoCloseable {
 public static final int RATE=16000,HEADER=44;private final ChunkedFile file;
 public PcmFile(File path,boolean create,SecretKey key)throws IOException{file=new ChunkedFile(path,create,key);if((file.length()&1)!=0){file.close();throw new IOException("Неверная длина аудио");}}
 public synchronized void append(short[] data,int count)throws IOException{if(count<0||count>data.length||samples()+count>2000000000L)throw new IOException("Достигнут предел аудио");byte[] bytes=new byte[count*2];for(int i=0;i<count;i++){bytes[i*2]=(byte)data[i];bytes[i*2+1]=(byte)(data[i]>>8);}try{file.append(bytes,0,bytes.length);}finally{java.util.Arrays.fill(bytes,(byte)0);}}
 public void checkpoint()throws IOException{file.checkpoint();}public long samples(){return file.length()/2;}
 public synchronized float[] read(long start,int length)throws IOException{if(start<0||length<0||length>RATE*31)throw new IOException("Неверное окно аудио");int n=(int)Math.min(length,Math.max(0,samples()-start));byte[] bytes=new byte[n*2];if(n>0&&file.read(start*2,bytes,0,bytes.length)!=bytes.length)throw new EOFException();float[] values=new float[n];for(int i=0;i<n;i++)values[i]=(short)((bytes[i*2]&255)|(bytes[i*2+1]<<8))/32768f;java.util.Arrays.fill(bytes,(byte)0);return values;}
 public int readBytes(long offset,byte[] bytes,int at,int length)throws IOException{return file.read(offset,bytes,at,length);}
 public byte[] wavHeader(){long data=samples()*2;ByteBuffer b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);b.put(new byte[]{'R','I','F','F'}).putInt((int)(36+data)).put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1).putInt(RATE).putInt(RATE*2).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt((int)data);return b.array();}
 public static long repair(File path,SecretKey key)throws IOException{try(ChunkedFile f=new ChunkedFile(path,false,key,true)){f.verifyAll();return f.length()/2;}}
 public void close()throws IOException{file.close();}
}
