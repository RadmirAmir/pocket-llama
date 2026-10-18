package dev.pocketllama.video;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.regex.*;

/** Shared text is data, never command-line options or a shell command. */
public final class VideoUrl {
    private VideoUrl(){}
    public static String parse(String text)throws IOException {
        if(text==null||text.length()>12000)throw new IOException("Вставьте ссылку на один ролик");
        Matcher match=Pattern.compile("https?://[^\\s<>\"\\p{Cntrl}]+",Pattern.CASE_INSENSITIVE).matcher(text);
        if(!match.find())throw new IOException("Нужна ссылка, начинающаяся с https:// или http://");
        String url=match.group().replaceFirst("[.,;!)}\\]]+$","");
        try {
            URI uri=new URI(url);String host=uri.getHost();
            if(url.length()>4096||host==null||uri.getRawUserInfo()!=null)throw new Exception();
            host=host.toLowerCase(Locale.ROOT);
            if(host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||!host.contains(".")||host.contains(":")||host.matches("[0-9.]+")||host.startsWith("["))throw new Exception();
            if(uri.getPort()!=-1&&uri.getPort()!=80&&uri.getPort()!=443)throw new Exception();
            return uri.toASCIIString();
        }catch(Exception e){throw new IOException("Нужна публичная ссылка на видеоплатформу без логина в адресе");}
    }
    public static String host(String url){try{return new URI(url).getHost().replaceFirst("^www\\.","");}catch(Exception e){return "Видео";}}
    public static String at(String raw,long milliseconds)throws IOException {
        String url=parse(raw);URI uri=URI.create(url);String host=uri.getHost().toLowerCase(Locale.ROOT);
        // Timestamps are platform-specific. Preserve the original URL for other sites.
        if(!(host.equals("youtu.be")||host.equals("youtube.com")||host.endsWith(".youtube.com")))return url;
        String base=url.split("#",2)[0];int split=base.indexOf('?');java.util.ArrayList<String> query=new java.util.ArrayList<>();
        if(split>=0){for(String part:base.substring(split+1).split("&"))if(!part.isEmpty()&&!part.matches("^(t|start)=.*"))query.add(part);base=base.substring(0,split);}
        query.add("t="+Math.max(0,milliseconds/1000));return base+"?"+String.join("&",query);
    }
}
