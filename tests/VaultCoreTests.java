import dev.pocketllama.security.*;
import dev.pocketllama.voice.PcmFile;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.SecretKey;

/** Production storage + migration code, with independently known plaintext and PBKDF2 vector. */
public final class VaultCoreTests {
 static int checks;static void check(boolean yes,String text){if(!yes)throw new AssertionError(text);checks++;}
 interface Work{void run()throws Exception;}
 static void fails(Work work,String text)throws Exception{try{work.run();throw new AssertionError(text);}catch(IOException|IllegalArgumentException expected){checks++;}}
 static byte[] bytes(ChunkedFile file)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] block=new byte[7777];long pos=0;int n;while((n=file.read(pos,block,0,block.length))>0){b.write(block,0,n);pos+=n;}return b.toByteArray();}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("pocket-vault-test");KeyGenerator kg=KeyGenerator.getInstance("AES");kg.init(256);SecretKey key=kg.generateKey();File audio=dir.resolve("00000000-0000-0000-0000-000000000001.pka").toFile();
  short[] pcm=new short[500000];byte[] expected=new byte[pcm.length*2];for(int i=0;i<pcm.length;i++){pcm[i]=(short)(i*31-12000);expected[i*2]=(byte)pcm[i];expected[i*2+1]=(byte)(pcm[i]>>8);}
  try(PcmFile f=new PcmFile(audio,true,key)){f.append(Arrays.copyOfRange(pcm,0,17001),17001);f.checkpoint();f.append(Arrays.copyOfRange(pcm,17001,pcm.length),pcm.length-17001);}
  check(PcmFile.repair(audio,key)==pcm.length,"all samples survive close/repair");
  try(PcmFile f=new PcmFile(audio,false,key)){for(int start:new int[]{0,16999,17001,32767,499997}){float[] data=f.read(start,3);for(int i=0;i<data.length;i++)check(data[i]==pcm[start+i]/32768f,"seek across authenticated blocks");}check(f.wavHeader()[0]=='R'&&ByteBuffer.wrap(f.wavHeader()).order(ByteOrder.LITTLE_ENDIAN).getInt(40)==expected.length,"virtual WAV header");}
  byte[] encrypted=Files.readAllBytes(audio.toPath());check(!Arrays.equals(Arrays.copyOf(encrypted,4),new byte[]{'R','I','F','F'}),"not a plaintext WAV");
  try(ChunkedFile f=new ChunkedFile(audio,false,key)){check(Arrays.equals(bytes(f),expected),"exact PCM bytes and unaligned random reads");check(f.read(0,new byte[0],0,0)==0,"empty read");}
  fails(()->new ChunkedFile(audio,false,kg.generateKey()),"wrong wrapping key must fail");
  File swapped=dir.resolve("00000000-0000-0000-0000-000000000002.pka").toFile();Files.copy(audio.toPath(),swapped.toPath());fails(()->new ChunkedFile(swapped,false,key),"whole-file substitution across recordings must fail");
  byte[] corrupt=encrypted.clone();corrupt[ChunkedFile.HEADER+4+12+37]^=1;Files.write(audio.toPath(),corrupt);fails(()->{try(ChunkedFile f=new ChunkedFile(audio,false,key)){f.verifyAll();}},"modified audio must fail authentication");Files.write(audio.toPath(),encrypted);
  try(RandomAccessFile f=new RandomAccessFile(audio,"rw")){f.seek(f.length());f.writeInt(65536);f.write(new byte[17]);}check(PcmFile.repair(audio,key)==pcm.length,"partial final write recovered");check(audio.length()==encrypted.length,"only incomplete tail truncated");
  File reordered=dir.resolve("two.pka").toFile();try(ChunkedFile f=new ChunkedFile(reordered,true,key)){f.append(new byte[ChunkedFile.BLOCK*2],0,ChunkedFile.BLOCK*2);}
  byte[] two=Files.readAllBytes(reordered.toPath());int record=4+12+ChunkedFile.BLOCK+16;byte[] first=Arrays.copyOfRange(two,ChunkedFile.HEADER,ChunkedFile.HEADER+record);System.arraycopy(two,ChunkedFile.HEADER+record,two,ChunkedFile.HEADER,record);System.arraycopy(first,0,two,ChunkedFile.HEADER+record,record);Files.write(reordered.toPath(),two);fails(()->{try(ChunkedFile f=new ChunkedFile(reordered,false,key)){f.verifyAll();}},"reordered blocks must fail");
  File legacy=dir.resolve("legacy.wav").toFile(),destination=dir.resolve("00000000-0000-0000-0000-000000000003.pka").toFile();try(PcmFile f=new PcmFile(audio,false,key);OutputStream out=new FileOutputStream(legacy)){out.write(f.wavHeader());out.write(expected);}
  VaultMigration.migrateStream(legacy,destination,key,true);check(legacy.isFile(),"migration copy does not delete source itself");byte[] committed=Files.readAllBytes(destination.toPath());VaultMigration.migrateStream(legacy,destination,key,true);check(Arrays.equals(committed,Files.readAllBytes(destination.toPath())),"resume validates committed copy without rewriting it");
  try(ChunkedFile f=new ChunkedFile(destination,false,key)){check(Arrays.equals(expected,bytes(f)),"legacy PCM content exactly preserved");}
  committed[committed.length-9]^=1;Files.write(destination.toPath(),committed);fails(()->VaultMigration.migrateStream(legacy,destination,key,true),"migration rejects bad committed copy");check(legacy.length()==expected.length+44,"old recording retained after failed migration");
  File raw=dir.resolve("old.tmp").toFile(),sealed=dir.resolve("orphan.pkb").toFile();Files.write(raw.toPath(),new byte[]{1,2,3,4,5});VaultMigration.migrateStream(raw,sealed,key,false);try(ChunkedFile f=new ChunkedFile(sealed,false,key)){check(Arrays.equals(bytes(f),new byte[]{1,2,3,4,5}),"odd-length legacy temporary data preserved encrypted");}
  byte[] hash=PinCrypto.derive("987654".toCharArray(),"0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII));StringBuilder hex=new StringBuilder();for(byte b:hash)hex.append(String.format(Locale.ROOT,"%02x",b&255));check(hex.toString().equals("1f07b7edbb167ebcb4e0ac2f39d7b7eec850d6194f16c7b78ecdde9f3e577f0c"),"independent PBKDF2-SHA256 600k vector");check(!PinCrypto.matches(hash,new byte[32]),"wrong PIN verifier");fails(()->PinCrypto.derive("123".toCharArray(),new byte[16]),"short PIN rejected");fails(()->PinCrypto.derive("12a456".toCharArray(),new byte[16]),"non-digits rejected");check(PinCrypto.delay(4)==0&&PinCrypto.delay(5)==30000&&PinCrypto.delay(6)==60000&&PinCrypto.delay(100)==3600000,"bounded progressive delay");
  GateSession.unlock();long epoch=GateSession.epoch();check(GateSession.isOpen(),"explicit session authorization");GateSession.lock();check(!GateSession.isOpen()&&GateSession.epoch()!=epoch,"background lock invalidates pending authorization epoch");long lockedEpoch=GateSession.epoch();GateSession.unlock();check(GateSession.isOpen()&&GateSession.epoch()==lockedEpoch,"only explicit fresh authorization reopens session");GateSession.lock();
  System.out.println("VaultCoreTests: "+checks+" checks passed");
 }
}
