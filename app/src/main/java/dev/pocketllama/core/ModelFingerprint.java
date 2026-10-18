package dev.pocketllama.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BooleanSupplier;

/** Constant-memory identity check. Never reads model bytes into the diagnostic log. */
public final class ModelFingerprint {
    private ModelFingerprint() { }
    public static String sha256(File file, BooleanSupplier cancelled) throws IOException {
        final MessageDigest digest;
        try {digest=MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        long size=file.length(),modified=file.lastModified(),read=0;
        try(FileInputStream input=new FileInputStream(file)) {
            byte[] buffer=new byte[1024*1024];int count;
            while((count=input.read(buffer))!=-1) {
                if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())throw new IOException("Проверка GGUF остановлена");
                digest.update(buffer,0,count);read+=count;
            }
        }
        if(cancelled.getAsBoolean())throw new IOException("Проверка GGUF остановлена");
        if(read!=size || file.length()!=size || file.lastModified()!=modified)throw new IOException("Файл GGUF изменился во время проверки");
        StringBuilder result=new StringBuilder(64);
        for(byte b:digest.digest()){result.append(Character.forDigit((b>>>4)&15,16));result.append(Character.forDigit(b&15,16));}
        return result.toString();
    }
    public static String reference(String sha256) {
        switch(sha256) {
            case "ec2d5801640099e97d8d7e8003ad4d81f336e757811f03a26173dddf386602fd":return "openbmb/MiniCPM5-2B-GGUF · Q4_K_M";
            case "967d467b5a73f2def7f339f8ad724a984fe1caa323006577cd9c26adb755f0de":return "bartowski/MiniCPM5-2B-GGUF · Q4_K_M";
            case "3f50d5f830873c22d33558f69eb2c34aa59858f37e7a58a982aae88bb38ebe88":return "bartowski/Qwen_Qwen3.5-2B-GGUF · Q5_K_M";
            default:return "Версия GGUF определяется по SHA256; отсутствие в списке не означает повреждение";
        }
    }
}
