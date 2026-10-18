package android.os;
public final class Process {public static int myPid(){return Math.toIntExact(ProcessHandle.current().pid());}}
