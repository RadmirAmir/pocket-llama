import dev.pocketllama.models.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;

public final class ModelDistributionTests {
    static int checks;static void check(boolean v,String why){if(!v)throw new AssertionError(why);checks++;}
    interface Work{void run()throws Exception;}
    static void rejects(Work w,String why)throws Exception{try{w.run();}catch(Exception e){checks++;return;}throw new AssertionError(why);}
    static String hash(byte[] b)throws Exception{StringBuilder s=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",x&255));return s.toString();}
    static byte[] signed(JSONObject data,PrivateKey key)throws Exception {byte[] p=data.toString().getBytes(StandardCharsets.UTF_8);Signature s=Signature.getInstance("SHA256withRSA");s.initSign(key);s.update(p);return new JSONObject().put("payload",Base64.getEncoder().encodeToString(p)).put("signature",Base64.getEncoder().encodeToString(s.sign())).toString().getBytes(StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),tmp=Files.createTempDirectory("pocket-download-test-");
        byte[] envelope=Files.readAllBytes(root.resolve("app/src/main/assets/model-catalog/catalog.signed.json"));String publicKey=Files.readString(root.resolve("app/src/main/assets/model-catalog/public-key.txt"));
        ModelCatalog catalog=ModelCatalog.verify(envelope,publicKey);check(catalog.entries.size()==3,"three pinned models");
        check(catalog.recommend(8L<<30,4L<<30,22).tier.equals("balanced"),"balanced on sufficient memory");
        check(catalog.recommend(4L<<30,2L<<30,22).tier.equals("fast"),"small phone recommendation");
        check(catalog.recommend(3L<<30,2L<<30,22)==null,"no unsupported recommendation");
        check(catalog.recommend(12L<<30,1L<<30,22).tier.equals("fast"),"current memory affects recommendation");
        check(catalog.recommend(12L<<30,8L<<30,21)==null,"app compatibility bound");
        JSONObject bad=new JSONObject(new String(envelope,StandardCharsets.UTF_8));byte[] payload=Base64.getDecoder().decode(bad.getString("payload"));payload[payload.length/2]^=1;bad.put("payload",Base64.getEncoder().encodeToString(payload));rejects(()->ModelCatalog.verify(bad.toString().getBytes(StandardCharsets.UTF_8),publicKey),"tampered payload");
        KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(3072);KeyPair pair=gen.generateKeyPair();String testPublic=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        JSONObject source=new JSONObject(Files.readString(root.resolve("distribution/model-catalog.json")));
        JSONObject duplicate=new JSONObject(source.toString());duplicate.getJSONArray("models").put(duplicate.getJSONArray("models").getJSONObject(0));rejects(()->ModelCatalog.verify(signed(duplicate,pair.getPrivate()),testPublic),"duplicate ID");
        JSONObject insecure=new JSONObject(source.toString());insecure.getJSONArray("models").getJSONObject(0).put("url","http://example.org/model.gguf");rejects(()->ModelCatalog.verify(signed(insecure,pair.getPrivate()),testPublic),"plain HTTP catalogue model");
        JSONObject traversal=new JSONObject(source.toString());traversal.getJSONArray("models").getJSONObject(0).put("filename","../bad.gguf");rejects(()->ModelCatalog.verify(signed(traversal,pair.getPrivate()),testPublic),"path traversal");
        rejects(()->ModelCatalog.verify(new byte[ModelCatalog.MAX_BYTES+1],publicKey),"bounded catalog");
        byte[] bytes=new byte[600000];new Random(19).nextBytes(bytes);String sha=hash(bytes);HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);final int[] requests={0};final String[] range={""};
        server.createContext("/model",e->{try{requests[0]++;String h=e.getRequestHeaders().getFirst("Range");range[0]=h;int offset=h==null?0:Integer.parseInt(h.substring(6,h.length()-1));if(offset>0)e.getResponseHeaders().set("Content-Range","bytes "+offset+"-"+(bytes.length-1)+"/"+bytes.length);e.sendResponseHeaders(offset>0?206:200,bytes.length-offset);e.getResponseBody().write(bytes,offset,bytes.length-offset);}finally{e.close();}});
        server.createContext("/cut",e->{try{e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes,0,200000);}finally{e.close();}});
        server.createContext("/ignore",e->{try{e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}finally{e.close();}});
        server.createContext("/wrong",e->{try{e.getResponseHeaders().set("Content-Range","bytes 0-"+(bytes.length-1)+"/"+bytes.length);e.sendResponseHeaders(206,bytes.length);e.getResponseBody().write(bytes);}finally{e.close();}});
        server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        try {
            File part=tmp.resolve("partial").toFile(),target=tmp.resolve("model.gguf").toFile();
            rejects(()->new VerifiedDownload().transfer(base+"/cut",bytes.length,sha,part,target,(a,b,c)->{},()->{}),"interrupted connection");
            check(part.length()==200000&&!target.exists(),"partial kept and never exposed as installed");
            new VerifiedDownload().transfer(base+"/model",bytes.length,sha,part,target,(a,b,c)->{},()->{});
            check(Arrays.equals(Files.readAllBytes(target.toPath()),bytes),"range resume contents");check("bytes=200000-".equals(range[0]),"resume offset");check(!part.exists(),"atomic publication removes part");
            int before=requests[0];new VerifiedDownload().transfer(base+"/model",bytes.length,sha,part,target,(a,b,c)->{},()->{});check(requests[0]==before,"installed model not downloaded again");
            target.delete();Files.write(part.toPath(),Arrays.copyOf(bytes,123456));new VerifiedDownload().transfer(base+"/ignore",bytes.length,sha,part,target,(a,b,c)->{},()->{});check(Arrays.equals(Files.readAllBytes(target.toPath()),bytes),"server ignoring Range restarts safely");
            target.delete();Files.write(part.toPath(),Arrays.copyOf(bytes,123456));rejects(()->new VerifiedDownload().transfer(base+"/wrong",bytes.length,sha,part,target,(a,b,c)->{},()->{}),"incorrect content range");check(part.length()==123456&&!target.exists(),"bad range cannot append");
            Files.write(target.toPath(),"old model".getBytes(StandardCharsets.UTF_8));Files.delete(part.toPath());rejects(()->new VerifiedDownload().transfer(base+"/model",bytes.length,"0".repeat(64),part,target,(a,b,c)->{},()->{}),"wrong checksum");check(!part.exists(),"corrupted part removed");check(Files.readString(target.toPath()).equals("old model"),"failed update preserves old target");
            target.delete();VerifiedDownload cancel=new VerifiedDownload();rejects(()->cancel.transfer(base+"/model",bytes.length,sha,part,target,(a,b,c)->cancel.cancel(),()->{}),"user pause");check(part.length()>0&&!target.exists(),"paused transfer retained");
            new VerifiedDownload().transfer(base+"/model",bytes.length,sha,part,target,(a,b,c)->{},()->{});check(Arrays.equals(Files.readAllBytes(target.toPath()),bytes),"resume after user pause");
            target.delete();Files.write(part.toPath(),bytes);before=requests[0];new VerifiedDownload().transfer(base+"/model",bytes.length,sha,part,target,(a,b,c)->{},()->{});check(requests[0]==before,"crash after final byte needs only verification");
        }finally{server.stop(0);try(var files=Files.walk(tmp)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception ignored){}});}}
        System.out.println("PASS: "+checks+" model distribution assertions");
    }
}
