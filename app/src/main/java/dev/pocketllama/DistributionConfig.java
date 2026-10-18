package dev.pocketllama;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Build-owned capabilities. A remote catalog cannot turn on restricted model distribution. */
public final class DistributionConfig {
    private static volatile JSONObject cached;
    private DistributionConfig(){}
    public static JSONObject get(Context context)throws Exception {
        JSONObject result=cached;if(result!=null)return result;
        try(InputStream in=context.getAssets().open("distribution.json")){
            result=new JSONObject(new String(dev.pocketllama.models.ModelCatalog.read(in,16384),StandardCharsets.UTF_8));
        }cached=result;return result;
    }
    public static boolean surya(Context c)throws Exception{return get(c).optBoolean("suryaEnabled",false);}
    public static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "0.8.0";}}
    public static int code(Context c){try{return (int)c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();}catch(Exception e){return 22;}}
}
