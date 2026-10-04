package com.wakka.deadshot;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
/** Bounded trace, disclosed trimming, and cumulative counters independent of it. */
public final class DeadshotDiagnostics {
    private static final int MAX_LINES=6000;
    private static final ArrayDeque<String> log=new ArrayDeque<>();
    private static final Map<String,Long> counts=new LinkedHashMap<>();
    private static final Map<String,String> details=new LinkedHashMap<>();
    private static long events,errors,trimmed,startNanos=System.nanoTime();
    private DeadshotDiagnostics() {}
    public static synchronized void reset() { log.clear();counts.clear();details.clear();events=errors=trimmed=0;startNanos=System.nanoTime(); }
    public static synchronized void count(String name) { counts.put(name,counts.containsKey(name)?counts.get(name)+1:1); }
    public static synchronized void detail(String name,String value) { details.put(name,value); }
    public static synchronized void event(String category,String message) { events++;append("["+((System.nanoTime()-startNanos)/1000000)+" ms] "+category+": "+message); }
    private static void append(String line) { log.addLast(line);while(log.size()>MAX_LINES) { log.removeFirst();trimmed++; } }
    public static synchronized void error(String category,Throwable error) {
        errors++;event(category,String.valueOf(error));StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
        for(String line:trace.toString().split("\\r?\\n")) append(line);
        detail("Latest captured error",String.valueOf(error));
    }
    public static synchronized String snapshot() {
        StringBuilder out=new StringBuilder("CUMULATIVE COUNTERS / RUNTIME CONTEXT\n");
        out.append("Elapsed session time ms: ").append((System.nanoTime()-startNanos)/1000000).append('\n');
        out.append("Log events: ").append(events).append("\nCaptured errors: ").append(errors).append('\n');
        for(Map.Entry<String,Long> x:counts.entrySet()) out.append(x.getKey()).append(": ").append(x.getValue()).append('\n');
        for(Map.Entry<String,String> x:details.entrySet()) out.append(x.getKey()).append(": ").append(x.getValue()).append('\n');
        out.append("\nRETAINED TRACE\nRetained lines: ").append(log.size()).append(" / ").append(MAX_LINES);
        out.append("\nOlder lines trimmed: ").append(trimmed).append(" (cumulative counters retained)\n");
        for(String line:log) out.append(line).append('\n');return out.toString();
    }
}
