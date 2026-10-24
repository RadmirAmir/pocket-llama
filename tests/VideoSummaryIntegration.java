import dev.pocketllama.voice.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Real-model regression using 84 synthetic turns, never a user's recording. */
public final class VideoSummaryIntegration {
    static final class Source implements SummarySource {
        final List<String> lines=new ArrayList<>();final Map<Integer,TreeMap<Integer,String>> saved=new HashMap<>();
        Reader reader(Collection<String> values){Iterator<String> it=values.iterator();return new Reader(){public String next(){return it.hasNext()?it.next():null;}public void close(){}};}
        public Reader turns(){return reader(lines);}
        public Reader notes(int pass){return reader(new ArrayList<>(saved.getOrDefault(pass,new TreeMap<>()).values()));}
        public String note(int pass,int index){return saved.getOrDefault(pass,new TreeMap<>()).get(index);}
        public void note(int pass,int index,String text){saved.computeIfAbsent(pass,p->new TreeMap<>()).put(index,text);}
        public boolean containsTurn(int index){return index>=1&&index<=lines.size();}
    }
    public static void main(String[] args)throws Exception{
        Source source=new Source();String[] speech={
            "Ольга: Обсуждаем ремонт городской библиотеки. Это ещё проект, а не утверждённый план.",
            "Андрей: Смета составляет 12 миллионов рублей. Бюджет пока не согласован.",
            "Ольга: Открытие предложили на 1 сентября, но точную дату ещё не утвердили.",
            "Андрей: До 20 мая необходимо провести экспертизу здания. Без неё ремонт начинать нельзя.",
            "Ольга: Рекомендуем сначала проверить состояние здания, а потом уточнять стоимость ремонта.",
            "Андрей: В проект включили подъёмник для посетителей, которым трудно подниматься по лестнице."
        };
        boolean varied=args.length>3;JSONObject fixture=varied?new JSONObject(Files.readString(Path.of(args[3]))):null;
        if(varied){JSONArray turns=fixture.getJSONArray("turns");for(int i=0;i<turns.length();i++)source.lines.add("[#"+(i+1)+"] "+turns.getString(i)+"\n");}
        else for(int i=1;i<=84;i++)source.lines.add("[#"+i+"] "+speech[(i-1)%speech.length]+"\n");
        String result=new LocalSummary(source,Integer.parseInt(args[0]),"test-voice",Integer.parseInt(args[2]),true).summarize(System.out::println);
        System.out.println(result);Files.writeString(Path.of(args[1]),result);
        if(!varied){
            if(!result.contains("12")||!result.contains("20 мая"))throw new AssertionError("Summary lost the amount or inspection deadline");
            if(!Pattern.compile("(?iu)(?:не|пока не)\\s+(?:согласован|утвержд[её]н)").matcher(result).find())throw new AssertionError("Unapproved proposal was presented without its qualification");
        }
        if(Pattern.compile("(?iu)(?:фрагмент|segment)\\s+(?:про|о|about)").matcher(result).find())throw new AssertionError("Topic labels replaced substantive content");
        for(String line:result.split("\\n"))if(line.startsWith("- ")){
            Set<Integer> ids=new HashSet<>();Matcher refs=Pattern.compile("\\[#(\\d+)\\]").matcher(line);
            while(refs.find()){int id=Integer.parseInt(refs.group(1));if(!source.containsTurn(id)||!ids.add(id))throw new AssertionError("Invalid/repeated source citation");}
            if(ids.isEmpty())throw new AssertionError("A substantive point has no source");
            if(varied){String text=Pattern.compile("\\[#\\d+\\]").matcher(line.substring(2)).replaceAll("").trim();boolean found=false;for(int id:ids)if(source.lines.get(id-1).contains(text))found=true;if(!found)throw new AssertionError("Displayed claim was not the exact cited speech: "+text);}
        }
        if(varied){Set<Integer> topics=new HashSet<>();Matcher refs=Pattern.compile("\\[#(\\d+)\\]").matcher(result);while(refs.find())topics.add((Integer.parseInt(refs.group(1))-1)/14);if(topics.size()<4)throw new AssertionError("Summary collapsed different news topics: "+topics);}
        System.out.println("PASS: real local video summary "+(varied?"covers at least four of six topics with exact cited speech":"preserves amount, deadline, qualification and unique source citations")+" across 84 synthetic turns");
    }
}
