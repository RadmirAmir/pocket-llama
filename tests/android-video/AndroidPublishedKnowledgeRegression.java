import dalvik.system.PathClassLoader;
import java.io.File;
import java.util.List;
import org.json.JSONObject;

/** Exercises RAG text extraction from the signed APK's DEX, not the source fixture. */
public final class AndroidPublishedKnowledgeRegression {
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        checks++;System.out.println("OK "+message);
    }
    private static List<?> pieces(Class<?> reader,String kind,String title,String body)throws Exception {
        Object source=reader.getMethod("text",String.class,String.class,String.class,String.class,String.class,JSONObject.class)
            .invoke(null,kind,"published-fixture","",title,body,new JSONObject());
        return (List<?>)source.getClass().getField("pieces").get(source);
    }
    private static String text(List<?> pieces)throws Exception {
        StringBuilder result=new StringBuilder();
        for(Object piece:pieces)result.append(piece.getClass().getField("text").get(piece));
        return result.toString();
    }
    public static void run(File apk)throws Exception {
        PathClassLoader loader=new PathClassLoader(apk.getAbsolutePath(),ClassLoader.getSystemClassLoader().getParent());
        Class<?> reader=loader.loadClass("dev.pocketllama.rag.RagSources");
        check(reader.getClassLoader()==loader,"knowledge reader comes from the signed APK DEX");
        check(text(pieces(reader,"note","Call Serena 742999","")).equals("Call Serena 742999"),"signed APK indexes a title-only note");
        check(text(pieces(reader,"note","Call Serena 742999"," \n\t")).equals("Call Serena 742999"),"signed APK indexes a note with whitespace-only body");
        check(text(pieces(reader,"note","Title","Actual body")).equals("Actual body"),"signed APK preserves nonempty note text");
        check(pieces(reader,"note","","").isEmpty(),"signed APK excludes an entirely empty note");
        check(pieces(reader,"ocr","Unprocessed page","").isEmpty(),"signed APK requires recognized OCR text");
        System.out.println("PASS: "+checks+" signed-APK knowledge checks");
    }
}
