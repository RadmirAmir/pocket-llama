package dev.pocketllama.security;
import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import android.util.Base64;
public final class PinStore {
    private final Context c;
    public PinStore(Context c){this.c=c.getApplicationContext();}
    public boolean exists(){return new java.io.File(Vault.root(c),"pin.bin").isFile();}
    private JSONObject read()throws Exception{return new JSONObject(new String(Vault.read(c,"pin.bin",16384),StandardCharsets.UTF_8));}
    private void write(JSONObject j)throws Exception{Vault.write(c,"pin.bin",j.toString().getBytes(StandardCharsets.UTF_8));}
    private int boot(){return Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);}
    private long remaining(JSONObject j){long wall=Math.max(0,j.optLong("untilWall")-System.currentTimeMillis());long elapsed=j.optInt("boot",-2)==boot()?Math.max(0,j.optLong("untilElapsed")-SystemClock.elapsedRealtime()):0;return Math.min(3600000,Math.max(wall,elapsed));}
    public synchronized long remaining()throws Exception{return exists()?remaining(read()):0;}
    public synchronized void set(char[] pin)throws Exception {
        if(!exists()&&Vault.ready(c))throw new java.io.IOException("Файл кода недоступен. Существующие данные сохранены");
        if(exists()&&!GateSession.isOpen())throw new SecurityException("Сначала введите действующий код");byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);byte[] derived=PinCrypto.derive(pin,salt);
        try{write(new JSONObject().put("version",1).put("iterations",PinCrypto.ITERATIONS).put("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).put("hash",Base64.encodeToString(derived,Base64.NO_WRAP)).put("failures",0));}finally{Arrays.fill(derived,(byte)0);}
    }
    public synchronized boolean verify(char[] pin)throws Exception {
        JSONObject j=read();if(j.optInt("iterations")!=PinCrypto.ITERATIONS)throw new java.io.IOException("Неизвестный формат защиты");long wait=remaining(j);if(wait>0)throw new java.io.IOException("Повторите через "+((wait+999)/1000)+" сек");
        byte[] derived=PinCrypto.derive(pin,Base64.decode(j.getString("salt"),Base64.NO_WRAP));boolean ok;try{ok=PinCrypto.matches(derived,Base64.decode(j.getString("hash"),Base64.NO_WRAP));}finally{Arrays.fill(derived,(byte)0);}
        int failures=ok?0:j.optInt("failures")+1;long delay=PinCrypto.delay(failures);j.put("failures",failures).put("untilWall",System.currentTimeMillis()+delay).put("untilElapsed",SystemClock.elapsedRealtime()+delay).put("boot",boot());write(j);return ok;
    }
}
