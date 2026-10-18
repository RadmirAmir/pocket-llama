package dev.pocketllama.photo;

import java.io.IOException;
import java.util.Locale;

/** Conservative streaming guard. Markup and repeated table cells are not prose loops. */
public final class SuryaOutputGuard {
    public static final class Repetition extends IOException {
        Repetition(){super("Surya повторяет один фрагмент. Готовые области сохранены");}
    }
    private final boolean page;
    private final StringBuilder plain=new StringBuilder(),tag=new StringBuilder();
    private boolean inTag;private char quote;private int unchecked;
    public SuryaOutputGuard(boolean page){this.page=page;}
    public void append(String chunk)throws Repetition {
        for(int i=0;i<chunk.length();i++) {
            char c=chunk.charAt(i);
            if(inTag) {
                if(tag.length()<2048)tag.append(c);
                if(quote!=0){if(c==quote)quote=0;}
                else if(c=='\''||c=='"')quote=c;
                else if(c=='>') {
                    inspect();String value=tag.toString().toLowerCase(Locale.ROOT);
                    String name=value.replaceFirst("^<\\s*/?\\s*","").split("[\\s/>]",2)[0];
                    if(name.equals("table")||name.equals("tr")||name.equals("td")||name.equals("th")||page&&name.equals("div")&&value.contains("data-bbox")){plain.setLength(0);unchecked=0;}
                    tag.setLength(0);inTag=false;
                }
            }else if(c=='<'){inTag=true;tag.append(c);}
            else {plain.append(c);if(plain.length()>4096)plain.delete(0,plain.length()-4096);if(++unchecked>=32)inspect();}
        }
        inspect();
    }
    private void inspect()throws Repetition {unchecked=0;if(repeated(plain))throw new Repetition();}
    private static boolean repeated(CharSequence text) {
        int n=text.length();if(n<1024)return false;
        // Six exact copies occupying at least 1024 characters. Compare only a bounded tail.
        for(int period=1;period<=Math.min(512,n/6);period++) {
            int needed=Math.max(1024,period*6);if(needed>n||text.charAt(n-1)!=text.charAt(n-1-period))continue;
            boolean meaningful=false;for(int i=n-period;i<n;i++)if(!Character.isWhitespace(text.charAt(i))){meaningful=true;break;}if(!meaningful)continue;
            int i=n-1;while(i>=n-needed+period&&text.charAt(i)==text.charAt(i-period))i--;
            if(i<n-needed+period)return true;
        }
        return false;
    }
}
