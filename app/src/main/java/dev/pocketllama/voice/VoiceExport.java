package dev.pocketllama.voice;
import android.content.Context;
import android.database.Cursor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.*;

public final class VoiceExport {
    private VoiceExport(){}
    public static void write(Context context,VoiceStore store,String id,String format,OutputStream output)throws Exception{
        if(format.equals("wav")){try(PcmFile audio=new PcmFile(VoiceStore.audio(context,id),false,dev.pocketllama.security.Vault.key(context))){output.write(audio.wavHeader());byte[] bytes=new byte[65536];long position=0;int n;while((n=audio.readBytes(position,bytes,0,bytes.length))>0){output.write(bytes,0,n);position+=n;}java.util.Arrays.fill(bytes,(byte)0);}return;}
        JSONObject meeting=store.get(id);Writer w=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));
        if(format.equals("md")){w.write("# "+meeting.getString("title")+"\n\n");if(!meeting.optString("video_url").isEmpty())w.write("Источник: "+meeting.optString("video_url")+"\n\nКонспект составлен по аудиодорожке.\n\n");w.write(meeting.optString("summary")+"\n\n## Расшифровка\n\n");}
        if(format.equals("json")){JSONObject meta=new JSONObject().put("title",meeting.getString("title")).put("created",meeting.getLong("created")).put("duration_ms",meeting.getLong("samples")/16).put("summary",meeting.optString("summary")).put("source_url",meeting.optString("video_url")).put("source_platform",meeting.optString("video_platform")).put("summary_basis",meeting.optString("video_url").isEmpty()?"recording":"audio_track_only").put("speaker_labels","approximate; unverified");String s=meta.toString();w.write(s.substring(0,s.length()-1)+",\"turns\":[");}
        try(Cursor c=store.turns(id)){boolean first=true;while(c.moveToNext()){int index=c.getInt(0);String name=store.speaker(id,c.getInt(3)),text=c.getString(4);long start=c.getLong(1),end=c.getLong(2);
            if(format.equals("md"))w.write("**[#"+index+" · "+VoiceStore.time(start)+" · "+name+"]**\n\n"+text+"\n\n");
            else if(format.equals("srt"))w.write(index+"\n"+srtTime(start)+" --> "+srtTime(Math.max(start+1,end))+"\n"+name+": "+text+"\n\n");
            else if(format.equals("json")){if(!first)w.write(',');w.write(new JSONObject().put("id",index).put("start_ms",start).put("end_ms",end).put("speaker",c.getInt(3)).put("speaker_name",name).put("text",text).toString());first=false;}
        }}if(format.equals("json"))w.write("]}");w.flush();
    }
    private static String srtTime(long ms){return VoiceStore.time(ms)+String.format(Locale.ROOT,",%03d",Math.max(0,ms)%1000);}
}
