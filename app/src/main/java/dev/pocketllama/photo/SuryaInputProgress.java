package dev.pocketllama.photo;

import org.json.JSONObject;

/** Only a real native input count can produce a percentage; it is not an OCR completion estimate. */
public final class SuryaInputProgress {
    private SuryaInputProgress() { }
    public static String message(JSONObject progress) {
        String base="Обрабатываем изображение";
        if(progress==null)return base;
        long total=progress.optLong("total",0),processed=progress.optLong("processed",-1);
        if(total<=0||total>1000000||processed<0||processed>total)return base;
        return base+" · "+(processed*100/total)+"% подготовки";
    }
}
