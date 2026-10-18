package dev.pocketllama.security;
import java.io.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Authenticated append-only 64 KiB blocks; random access decrypts at most one block at a time. */
public final class ChunkedFile implements AutoCloseable {
    public static final int BLOCK=65536,HEADER=84;
    private static final byte[] MAGIC={'P','K','A','U','D','I','O',1};
    private final RandomAccessFile file;private final boolean writable;private final byte[] id=new byte[16],pending=new byte[BLOCK];
    private final ArrayList<Long> offsets=new ArrayList<>(),starts=new ArrayList<>();private final ArrayList<Integer> sizes=new ArrayList<>();
    private final byte[] binding;private SecretKey dataKey;private long length,validEnd;private int used,cached=-1;private byte[] cache;
    public ChunkedFile(File path,boolean create,SecretKey master)throws IOException {this(path,create,master,create);}
    public ChunkedFile(File path,boolean create,SecretKey master,boolean writable)throws IOException {
        this.writable=writable;String name=path.getName();name=name.matches("[a-f0-9-]{36}.*")?name.substring(0,36):name.replaceFirst("\\.(part|pending)$","");binding=name.getBytes(java.nio.charset.StandardCharsets.UTF_8);file=new RandomAccessFile(path,writable?"rw":"r");try{if(create){file.setLength(0);new SecureRandom().nextBytes(id);byte[] raw=new byte[32];new SecureRandom().nextBytes(raw);dataKey=new SecretKeySpec(raw,"AES");byte[] wrapped=encrypt(master,raw,wrapAad());Arrays.fill(raw,(byte)0);file.write(MAGIC);file.write(id);file.write(wrapped);file.getFD().sync();}else{byte[] magic=new byte[8];file.readFully(magic);if(!Arrays.equals(magic,MAGIC))throw new IOException("Неизвестный формат защищённого аудио");file.readFully(id);byte[] wrapped=new byte[60];file.readFully(wrapped);byte[] raw=decrypt(master,wrapped,wrapAad());if(raw.length!=32)throw new IOException("Ключ аудио повреждён");dataKey=new SecretKeySpec(raw,"AES");Arrays.fill(raw,(byte)0);}
            scan();if(writable&&file.length()!=validEnd){file.setLength(validEnd);file.getFD().sync();}
        }catch(Exception e){try{file.close();}catch(Exception ignored){}throw failure(e);}
    }
    private byte[] wrapAad(){return ByteBuffer.allocate(id.length+binding.length+8).put(MAGIC).put(id).put(binding).array();}
    private void scan()throws Exception {long position=HEADER;length=0;while(position<file.length()){
        if(file.length()-position<4)break;file.seek(position);int n=file.readInt();if(n<=0||n>BLOCK)throw new IOException("Повреждён блок аудио");if(file.length()-position<4L+12+n+16)break;
        offsets.add(position);starts.add(length);sizes.add(n);length+=n;position+=4L+12+n+16;
    }validEnd=position;}
    private static byte[] encrypt(SecretKey key,byte[] plain,byte[] aad)throws Exception {Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(aad);byte[] encrypted=cipher.doFinal(plain),out=new byte[12+encrypted.length];System.arraycopy(cipher.getIV(),0,out,0,12);System.arraycopy(encrypted,0,out,12,encrypted.length);return out;}
    private static byte[] decrypt(SecretKey key,byte[] encrypted,byte[] aad)throws Exception {Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,encrypted,0,12));cipher.updateAAD(aad);return cipher.doFinal(encrypted,12,encrypted.length-12);}
    private byte[] aad(long start,int size){return ByteBuffer.allocate(32).put(id).putLong(start).putInt(size).putInt(1).array();}
    public synchronized long length(){return length+used;}
    public synchronized void append(byte[] data,int offset,int n)throws IOException {if(!writable)throw new IOException("Только чтение");if(offset<0||n<0||offset+n>data.length)throw new IOException("Неверный буфер");while(n>0){int count=Math.min(n,BLOCK-used);System.arraycopy(data,offset,pending,used,count);used+=count;offset+=count;n-=count;if(used==BLOCK)flushBlock();}}
    private void flushBlock()throws IOException {if(used==0)return;try{byte[] data=Arrays.copyOf(pending,used);byte[] sealed;try{sealed=encrypt(dataKey,data,aad(length,used));}finally{Arrays.fill(data,(byte)0);}file.seek(validEnd);file.writeInt(used);file.write(sealed);offsets.add(validEnd);starts.add(length);sizes.add(used);validEnd+=4+sealed.length;length+=used;Arrays.fill(pending,0,used,(byte)0);used=0;}catch(Exception e){throw failure(e);}}
    public synchronized void checkpoint()throws IOException{if(writable){flushBlock();file.getFD().sync();}}
    private byte[] block(int i)throws IOException {if(cached==i)return cache;try{file.seek(offsets.get(i)+4);byte[] encrypted=new byte[12+sizes.get(i)+16];file.readFully(encrypted);byte[] plain=decrypt(dataKey,encrypted,aad(starts.get(i),sizes.get(i)));if(cache!=null)Arrays.fill(cache,(byte)0);cache=plain;cached=i;return plain;}catch(Exception e){throw failure(e);}}
    public synchronized int read(long position,byte[] output,int offset,int n)throws IOException {if(position<0||offset<0||n<0||offset+n>output.length)throw new IOException("Неверное окно аудио");if(n==0)return 0;if(position>=length)return -1;int count=(int)Math.min(n,length-position),done=0;
        int lo=0,hi=starts.size()-1,best=0;while(lo<=hi){int mid=(lo+hi)>>>1;if(starts.get(mid)<=position){best=mid;lo=mid+1;}else hi=mid-1;}
        while(done<count){byte[] data=block(best);int at=(int)(position-starts.get(best)),take=Math.min(count-done,data.length-at);System.arraycopy(data,at,output,offset+done,take);done+=take;position+=take;best++;}return done;
    }
    public synchronized void verifyAll()throws IOException{for(int i=0;i<offsets.size();i++)block(i);}
    public synchronized void close()throws IOException {try{checkpoint();}finally{Arrays.fill(pending,(byte)0);if(cache!=null)Arrays.fill(cache,(byte)0);dataKey=null;file.close();}}
    private static IOException failure(Exception e){return e instanceof IOException?(IOException)e:new IOException("Проверка шифрованного аудио не пройдена",e);}
}
