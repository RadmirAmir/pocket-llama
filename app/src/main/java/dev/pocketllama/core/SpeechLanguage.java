package dev.pocketllama.core;

/** Keeps a resumed transcript on the engine that produced its committed parts. */
public final class SpeechLanguage {
    private SpeechLanguage() {}
    public static String choose(String requested,String previous,boolean complete,boolean hasParts) {
        if(!valid(requested)||!valid(previous))throw new IllegalArgumentException("Unsupported speech language");
        return complete||hasParts?previous:requested;
    }
    public static boolean valid(String language){return "ru".equals(language)||"en".equals(language);}
}
