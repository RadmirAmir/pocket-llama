package dev.pocketllama.voice;

import ai.moonshine.voice.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** English ASR in the isolated :speech worker. All audio remains in memory. */
public final class MoonshineAsr implements SpeechRecognizer {
    private int handle;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    public MoonshineAsr(File directory)throws IOException {
        handle=JNI.moonshineLoadTranscriberFromFiles(directory.getAbsolutePath(),5,new Object[]{
            new TranscriberOption("word_timestamps","true"),
            new TranscriberOption("identify_speakers","false")});
        if(handle<0)throw new IOException("Moonshine Streaming Medium: "+JNI.moonshineErrorToString(handle));
    }
    public void cancel(){cancelled.set(true);}
    private void check()throws InterruptedException {
        if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedException("Transcription stopped");
    }
    public List<Word> recognize(float[] pcm,long offsetMs)throws Exception {
        check();if(pcm.length>16000*30)throw new IOException("Moonshine audio window exceeds 30 seconds");
        Transcript transcript=JNI.moonshineTranscribeWithoutStreaming(handle,pcm,16000,0);check();
        if(transcript==null||transcript.lines==null)throw new IOException("Moonshine returned no transcript");
        ArrayList<Word> result=new ArrayList<>();
        long duration=Math.max(1,pcm.length/16),limit=offsetMs+duration;
        for(TranscriptLine line:transcript.lines){
            if(line.words==null||line.words.isEmpty()){
                if(line.text!=null&&!line.text.trim().isEmpty())throw new IOException("Moonshine word timestamps are unavailable; transcript was not committed");
                continue;
            }
            for(WordTiming word:line.words){
                if(word.word==null||word.word.trim().isEmpty())continue;
                if(!Float.isFinite(word.start)||!Float.isFinite(word.end)||word.start<0||word.end<word.start)throw new IOException("Invalid Moonshine word timestamp");
                long start=Math.min(limit-1,offsetMs+Math.round(word.start*1000.0));
                long end=Math.min(limit,Math.max(start+1,offsetMs+Math.round(word.end*1000.0)));
                result.add(new Word(word.word.trim(),start,end));
            }
            if(line.audioData!=null)Arrays.fill(line.audioData,0);
        }
        result.sort(Comparator.comparingLong(w->w.startMs));return result;
    }
    public void close(){if(handle>=0){JNI.moonshineFreeTranscriber(handle);handle=-1;}}
}
