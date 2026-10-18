package dev.pocketllama;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/** Local UI language only. Content and stored transcripts are never translated. */
public final class I18n {
    private static File preference;
    private static volatile String language="ru", dictionary="{}";
    private static volatile long modified=-1;
    private static Map<String,String> english=Collections.emptyMap();
    private static List<String> phrases=Collections.emptyList();
    private I18n() {}
    public static synchronized void initialize(Context c) {
        if(preference!=null)return;
        preference=new File(c.getNoBackupFilesDir(),"interface-language");
        try(InputStream in=c.getAssets().open("i18n-en.json")) {
            dictionary=new String(read(in,1024*1024),StandardCharsets.UTF_8);
            JSONObject json=new JSONObject(dictionary);Map<String,String> values=new HashMap<>();
            for(Iterator<String> it=json.keys();it.hasNext();) {String key=it.next();values.put(key,json.getString(key));}
            english=Collections.unmodifiableMap(values);
            ArrayList<String> ordered=new ArrayList<>(values.keySet());ordered.removeIf(k->k.length()<5);
            ordered.sort((a,b)->Integer.compare(b.length(),a.length()));phrases=Collections.unmodifiableList(ordered);
        } catch(Exception e) {throw new IllegalStateException("Cannot load local interface translations",e);}
        language();
    }
    public static synchronized String language() {
        if(preference==null)return language;
        long stamp=preference.lastModified();
        if(stamp!=modified) {
            try(InputStream in=new AtomicFile(preference).openRead()) {
                String value=new String(read(in,16),StandardCharsets.US_ASCII).trim();
                language=value.equals("en")?"en":"ru";
            } catch(IOException e) {language="ru";}
            modified=stamp;
        }
        return language;
    }
    public static synchronized void setLanguage(String value)throws IOException {
        if(!value.equals("en")&&!value.equals("ru"))throw new IllegalArgumentException("Unsupported interface language");
        if(preference==null)throw new IOException("Interface preferences are unavailable");
        AtomicFile file=new AtomicFile(preference);FileOutputStream out=null;
        try {out=file.startWrite();out.write(value.getBytes(StandardCharsets.US_ASCII));file.finishWrite(out);}
        catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
        language=value;modified=preference.lastModified();
    }
    public static String dictionary() {return dictionary;}
    private static byte[] read(InputStream input,int limit)throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){if(output.size()+n>limit)throw new IOException("Interface asset is too large");output.write(buffer,0,n);}
        return output.toByteArray();
    }
    public static String t(String source) {return source!=null&&language().equals("en")?english.getOrDefault(source,source):source;}
    /** Only for application status/error text, never document or conversation content. */
    public static String status(String source) {
        if(source==null||!language().equals("en"))return source;
        String exact=english.get(source);if(exact!=null)return exact;
        String value=source;for(String phrase:phrases)value=value.replace(phrase,english.get(phrase));return value;
    }
    public static Context wrap(Context base) {
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(language()));return base.createConfigurationContext(config);
    }
}
