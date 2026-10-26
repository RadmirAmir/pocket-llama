import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import dev.pocketllama.voice.*;
import java.io.IOException;
import java.util.*;

/** Real SQLCipher on ART: migration, speaker zero, overlap, restart and atomic rollback. */
public final class AndroidDiarizationRegression {
    private static int checks;
    private static void check(boolean value,String label) {
        if(!value)throw new AssertionError(label);checks++;System.out.println("OK "+label);
    }
    public static void run(Context context)throws Exception {
        try(VoiceStore store=new VoiceStore(context)) {
            String id=store.create("Speaker regression",true,0);
            check(store.get(id).getString("diarization_engine").equals(SpeakerDiarization.NEMOTRON),"new jobs select Nemotron");
            check(store.speakerAt(id,100)==0,"uncovered word is unknown");
            store.speakerSegments(id,SpeakerDiarization.segments(new double[]{0,1,0,.5,2,7},32000));
            check(store.get(id).getInt("speakers_done")==1,"speaker map and checkpoint committed together");
            check(store.speakerAt(id,100)==1,"first native channel becomes speaker one");
            check(store.speakerAt(id,750)==8,"overlap uses most recent onset deterministically");
            check(store.speakerAt(id,2000)==0,"segment end is exclusive");
            store.part(id,0,Arrays.asList(new SpeechRecognizer.Word("one",0,400),new SpeechRecognizer.Word("eight",1000,1600)),true,0,2000);
            store.rebuildTurns(id);
            try(Cursor c=store.turns(id)){check(c.moveToFirst()&&c.getInt(3)==1,"first channel survives word and turn persistence");check(c.moveToNext()&&c.getInt(3)==8,"eighth channel survives word and turn persistence");}
            boolean failed=false;
            try{store.speakerSegments(id,Arrays.asList(new SpeakerDiarization.Segment(0,100,2),null));}catch(NullPointerException expected){failed=true;}
            check(failed&&store.speakerAt(id,100)==1,"failed segment replacement rolls back to complete old map");
            store.restartRecognition(id);
            check(!store.partDone(id,0)&&store.speakerAt(id,100)==0&&store.get(id).getInt("speakers_done")==0,"restart clears both word and speaker checkpoints");
            store.configure(id,true,2,SpeakerDiarization.LEGACY);
            check(store.get(id).getString("diarization_engine").equals("cam++"),"explicit CAM++ comparison supported");
            store.embedding(id,0,0,1000,new float[]{1});
            ContentValues v=new ContentValues();v.put("speaker",2);v.put("score",1);
            store.getWritableDatabase().update("embeddings",v,"mid=?",new String[]{id});
            check(store.speakerAt(id,500)==2,"CAM++ retains original weighted voting");
            store.configure(id,true,0,SpeakerDiarization.NEMOTRON);
            check(store.speakerAt(id,500)==0,"switching engine clears incompatible embeddings");
            failed=false;try{store.configure(id,true,2,SpeakerDiarization.NEMOTRON);}catch(IOException expected){failed=true;}
            check(failed,"Nemotron never pretends to enforce a fixed speaker count");
            store.speakerSegments(id,Collections.emptyList());
            check(store.get(id).getInt("speakers_done")==1&&store.speakerAt(id,100)==0,"silence persists as completed empty map");
            store.delete(context,id);
            try(Cursor c=store.getReadableDatabase().rawQuery("SELECT count(*) FROM speaker_segments WHERE mid=?",new String[]{id})){c.moveToFirst();check(c.getInt(0)==0,"recording deletion cascades speaker map");}
        }
        try(LegacyVoiceStore4 legacy=new LegacyVoiceStore4(context)){
            check(legacy.getWritableDatabase().getVersion()==4,"accepted 0.13.4 helper reopens candidate database");
            String old=legacy.create("Rollback legacy job",true,0);
            check(legacy.get(old).getString("diarization_engine").equals("cam++"),"jobs made by old app keep legacy engine on return");
            legacy.delete(context,old);
        }
        System.out.println("PASS: "+checks+" encrypted Android speaker checks");
    }
}
