import dev.pocketllama.voice.*;
import java.util.*;
import java.nio.file.*;

/** Exercises the exact Android summarizer against a real pinned llama-server.
 * Storage is in-memory here; SQLite durability is owned by VoiceStore on Android. */
public final class LocalSummaryIntegration {
    private static final class Source implements SummarySource {
        final List<String> turns=new ArrayList<>();final TreeMap<Integer,TreeMap<Integer,String>> saved=new TreeMap<>();
        Reader reader(List<String> lines){Iterator<String> it=lines.iterator();return new Reader(){public String next(){return it.hasNext()?it.next():null;}public void close(){}};}
        public Reader turns(){return reader(turns);}
        public Reader notes(int pass){return reader(new ArrayList<>(saved.getOrDefault(pass,new TreeMap<>()).values()));}
        public String note(int pass,int index){return saved.getOrDefault(pass,new TreeMap<>()).get(index);}
        public void note(int pass,int index,String value){saved.computeIfAbsent(pass,p->new TreeMap<>()).put(index,value);}
        public boolean containsTurn(int index){return index>=1&&index<=turns.size();}
    }
    public static void main(String[] args)throws Exception{
        Source source=new Source();source.turns.add("[#1] 00:00:00 Иван: Решили запустить пилот диктофона в пятницу.\n");source.turns.add("[#2] 00:00:08 Мария: Я подготовлю тестовую запись до среды.\n");source.turns.add("[#3] 00:00:16 Сергей: Бюджет пока не согласован, решение о закупке не принято.\n");
        LocalSummary engine=new LocalSummary(source,Integer.parseInt(args[0]),"test-voice",2048);String result=engine.summarize(System.out::println);System.out.println(result);
        if(source.saved.isEmpty())throw new AssertionError("No checkpoint was saved");
        if(!result.contains("[#"))throw new AssertionError("Source citations were lost");
        if(args.length>1)Files.writeString(Path.of(args[1]),result);
        System.out.println("Real llama.cpp API/schema integration passed (semantic quality is not asserted); checkpoints="+source.saved.values().stream().mapToInt(Map::size).sum());
    }
}
