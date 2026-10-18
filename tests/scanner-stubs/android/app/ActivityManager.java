package android.app;
import java.util.*;
public class ActivityManager {public boolean alive;public static class RunningAppProcessInfo {public String processName;}public List<RunningAppProcessInfo> getRunningAppProcesses(){List<RunningAppProcessInfo> rows=new ArrayList<>();if(alive){RunningAppProcessInfo p=new RunningAppProcessInfo();p.processName="dev.pocketllama.memoryfix:ocr";rows.add(p);}return rows;}}
