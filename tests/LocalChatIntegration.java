import dev.pocketllama.ChatApi;
import dev.pocketllama.core.*;
import java.io.File;
import org.json.*;

/** Public greeting fixture through the production Java request and SSE decoder. */
public final class LocalChatIntegration {
    public static void main(String[] args) throws Exception {
        int port=Integer.parseInt(args[0]);File model=new File(args[1]);
        GenerationProfile profile=GenerationProfile.from(GgufReader.read(model));
        if(profile!=GenerationProfile.MINICPM5)throw new AssertionError("MiniCPM profile not selected from metadata");
        String hash=ModelFingerprint.sha256(model,()->false);
        if(!hash.equals(args[2]))throw new AssertionError("Fixture checksum mismatch");
        System.out.println("GGUF SHA256: "+hash+"; profile="+profile.id);
        for(boolean thinking:args.length>3&&args[3].equals("--no-thinking-only")?new boolean[]{false}:new boolean[]{false,true}) {
            JSONArray messages=new JSONArray()
                .put(new JSONObject().put("role","system").put("content","Отвечай по-русски, кратко."))
                .put(new JSONObject().put("role","user").put("content","Привет"));
            JSONObject body=ChatApi.request(messages,thinking,128,false,profile);
            body.put("seed",42);
            StringBuilder text=new StringBuilder(),reasoning=new StringBuilder();String[] finish={""};
            new ChatApi(port,"test-chat").stream(body,new ChatApi.Listener(){
                public void chunk(String content,String thought){text.append(content);reasoning.append(thought);}
                public void complete(String reason,JSONObject usage){finish[0]=reason;}
            });
            if(text.length()==0||finish[0].isEmpty()||text.indexOf("<think>")>=0)throw new AssertionError("No final answer or invalid tag framing");
            if(!thinking && reasoning.length()!=0)throw new AssertionError("Reasoning enabled despite explicit zero budget");
            long cyrillic=text.codePoints().filter(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.CYRILLIC).count();
            if(cyrillic<3)throw new AssertionError("Russian greeting fixture has no Russian answer");
            System.out.println("PASS API framing: thinking="+thinking+"; finish="+finish[0]+"; reasoning_chars="+reasoning.length()+"; answer="+text);
        }
    }
}
