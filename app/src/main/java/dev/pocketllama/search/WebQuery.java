package dev.pocketllama.search;

import java.net.URI;

/** Deliberately accepts only the current question, never conversation context. */
public final class WebQuery {
    private WebQuery() {}
    public static String validate(String text) {
        if(text==null||text.trim().isEmpty()||text.length()>1000)
            throw new IllegalArgumentException("Для поиска нужен вопрос длиной до 1000 символов");
        return text.trim();
    }
    public static boolean publicUrl(String value) {
        if(value==null||value.length()>2048||value.chars().anyMatch(c->c<32))return false;
        try {
            URI uri=new URI(value);String scheme=uri.getScheme(),host=uri.getHost();
            if(!("https".equals(scheme)||"http".equals(scheme))||host==null||uri.getRawUserInfo()!=null)return false;
            host=host.toLowerCase(java.util.Locale.ROOT);while(host.endsWith("."))host=host.substring(0,host.length()-1);
            if(host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||host.endsWith(".internal"))return false;
            if(host.contains(":"))return false; // Do not hand local/link-local IPv6 literals to a browser.
            if(host.matches("[0-9.]+")){
                String[] p=host.split("\\.");if(p.length!=4)return false;
                int[] b=new int[4];for(int i=0;i<4;i++){b[i]=Integer.parseInt(p[i]);if(b[i]>255||p[i].length()>1&&p[i].startsWith("0"))return false;}
                if(b[0]==0||b[0]==10||b[0]==127||b[0]>=224||b[0]==169&&b[1]==254||b[0]==172&&b[1]>=16&&b[1]<=31||b[0]==192&&b[1]==168||b[0]==100&&b[1]>=64&&b[1]<=127)return false;
            }
            return host.contains(".")&&uri.getPort()<=65535;
        }catch(Exception e){return false;}
    }
}
