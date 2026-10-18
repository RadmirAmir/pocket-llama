package dev.pocketllama.models;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.Locale;
import java.util.regex.*;

/** One resumable transfer. A verified file is atomically installed; partial files are kept separately. */
public final class VerifiedDownload {
    public interface Progress {void update(long done,long total,String stage)throws Exception;}
    public interface Check {void run()throws Exception;}
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    public void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    private void check(Check check)throws Exception {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException("Загрузка приостановлена");check.run();}
    public File transfer(String address,long size,String sha,File part,File target,Progress progress,Check policy)throws Exception {
        if(size<1||!sha.matches("[a-f0-9]{64}"))throw new IOException("Неверные параметры загрузки");
        check(policy);mkdir(part.getParentFile());mkdir(target.getParentFile());
        if(target.isFile()&&target.length()==size&&sha256(target,policy).equals(sha)){progress.update(size,size,"Готово");return target;}
        if(part.length()>size&&!part.delete())throw new IOException("Не удалось сбросить незавершённую загрузку");
        if(part.getParentFile().getUsableSpace()<size-part.length()+256L*1024*1024)throw new IOException("Недостаточно места для модели и рабочего запаса");
        if(part.length()<size){
            long offset=part.length();HttpURLConnection c=open(address,offset);connection=c;
            try {
                check(policy);int code=c.getResponseCode();
                if(code==206){
                    Matcher m=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(String.valueOf(c.getHeaderField("Content-Range")));
                    if(!m.matches()||Long.parseLong(m.group(1))!=offset||Long.parseLong(m.group(3))!=size||Long.parseLong(m.group(2))<offset||Long.parseLong(m.group(2))>=size)throw new IOException("Сервер вернул неверный диапазон модели");
                    long length=c.getContentLengthLong();if(length>=0&&length!=Long.parseLong(m.group(2))-offset+1)throw new IOException("Сервер вернул неверную длину диапазона");
                }else if(code==200){offset=0;}else throw new IOException("Загрузка: HTTP "+code+". Можно продолжить позже");
                String encoding=c.getContentEncoding();if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("Неожиданное кодирование модели");
                long length=c.getContentLengthLong();if(code==200&&length>=0&&length!=size)throw new IOException("Размер модели на сервере изменился");
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part,offset>0)){
                    byte[] buffer=new byte[262144];long done=offset,last=0;int n;
                    try{while((n=in.read(buffer))!=-1){check(policy);if(n==0)continue;if(done+n>size)throw new IOException("Ответ сервера больше ожидаемой модели");out.write(buffer,0,n);done+=n;long now=System.nanoTime();if(now-last>500000000L){progress.update(done,size,"Загружаем");last=now;}}}
                    finally{out.getFD().sync();}
                    if(done!=size)throw new EOFException("Соединение прервалось. Загрузка продолжится с сохранённого места");
                }
            }finally{connection=null;c.disconnect();}
        }
        check(policy);progress.update(size,size,"Проверяем файл");
        if(!sha256(part,policy).equals(sha)){part.delete();throw new IOException("Контрольная сумма не совпала. Повторите загрузку");}
        check(policy);
        try{Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(part.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        progress.update(size,size,"Готово");return target;
    }
    private HttpURLConnection open(String address,long offset)throws Exception {
        URL url=new URL(address);boolean secure=url.getProtocol().equals("https");
        if(!secure&&!url.getProtocol().equals("http"))throw new IOException("Неизвестный протокол");
        for(int i=0;i<8;i++){
            if(cancelled)throw new InterruptedException();
            HttpURLConnection c=(HttpURLConnection)url.openConnection();connection=c;
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(30000);
            c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("User-Agent","PocketLlama/0.8.0");
            if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
            int code;try{code=c.getResponseCode();}catch(Exception e){c.disconnect();throw e;}
            if(code>=300&&code<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Неверный адрес перенаправления");url=new URL(url,location);if((secure&&!url.getProtocol().equals("https"))||(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))||url.getUserInfo()!=null)throw new IOException("Небезопасное перенаправление");continue;}
            return c;
        }throw new IOException("Слишком много перенаправлений");
    }
    public String sha256(File f,Check policy)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[1048576];
        try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(b))!=-1){check(policy);digest.update(b,0,n);}}
        StringBuilder out=new StringBuilder();for(byte x:digest.digest())out.append(String.format(Locale.ROOT,"%02x",x&255));return out.toString();
    }
    private static void mkdir(File dir)throws IOException{if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удалось создать папку моделей");}
}
