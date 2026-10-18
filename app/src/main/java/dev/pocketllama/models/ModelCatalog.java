package dev.pocketllama.models;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.json.*;

/** Signed, bounded data only. Templates and native code are never downloaded by the catalog. */
public final class ModelCatalog {
    public static final int MAX_BYTES=262144;
    public final long revision;
    public final List<Entry> entries;
    private ModelCatalog(long revision,List<Entry> entries){this.revision=revision;this.entries=Collections.unmodifiableList(entries);}
    public static final class Entry {
        public final String id,title,description,filename,sha256,url,source,license,architecture,tier;
        public final long bytes,minRamBytes,workingBytes;
        public final int context,minAppCode;
        private Entry(JSONObject j)throws Exception {
            id=j.getString("id");title=j.getString("title");description=j.getString("description");
            filename=j.getString("filename");sha256=j.getString("sha256");url=j.getString("url");
            source=j.getString("source");license=j.getString("license");architecture=j.getString("architecture");tier=j.getString("tier");
            bytes=j.getLong("bytes");minRamBytes=j.getLong("minRamBytes");workingBytes=j.getLong("workingBytes");
            context=j.getInt("context");minAppCode=j.getInt("minAppCode");
            if(!id.matches("[a-z0-9_-]{1,64}")||!filename.matches("[A-Za-z0-9._-]{1,150}\\.gguf")||!sha256.matches("[a-f0-9]{64}"))throw new IOException("Некорректная запись каталога");
            if(title.length()>120||description.length()>600||bytes<1024||bytes>16L*1024*1024*1024||minRamBytes<0||workingBytes<bytes||context<512||context>32768||minAppCode<1)throw new IOException("Некорректные ограничения модели");
            URI u=new URI(url),s=new URI(source);
            if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||!"https".equals(s.getScheme())||s.getHost()==null)throw new IOException("Каталог требует HTTPS");
            if(!Arrays.asList("fast","balanced","quality").contains(tier)||!architecture.matches("[a-z0-9_]{1,40}")||license.length()>80)throw new IOException("Неизвестный профиль модели");
        }
    }
    public Entry find(String id)throws IOException {for(Entry e:entries)if(e.id.equals(id))return e;throw new IOException("Модель отсутствует в каталоге");}
    public Entry recommend(long ram,long available,int appCode){
        Entry fast=null,balanced=null;
        for(Entry e:entries)if(e.minAppCode<=appCode&&ram>=e.minRamBytes){if(e.tier.equals("fast"))fast=e;if(e.tier.equals("balanced")&&available>=e.workingBytes+512L*1024*1024)balanced=e;}
        return balanced!=null?balanced:fast;
    }
    public static ModelCatalog verify(byte[] envelope,String publicKey)throws Exception {
        if(envelope.length>MAX_BYTES)throw new IOException("Каталог слишком большой");
        JSONObject wrapper=new JSONObject(new String(envelope,StandardCharsets.UTF_8));
        byte[] payload=Base64.getDecoder().decode(wrapper.getString("payload"));
        byte[] signature=Base64.getDecoder().decode(wrapper.getString("signature"));
        if(payload.length>MAX_BYTES||signature.length>1024)throw new IOException("Каталог слишком большой");
        PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(publicKey)));
        Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(key);verifier.update(payload);
        if(!verifier.verify(signature))throw new IOException("Подпись каталога не прошла проверку");
        JSONObject data=new JSONObject(new String(payload,StandardCharsets.UTF_8));
        if(data.getInt("schema")!=1||data.getLong("revision")<1)throw new IOException("Неподдерживаемый каталог");
        JSONArray models=data.getJSONArray("models");if(models.length()<1||models.length()>100)throw new IOException("Неверное число моделей");
        List<Entry> entries=new ArrayList<>();Set<String> ids=new HashSet<>(),files=new HashSet<>();
        for(int i=0;i<models.length();i++){Entry e=new Entry(models.getJSONObject(i));if(!ids.add(e.id)||!files.add(e.filename))throw new IOException("Повтор модели в каталоге");entries.add(e);}
        return new ModelCatalog(data.getLong("revision"),entries);
    }
    public static byte[] read(InputStream in,int max)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Файл превышает допустимый размер");out.write(b,0,n);}return out.toByteArray();
    }
}
