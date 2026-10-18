package dev.pocketllama.photo;
import android.content.Context;
import javax.crypto.*;
public class PhotoKey {private static final SecretKey key;static{try{KeyGenerator g=KeyGenerator.getInstance("AES");g.init(256);key=g.generateKey();}catch(Exception e){throw new ExceptionInInitializerError(e);}}public static SecretKey get(Context c){return key;}}
