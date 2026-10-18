package dev.pocketllama.voice;
import java.io.*;
import java.nio.channels.*;
/** Cross-process exclusion: ASR and llama.cpp may never own model memory together. */
public final class ModelLease implements AutoCloseable {
    private final RandomAccessFile file;private final FileLock lock;
    public ModelLease(File directory)throws IOException {
        file=new RandomAccessFile(new File(directory,"heavy-model.lock"),"rw");
        FileLock l=null;
        try{l=file.getChannel().tryLock();if(l==null)throw new IOException("Другая модель ещё освобождает память. Повторите через несколько секунд.");lock=l;}
        catch(IOException|OverlappingFileLockException e){file.close();throw new IOException("Речевой процесс или LLM уже работает",e);}
    }
    public void close()throws IOException{try{lock.release();}finally{file.close();}}
}
