package dev.pocketllama.voice;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;

/** Decode through Android codecs, mix channels, anti-alias to 16k, write incrementally. */
public final class AudioImporter {
    public interface Check{void check(long samples)throws Exception;}
    private AudioImporter(){}
    public static long decode(Context context,Uri uri,File output,Check check)throws Exception{
        return decode(context,uri,null,output,check);
    }
    public static long decode(Context context,MediaDataSource source,File output,Check check)throws Exception{
        return decode(context,null,source,output,check);
    }
    private static long decode(Context context,Uri uri,MediaDataSource source,File output,Check check)throws Exception{
        File temp=new File(output.getParentFile(),output.getName()+".part");MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
        try(PcmFile pcm=new PcmFile(temp,true,dev.pocketllama.security.Vault.key(context))){
            if(source!=null)extractor.setDataSource(source);else extractor.setDataSource(context,uri,null);int track=-1;MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){track=i;format=f;break;}}
            if(track<0)throw new IOException("В файле нет поддерживаемой аудиодорожки");extractor.selectTrack(track);
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(channels<1||channels>8)throw new IOException("Неподдерживаемое число каналов");
            decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);decoder.configure(format,null,null,0);decoder.start();
            final short[] block=new short[2048];final int[] used={0};final long[] written={0};
            SincResampler resampler=new SincResampler(rate,x->{block[used[0]++]=(short)Math.max(-32768,Math.min(32767,Math.round(x*32768)));if(used[0]==block.length){pcm.append(block,block.length);used[0]=0;written[0]+=block.length;check.check(written[0]);if(written[0]%(16000*2)<2048)pcm.checkpoint();}});
            boolean inputDone=false,outputDone=false;int encoding=AudioFormat.ENCODING_PCM_16BIT;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long idleAt=android.os.SystemClock.elapsedRealtime();
            while(!outputDone){check.check(written[0]);if(output.getParentFile().getUsableSpace()<64*1024*1024L)throw new IOException("Недостаточно места для декодированного аудио");
                if(!inputDone){int index=decoder.dequeueInputBuffer(10000);if(index>=0){ByteBuffer input=decoder.getInputBuffer(index);input.clear();int n=extractor.readSampleData(input,0);if(n<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{decoder.queueInputBuffer(index,0,n,Math.max(0,extractor.getSampleTime()),0);extractor.advance();}}}
                int index=decoder.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=decoder.getOutputFormat();if(f.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=rate)throw new IOException("Декодер изменил частоту аудио");channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;if(channels<1||channels>8)throw new IOException("Неподдерживаемые каналы");if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&encoding!=AudioFormat.ENCODING_PCM_FLOAT)throw new IOException("Декодер вернул неподдерживаемый PCM");}
                if(index>=0){idleAt=android.os.SystemClock.elapsedRealtime();ByteBuffer data=decoder.getOutputBuffer(index).order(ByteOrder.LITTLE_ENDIAN);data.position(info.offset);data.limit(info.offset+info.size);int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                    while(data.remaining()>=bytes*channels){double mixed=0;for(int c=0;c<channels;c++)mixed+=encoding==AudioFormat.ENCODING_PCM_FLOAT?data.getFloat():data.getShort()/32768.0;resampler.accept((float)(mixed/channels));}
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;decoder.releaseOutputBuffer(index,false);
                }
                if(android.os.SystemClock.elapsedRealtime()-idleAt>30000)throw new IOException("Аудиодекодер не отвечает");
                if(written[0]>16000L*6*3600)throw new IOException("Импорт ограничен шестью часами аудио");
            }
            resampler.finish();if(used[0]>0)pcm.append(block,used[0]);pcm.checkpoint();long samples=pcm.samples();check.check(samples);
            if(!temp.renameTo(output))throw new IOException("Не удалось сохранить импортированную запись");return samples;
        }finally{if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}extractor.release();temp.delete();}
    }
}
