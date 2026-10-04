package com.wakka.deadshot;
import android.app.Activity;
import com.nttdocomo.ui.Frame;
import com.wakka.bridge.SessionGate;
/** Preserve native host entry points and attach views to the existing session. */
public final class Host {
    public interface Events { void keyEvent(int type,int key); void terminated(); boolean hardwareKey(int code,boolean down); }
    private static volatile Activity activity;
    private static volatile GameView padView;
    private static volatile GameDisplayView displayView;
    private static volatile Frame currentFrame;
    private static volatile Events events;
    private static final SessionGate gate=new SessionGate();
    private Host() {}
    public static void events(Events value) { events=value; }
    public static void attach(Activity a,GameDisplayView display,GameView pad) { activity=a;displayView=display;padView=pad; }
    public static void detach() { displayView=null;padView=null;activity=null; }
    public static Activity activity() { return activity; }
    public static GameView view() { return padView; }
    public static Frame currentFrame() { return currentFrame; }
    public static void setCurrentFrame(Frame frame) {
        currentFrame=frame;DeadshotDiagnostics.event("DISPLAY",frame==null?"null":frame.getClass().getName());invalidate();
    }
    public static void invalidate() {
        DeadshotDiagnostics.count("Display invalidation requests");
        GameDisplayView display=displayView;GameView pad=padView;
        if(display!=null) display.postInvalidateOnAnimation();if(pad!=null) pad.postInvalidateOnAnimation();
    }
    public static void frameCompleted() { DeadshotDiagnostics.count("Completed root frame unlocks"); }
    public static void setForeground(boolean value) { gate.foreground(value); }
    public static void setUserPaused(boolean value) { gate.userPaused(value); }
    public static boolean isUserPaused() { return gate.isUserPaused(); }
    public static boolean isForeground() { return gate.isForeground(); }
    public static boolean canRun() { return !gate.blocked(); }
    public static void awaitForeground() { gate.awaitRunning(); }
    public static void routeKey(int type,int key) { Events sink=events;if(sink!=null) sink.keyEvent(type,key); }
    public static boolean routeHardware(int code,boolean down) { Events sink=events;return sink!=null&&sink.hardwareKey(code,down); }
    public static void terminate() {
        Events sink=events;if(sink!=null) sink.terminated();
        final Activity a=activity;if(a!=null) a.runOnUiThread(a::finish);
    }
}
