package dev.pocketllama.voice;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** All model bytes are in the APK. No model or audio downloads at runtime. */
public final class SpeechAssets {
    private SpeechAssets(){}
    public static File prepare(Context c)throws Exception{
        File dir=new File(c.getFilesDir(),"speech-models-v1");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Нет места для речевых моделей");
        JSONObject manifest;try(InputStream in=c.getAssets().open("speech/manifest.json")){manifest=new JSONObject(read(in));}
        JSONArray files=manifest.getJSONArray("files");
        // The caller owns ModelLease, so first-use extraction cannot race another speech job.
        for(int i=0;i<files.length();i++){JSONObject entry=files.getJSONObject(i);String name=entry.getString("name");if(!name.matches("[a-zA-Z0-9._-]+"))throw new IOException("Некорректный пакет моделей");
            File target=new File(dir,name);if(target.length()==entry.getLong("bytes")&&new File(dir,name+".ok").isFile())continue;
            if(dir.getUsableSpace()<entry.getLong("bytes")+64*1024*1024L)throw new IOException("Недостаточно места для речевых моделей (нужно около 260 МБ)");
            File part=new File(dir,name+".part");MessageDigest md=MessageDigest.getInstance("SHA-256");long count=0;
            try(InputStream in=c.getAssets().open("speech/"+name);FileOutputStream out=new FileOutputStream(part)){byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);md.update(b,0,n);count+=n;}out.getFD().sync();}
            StringBuilder hash=new StringBuilder();for(byte b:md.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(count!=entry.getLong("bytes")||!hash.toString().equals(entry.getString("sha256"))){part.delete();throw new IOException("Проверка модели не прошла: "+name);}
            if(!part.renameTo(target))throw new IOException("Не удалось сохранить "+name);
            try(FileOutputStream out=new FileOutputStream(new File(dir,name+".ok"))){out.write(hash.toString().getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}
        }return dir;
    }
    private static String read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
}
