package dev.pocketllama.security;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
/** PIN derivation has no Android dependency, so the exact implementation is host-tested. */
public final class PinCrypto {
    public static final int ITERATIONS=600000;
    private PinCrypto(){}
    public static byte[] derive(char[] pin,byte[] salt)throws Exception {if(pin.length!=6)throw new IllegalArgumentException("Нужно 6 цифр");for(char c:pin)if(c<'0'||c>'9')throw new IllegalArgumentException("Используйте цифры");if(salt.length!=16)throw new IllegalArgumentException("Неверная соль");PBEKeySpec spec=new PBEKeySpec(pin,salt,ITERATIONS,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}}
    public static boolean matches(byte[] a,byte[] b){return MessageDigest.isEqual(a,b);}
    public static long delay(int failures){if(failures<5)return 0;return Math.min(3600000,30000L<<Math.min(7,failures-5));}
}
