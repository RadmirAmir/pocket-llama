package dev.pocketllama.photo;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.SecretKey;

/** Authenticated before release; bounded reads; temporary files contain ciphertext only. */
public final class EncryptedFiles {
    private static final byte[] MAGIC = {'P','K','P',1};
    private EncryptedFiles() {}
    public static void write(File target, SecretKey key, String binding, byte[] plain) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
        byte[] iv=cipher.getIV(); if(iv.length!=12)throw new IOException("Некорректный ключ хранилища");
        File part=new File(target.getParentFile(),target.getName()+".tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(part)) {
                out.write(MAGIC);out.write(iv);out.write(cipher.doFinal(plain));out.getFD().sync();
            }
            Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {part.delete();}
    }
    public static byte[] read(File source,SecretKey key,String binding,int limit)throws Exception {
        long size=source.length();if(size<32||size>limit+32L)throw new IOException("Повреждённый или слишком большой файл");
        byte[] bytes=new byte[(int)size];
        try(DataInputStream in=new DataInputStream(new FileInputStream(source))){in.readFully(bytes);if(in.read()!=-1)throw new IOException("Файл изменился");}
        for(int i=0;i<4;i++)if(bytes[i]!=MAGIC[i])throw new IOException("Неизвестный формат хранилища");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,bytes,4,12));
        cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(bytes,16,bytes.length-16);
    }
}
