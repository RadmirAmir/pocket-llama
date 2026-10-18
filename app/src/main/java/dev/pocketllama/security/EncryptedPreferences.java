package dev.pocketllama.security;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Atomic authenticated settings, including API credentials. */
public final class EncryptedPreferences {
    private final Context c;private JSONObject state;
    public EncryptedPreferences(Context c){this.c=c.getApplicationContext();reload();}
    public synchronized void reload(){try{state=new File(Vault.root(c),"server.bin").exists()?new JSONObject(new String(Vault.read(c,"server.bin",65536),StandardCharsets.UTF_8)):new JSONObject();}catch(Exception e){throw new IllegalStateException("Не удалось прочитать защищённые настройки",e);}}
    public String getString(String n,String d){return state.has(n)?state.optString(n,d):d;}public int getInt(String n,int d){return state.optInt(n,d);}public boolean getBoolean(String n,boolean d){return state.optBoolean(n,d);}
    public Editor edit(){return new Editor();}
    public final class Editor {private final JSONObject values=new JSONObject();private Editor put(String k,Object v){try{values.put(k,v);return this;}catch(JSONException e){throw new IllegalArgumentException(e);}}public Editor putString(String k,String v){return put(k,v);}public Editor putInt(String k,int v){return put(k,v);}public Editor putBoolean(String k,boolean v){return put(k,v);}
        public boolean commit(){synchronized(EncryptedPreferences.class){try(RandomAccessFile f=new RandomAccessFile(new File(Vault.root(c),"server.lock"),"rw");FileLock lock=f.getChannel().lock()){reload();Iterator<String> keys=values.keys();while(keys.hasNext()){String k=keys.next();state.put(k,values.get(k));}Vault.write(c,"server.bin",state.toString().getBytes(StandardCharsets.UTF_8));return true;}catch(Exception e){throw new IllegalStateException("Не удалось сохранить защищённые настройки",e);}}}public void apply(){commit();}}
}
