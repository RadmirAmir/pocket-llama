package dev.pocketllama.video;
import java.io.*;
import java.util.*;
/** Host fixture only; never compiled into Android. */
public final class VideoRuntime {
 private final String fixture;public VideoRuntime(String fixture){this.fixture=fixture;}
 public Process start(List<String> options)throws IOException{List<String> args=new ArrayList<>(Arrays.asList("python3",fixture));args.addAll(options);return new ProcessBuilder(args).start();}
}
