package dev.pocketllama.video;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Bundled CPython/QuickJS. No runtime code downloads, pip, shell, or external app. */
public final class VideoRuntime {
    private final File python,script,home,temporary,quickjs,launcher;
    private VideoRuntime(Context c,File base){File libs=new File(c.getApplicationInfo().nativeLibraryDir);python=new File(libs,"libpython.so");quickjs=new File(libs,"libqjs.so");script=new File(base,"yt-dlp");launcher=new File(base,"launcher.py");home=new File(base,"python/usr");temporary=new File(base,"tmp");}
    public static VideoRuntime prepare(Context c)throws Exception {
        File base=new File(c.getNoBackupFilesDir(),"video-runtime-v1");
        if(!base.isDirectory()&&!base.mkdirs())throw new IOException("Нет места для загрузчика видео");
        // All runtime contents are public program bytes. Personal bytes never go here.
        try(RandomAccessFile lockFile=new RandomAccessFile(new File(base,"runtime.lock"),"rw");java.nio.channels.FileLock lock=lockFile.getChannel().lock()){
            String stamp;try(InputStream in=asset(c,"video/runtime-id.txt")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();copy(in,bytes);stamp=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).trim();}
            if(!stamp.matches("[0-9a-f]{64}"))throw new IOException("Не удалось подготовить загрузчик видео. Установите обновление Pocket.");
            File marker=new File(base,"ready.txt"),unpacked=new File(base,"python");
            String previous=marker.isFile()?new String(Files.readAllBytes(marker.toPath()),java.nio.charset.StandardCharsets.UTF_8):"";
            if(!stamp.equals(previous)||!new File(unpacked,"usr/lib/python3.12/encodings/__init__.py").isFile()){
                marker.delete();remove(unpacked);unpacked.mkdirs();
                long total=0;byte[] buf=new byte[65536];String prefix=unpacked.getCanonicalPath()+File.separator;
                try(ZipInputStream zip=new ZipInputStream(asset(c,"video/python.zip"))){ZipEntry entry;
                    while((entry=zip.getNextEntry())!=null){File target=new File(unpacked,entry.getName());if(!target.getCanonicalPath().startsWith(prefix))throw new IOException("Повреждён комплект загрузчика");
                        if(entry.isDirectory()){target.mkdirs();continue;}target.getParentFile().mkdirs();
                        try(OutputStream out=new FileOutputStream(target)){int n;while((n=zip.read(buf))!=-1){total+=n;if(total>256L*1024*1024)throw new IOException("Комплект загрузчика слишком большой");out.write(buf,0,n);}}
                    }
                }
                File tool=new File(base,"yt-dlp.part");try(InputStream in=asset(c,"video/yt-dlp");OutputStream out=new FileOutputStream(tool)){copy(in,out);}
                if(!tool.renameTo(new File(base,"yt-dlp")))throw new IOException("Не удалось подготовить загрузчик");
                Files.write(marker.toPath(),stamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            File launcher=new File(base,"launcher.py.part");try(InputStream in=asset(c,"video/launcher.py");OutputStream out=new FileOutputStream(launcher)){copy(in,out);}if(!launcher.renameTo(new File(base,"launcher.py")))throw new IOException("Не удалось подготовить загрузчик");
        }
        VideoRuntime runtime=new VideoRuntime(c,base);runtime.temporary.mkdirs();return runtime;
    }
    public Process start(List<String> options)throws IOException {
        ArrayList<String> args=new ArrayList<>(Arrays.asList(python.getAbsolutePath(),"-B",launcher.getAbsolutePath(),script.getAbsolutePath(),"--ignore-config","--no-plugin-dirs","--no-cache-dir","--no-update","--no-playlist","--playlist-items","1","--no-warnings","--no-colors","--socket-timeout","20","--retries","2","--fragment-retries","2","--js-runtimes","quickjs:"+quickjs.getAbsolutePath(),"--format","bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best"));
        args.addAll(options);ProcessBuilder builder=new ProcessBuilder(args);builder.directory(temporary);
        Map<String,String> env=builder.environment();env.put("PYTHONHOME",home.getAbsolutePath());env.put("PYTHONPATH","");env.put("PYTHONNOUSERSITE","1");env.put("PYTHONDONTWRITEBYTECODE","1");env.put("LD_LIBRARY_PATH",new File(home,"lib").getAbsolutePath());env.put("SSL_CERT_FILE",new File(home,"etc/tls/cert.pem").getAbsolutePath());env.put("TMPDIR",temporary.getAbsolutePath());
        return builder.start();
    }
    private static InputStream asset(Context c,String name)throws IOException {
        try{return c.getAssets().open(name);}catch(FileNotFoundException e){throw new IOException("Не удалось подготовить загрузчик видео. Установите обновление Pocket.",e);}
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
    private static void remove(File f)throws IOException{File[] children=f.listFiles();if(children!=null)for(File child:children)remove(child);if(f.exists()&&!f.delete())throw new IOException("Не удалось обновить загрузчик");}
}
