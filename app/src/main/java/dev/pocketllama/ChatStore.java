package dev.pocketllama;

import android.content.Context;
import dev.pocketllama.core.TextAttachment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.JSONObject;

/** One private conversation; update-in-place APK installation preserves it. */
public final class ChatStore {
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    public static synchronized String read(Context context) {
        File file=new File(dev.pocketllama.security.Vault.root(context),"chat.bin");if(!file.isFile())return "{\"messages\":[]}";
        try {String text=new String(dev.pocketllama.security.Vault.read(context,"chat.bin",MAX_BYTES),StandardCharsets.UTF_8);new JSONObject(text);return text;}
        catch(Exception e){throw new IllegalStateException("Не удалось открыть защищённую историю. Данные сохранены",e);}
    }
    public static synchronized void save(Context context,String value)throws Exception {
        JSONObject history=new JSONObject(value);if(history.optJSONArray("messages")==null||history.getJSONArray("messages").length()>64)throw new IOException("Некорректная история");
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("История слишком большая");dev.pocketllama.security.Vault.write(context,"chat.bin",bytes);
    }
}
