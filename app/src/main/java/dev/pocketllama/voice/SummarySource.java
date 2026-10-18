package dev.pocketllama.voice;
/** Storage-independent, streaming interface for the token-budgeted summarizer. */
public interface SummarySource {
    interface Reader extends AutoCloseable {String next()throws Exception;void close()throws Exception;}
    Reader turns()throws Exception;
    Reader notes(int pass)throws Exception;
    String note(int pass,int index)throws Exception;
    void note(int pass,int index,String text)throws Exception;
    boolean containsTurn(int index)throws Exception;
}
