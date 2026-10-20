package dev.pocketllama.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

/** Small overlapping passages; offsets always refer to the canonical, edited source. */
public final class RagText {
    public interface Check {void run()throws Exception;}
    public static final int CHARS = 1400, OVERLAP = 200, DIMENSIONS = 768;
    public static final String VERSION = "embeddinggemma-q8-768-v1";
    private RagText() {}
    public static final class Passage {
        public final int start, end;
        public final String text;
        Passage(int start, int end, String text) { this.start=start;this.end=end;this.text=text; }
    }
    public static List<Passage> split(String text) {
        List<Passage> out=new ArrayList<>();
        for(int start=0;start<text.length();) {
            int end=utf8End(text,start,CHARS,1400);
            if(end<text.length()) {
                int boundary=-1;
                for(int i=end;i>start+(end-start)*2/3;i--) if(Character.isWhitespace(text.charAt(i-1))) {boundary=i;break;}
                if(boundary>0)end=boundary;
                if(Character.isLowSurrogate(text.charAt(end)))end--;
            }
            String part=text.substring(start,end).trim();
            if(!part.isEmpty())out.add(new Passage(start,end,part));
            if(end==text.length())break;
            int next=Math.max(start+1,end-OVERLAP);
            if(Character.isLowSurrogate(text.charAt(next)))next--;
            while(next<end&&!Character.isWhitespace(text.charAt(next)))next++;
            start=next<end?next:end;
        }
        return out;
    }
    public static String queryPrompt(String text) {return "task: search result | query: "+text;}
    public static String documentPrompt(String title,String text) {String t=title.trim();if(t.codePointCount(0,t.length())>100)t=t.substring(0,t.offsetByCodePoints(0,100));return "title: "+(t.isEmpty()?"none":t)+" | text: "+text;}
    public static int utf8End(String text,int start,int chars,int bytes) {int end=start,total=0;while(end<text.length()&&end-start<chars){int cp=text.codePointAt(end),n=cp<=0x7f?1:cp<=0x7ff?2:cp<=0xffff?3:4;if(total+n>bytes)break;total+=n;end+=Character.charCount(cp);}return end;}
    public static String hash(String text) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String fold(String text) {return Normalizer.normalize(text,Normalizer.Form.NFC).toLowerCase(Locale.ROOT).replace('ё','е');}
    /** Quote every token: the user's query can never become FTS operators or SQL. */
    public static String match(String query) {
        LinkedHashSet<String> terms=new LinkedHashSet<>();
        for(String word:fold(query).split("[^\\p{L}\\p{N}]+")) {
            if(word.length()<2||STOP.contains(word))continue;
            if(word.length()>80)word=word.substring(0,80);
            terms.add("\""+word+"\"");
            if(terms.size()==16)break;
        }
        return String.join(" OR ",terms);
    }
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("the","and","for","with","what","where","when","that","this","from","about","have","does","как","что","где","когда","это","для","про","или","мне","меня","было","были","есть","найди","найти","расскажи","который","какой","какие","мой","мои","моих","find","search","tell"));
    public static float[] normalize(float[] vector) {
        if(vector.length!=DIMENSIONS)throw new IllegalArgumentException("Unexpected embedding dimensions");
        double norm=0;for(float v:vector){if(!Float.isFinite(v))throw new IllegalArgumentException("Invalid embedding");norm+=(double)v*v;}
        if(norm<1e-12)throw new IllegalArgumentException("Empty embedding");
        float scale=(float)(1/Math.sqrt(norm));for(int i=0;i<vector.length;i++)vector[i]*=scale;
        return vector;
    }
    public static double cosine(float[] a,float[] b) {double sum=0;for(int i=0;i<a.length;i++)sum+=(double)a[i]*b[i];return sum;}
}
