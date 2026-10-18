package dev.pocketllama.core;

/** Ephemeral profile: never writes over the user's chat/API configuration. */
public final class SummaryProfile {
    private SummaryProfile(){}
    public static int context(int configured){return Math.min(4096,Math.max(2048,configured));}
    public static void apply(ServerConfig c){
        c.backend="cpu";c.projectorName="";c.lan=false;c.slots=1;
        c.contextPerSlot=context(c.contextPerSlot);c.threads=Math.min(4,c.threads);c.stayAwake=true;
    }
}
