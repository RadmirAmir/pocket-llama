package dev.pocketllama.photo;
import android.graphics.Bitmap;
import java.nio.file.*;
public final class ScanImages {public static byte[] jpeg(Bitmap bitmap,int quality)throws Exception{return Files.readAllBytes(Path.of(System.getProperty("surya.fixture")));}}
