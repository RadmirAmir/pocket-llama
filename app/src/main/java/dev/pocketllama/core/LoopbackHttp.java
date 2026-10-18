package dev.pocketllama.core;

import java.io.*;
import java.net.*;
import java.util.Locale;

/** One request per loopback socket; no proxy, redirects, disk cache or idle pool reuse. */
public final class LoopbackHttp {
    private LoopbackHttp(){}
    public static HttpURLConnection open(int port,String path,String key,int connectMs,int readMs)throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(Proxy.NO_PROXY);
        c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(connectMs);c.setReadTimeout(readMs);
        c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Connection","close");return c;
    }
    public static class Failure extends IOException {
        public Failure(String message){super(message);}
        public Failure(String message,Throwable cause){super(message,cause);}
    }
    public static final class HttpFailure extends Failure {
        public final int status;
        public HttpFailure(int status,String message){super(message);this.status=status;}
    }
    public static final class RecoveryFailure extends Failure {
        public final String stage;public final boolean unavailable;
        public RecoveryFailure(String stage,boolean unavailable,Throwable cause){
            super(unavailable?"Локальная LLM не отвечает после обрыва. Завершённые части сохранены; можно продолжить [SUMMARY_SERVER_LOST/"+stage+"]":"Соединение с локальной LLM повторно обрывается. Завершённые части сохранены; можно продолжить [SUMMARY_"+stage+"_EOF]",cause);
            this.stage=stage;this.unavailable=unavailable;
        }
    }
    public static boolean disconnected(Throwable error){
        for(int depth=0;error!=null&&depth<8;depth++,error=error.getCause()){
            if(error instanceof Failure||error instanceof java.io.InterruptedIOException)return false;
            if(error instanceof EOFException||error instanceof SocketException)return true;
            if(error instanceof IOException){String text=String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
                if(text.contains("unexpected end of stream")||text.contains("unexpected end of file")||text.contains("connection reset")||text.contains("broken pipe")||text.contains("socket closed")||text.contains("connection refused"))return true;
            }
        }return false;
    }
}
