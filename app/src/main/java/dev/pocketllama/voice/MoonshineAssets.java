package dev.pocketllama.voice;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.*;

/** Copies only bundled, pinned English assets. Never downloads models or audio. */
public final class MoonshineAssets {
    private MoonshineAssets() {}
    public static File prepare(Context context)throws Exception {
        File directory=new File(context.getFilesDir(),"moonshine-medium-en-v1");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("No storage for Moonshine");
        JSONObject manifest;
        try(InputStream input=context.getAssets().open("moonshine/manifest.json")) {
            ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=input.read(buffer))!=-1){if(output.size()+n>65536)throw new IOException("Invalid Moonshine manifest size");output.write(buffer,0,n);}
            manifest=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
        }
        JSONArray files=manifest.getJSONArray("files");
        for(int i=0;i<files.length();i++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("Moonshine setup stopped");
            JSONObject entry=files.getJSONObject(i);String name=entry.getString("name"),expected=entry.getString("sha256");
            if(!name.matches("[A-Za-z0-9._-]+"))throw new IOException("Invalid Moonshine asset name");
            File target=new File(directory,name),marker=new File(directory,name+".ok");long bytes=entry.getLong("bytes");
            if(target.length()==bytes&&marker.isFile()&&marker.length()==64&&new String(Files.readAllBytes(marker.toPath()),StandardCharsets.US_ASCII).equals(expected))continue;
            if(directory.getUsableSpace()<bytes+64L*1024*1024)throw new IOException("Not enough storage to prepare Moonshine");
            File partial=new File(directory,name+".part");MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
            try {
                try(InputStream input=context.getAssets().open("moonshine/"+name);FileOutputStream output=new FileOutputStream(partial)) {
                    byte[] buffer=new byte[128*1024];int n;
                    while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException("Moonshine setup stopped");output.write(buffer,0,n);digest.update(buffer,0,n);count+=n;}
                    output.getFD().sync();
                }
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
                if(count!=bytes||!hash.toString().equals(expected))throw new IOException("Moonshine asset checksum mismatch: "+name);
                if(!partial.renameTo(target))throw new IOException("Could not install Moonshine asset: "+name);
                try(FileOutputStream output=new FileOutputStream(marker)){output.write(expected.getBytes(StandardCharsets.US_ASCII));output.getFD().sync();}
            } finally {if(partial.exists())partial.delete();}
        }
        return directory;
    }
}
