import dev.pocketllama.photo.EncryptedFiles;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.*;
import javax.crypto.SecretKey;

/** Black-box file privacy and authentication, with the production encrypted-file implementation. */
public final class PhotoPrivacyTests {
    private static int checks;
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;}
    private interface Action{void run()throws Exception;}
    private static void fails(Action action,String message)throws Exception {try{action.run();throw new AssertionError(message);}catch(java.security.GeneralSecurityException|IOException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("pocket-privacy-test");File target=dir.resolve("photo.bin").toFile();KeyGenerator kg=KeyGenerator.getInstance("AES");kg.init(256);SecretKey key=kg.generateKey();
        byte[] source=("Личный документ, сумма 1234567. Do not upload. ".repeat(100)).getBytes(StandardCharsets.UTF_8);
        try {
            EncryptedFiles.write(target,key,"first/photo",source);byte[] first=Files.readAllBytes(target.toPath());check(Arrays.equals(source,EncryptedFiles.read(target,key,"first/photo",100000)),"roundtrip");check(!new String(first,StandardCharsets.UTF_8).contains("1234567"),"plaintext absent");
            EncryptedFiles.write(target,key,"first/photo",source);check(!Arrays.equals(first,Files.readAllBytes(target.toPath())),"fresh nonce each write");
            fails(()->EncryptedFiles.read(target,key,"second/photo",100000),"photo substitution must fail");fails(()->EncryptedFiles.read(target,key,"first/thumb",100000),"cross-purpose substitution must fail");fails(()->EncryptedFiles.read(target,kg.generateKey(),"first/photo",100000),"wrong key must fail");fails(()->EncryptedFiles.read(target,key,"first/photo",2),"bounded read");
            byte[] valid=Files.readAllBytes(target.toPath());byte[] changed=valid.clone();changed[changed.length/2]^=1;Files.write(target.toPath(),changed);fails(()->EncryptedFiles.read(target,key,"first/photo",100000),"tamper must fail");
            Files.write(target.toPath(),Arrays.copyOf(valid,valid.length-1));fails(()->EncryptedFiles.read(target,key,"first/photo",100000),"truncation must fail");
            Files.write(target.toPath(),valid);EncryptedFiles.write(target,key,"first/photo","updated".getBytes(StandardCharsets.UTF_8));check(new String(EncryptedFiles.read(target,key,"first/photo",100000),StandardCharsets.UTF_8).equals("updated"),"atomic replacement");check(Objects.requireNonNull(dir.toFile().listFiles()).length==1,"no plaintext or temporary file remains");
        }finally{for(File f:Objects.requireNonNull(dir.toFile().listFiles()))f.delete();Files.delete(dir);}
        System.out.println("PhotoPrivacyTests: "+checks+" checks passed");
    }
}
