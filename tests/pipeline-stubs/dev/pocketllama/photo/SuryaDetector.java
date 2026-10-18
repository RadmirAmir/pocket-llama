package dev.pocketllama.photo;
import android.graphics.Bitmap;
import java.io.File;
import org.json.JSONArray;
public final class SuryaDetector implements AutoCloseable {
 public static int calls;public SuryaDetector(File file){}public void cancel(){}public void close(){}
 public JSONArray detect(Bitmap image,SuryaRuntime.Check check)throws Exception {check.run();calls++;return new JSONArray();}
}
