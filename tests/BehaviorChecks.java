import com.wakka.bridge.SessionGate;
import com.wakka.deadshot.NativeEventRouter;
import com.wakka.deadshot.DeadshotDiagnostics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Guard native event behavior, ownership, pause separation and diagnostic retention. */
public final class BehaviorChecks {
    private static int assertions;
    private static void check(boolean value,String why) { assertions++;if(!value)throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        List<String> events=new ArrayList<>();
        NativeEventRouter input=new NativeEventRouter((type,key)->events.add(type+":"+key));
        input.set(10000,1<<20);input.set(2,1<<20);input.set(-97,1<<20);
        check(events.toString().equals("[0:20]"),"overlapping touch/keypad/hardware sends one native press");
        input.remove(2);input.remove(10000);
        check(events.size()==1&&input.mask()==(1<<20),"lifting two owners cannot release hardware Confirm");
        input.remove(-97);
        check(events.toString().equals("[0:20, 1:20]"),"last owner sends native type-1 release");
        events.clear();input.set(1,1<<16);input.set(1,1<<4);
        check(events.toString().equals("[0:16, 1:16, 0:4]"),"changing native family releases old key before pressing replacement");
        input.set(-21,1<<17);input.remove(-21);
        check(input.mask()==(1<<4),"hardware release removes its captured key even when game mode changes");
        input.set(2,(1<<10)|(1<<11));input.clear();
        check(input.mask()==0&&input.contacts()==0,"focus loss clears every native owner");
        check(events.subList(events.size()-3,events.size()).toString().equals("[1:4, 1:10, 1:11]"),"clear releases all remaining native keys");

        SessionGate gate=new SessionGate();gate.foreground(true);gate.userPaused(true);
        gate.foreground(false);gate.foreground(true);
        check(gate.blocked(),"foreground must not erase explicit user pause");
        CountDownLatch entered=new CountDownLatch(1),released=new CountDownLatch(1);
        Thread worker=new Thread(()->{entered.countDown();gate.awaitRunning();released.countDown();});worker.start();
        check(entered.await(1,TimeUnit.SECONDS),"original graphics boundary can enter gate");
        check(!released.await(40,TimeUnit.MILLISECONDS),"game waits while paused");
        gate.userPaused(false);check(released.await(1,TimeUnit.SECONDS),"resume wakes the same worker");
        gate.foreground(false);check(gate.blocked(),"background is an independent pause reason");

        DeadshotDiagnostics.reset();
        for(int i=0;i<6100;i++){DeadshotDiagnostics.count("frame");DeadshotDiagnostics.event("FRAME",Integer.toString(i));}
        String retained=DeadshotDiagnostics.snapshot();
        check(retained.contains("frame: 6100"),"cumulative counters survive trace trimming");
        check(retained.contains("Retained lines: 6000 / 6000")&&retained.contains("Older lines trimmed: 100"),"bounded trace discloses exact trimming");
        Throwable cause=new IllegalStateException("deepest diagnostic cause");
        StackTraceElement[] stack=new StackTraceElement[400];
        for(int i=0;i<stack.length;i++)stack[i]=new StackTraceElement("Game","frame"+i,"Game.java",i+1);
        cause.setStackTrace(stack);DeadshotDiagnostics.error("CAPTURE",new RuntimeException("wrapper",cause));
        retained=DeadshotDiagnostics.snapshot();
        check(retained.contains("Caused by: java.lang.IllegalStateException: deepest diagnostic cause"),"deepest cause stays in report");
        check(retained.contains("Game.frame399(Game.java:400)"),"exception stack is not cut off at 300 lines");
        check(retained.contains("Captured errors: 1")&&retained.contains("Log events: 6101"),"errors and event totals are retained");
        System.out.println("PASS: "+assertions+" behavioral assertions");
    }
}
