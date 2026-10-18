package dev.pocketllama.photo;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
public final class OcrAssets {
    private OcrAssets(){}
    public static File prepare(Context c)throws Exception {
        File dir=new File(c.getNoBackupFilesDir(),"ocr-models-v1");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Нет места для OCR");
        JSONObject manifest;try(InputStream in=c.getAssets().open("ocr/manifest.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);manifest=new JSONObject(out.toString("UTF-8"));}
        JSONArray files=manifest.getJSONArray("files");for(int i=0;i<files.length();i++){JSONObject entry=files.getJSONObject(i);String name=entry.getString("name");if(!name.matches("[a-zA-Z0-9._-]+"))throw new IOException("Некорректный пакет OCR");File target=new File(dir,name);
            if(target.length()==entry.getLong("bytes")&&hash(target).equals(entry.getString("sha256")))continue;
            if(dir.getUsableSpace()<entry.getLong("bytes")+32*1024*1024)throw new IOException("Недостаточно места для OCR");File part=new File(dir,name+".part");
            try(InputStream in=c.getAssets().open("ocr/"+name);FileOutputStream out=new FileOutputStream(part)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}
            if(part.length()!=entry.getLong("bytes")||!hash(part).equals(entry.getString("sha256"))){part.delete();throw new IOException("Проверка OCR-модели не прошла");}
            java.nio.file.Files.move(part.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }return dir;
    }
    private static String hash(File file)throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
