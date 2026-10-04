package com.wakka.deadshot;
import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.IApplication;
import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.BridgeImportBackend;
import com.wakka.bridge.GameSpec;
import com.wakka.bridge.KeypadView;
import com.wakka.bridge.ReportStore;
import java.lang.reflect.Field;
import java.util.UUID;
/** Original DeadShot host behind the accepted, unchanged Omni shell. */
public final class DeadshotBackend implements BridgeBackend,BridgeImportBackend,Host.Events {
    private static final GameSpec SPEC=new GameSpec("deadshot-doja","DEVIL MAY CRY: DEADSHOT",
        "Original DeadShot runtime · shared Wakkan bridge","DeadShot Bridge 0.0.12 / shared shell 0.1.0",
        "DoJa / DeadShot 240×240","PLAY DEADSHOT","deadshot-icon.png",null,
        "MOVEMENT\nDeadShot chooses its own control family. Modes 0/2 use four-way D-pad movement; modes 1/3 use eight-way numeric movement. The custom movement surface follows that setting automatically.\n\nPRIMARY ACTION\nManual modes show ATTACK; auto-attack modes show DIR LOCK. Modes 0/2 send native # and modes 1/3 send native OK. The full original phone keypad remains available from the toolbar.\n\nBRIDGE\nToolbar Menu / Android Back returns to the bridge and retains this session. Pause stops at the existing Graphics.lock boundary. Report exports this session without restarting it. Returning uses DeadShot's original resume behavior.\n\nThe original DeadShot app and its saves remain separate.",
        "Payload import and gameplay path passed owner phone testing. Audio uses the preserved four MIDI resources; pause/resume follows DeadShot's original resume policy. Pause is at a graphics boundary, not an exact save state.",0xffc04359);
    private final Context app;
    private final NativeEventRouter input=new NativeEventRouter(this::sendNative);
    private GameView controls;
    private GameDisplayView display;
    private KeypadView keypad;
    private volatile boolean started,ended;
    private volatile Throwable fatal;
    private String session="not-started";
    private int ergonomicMask;
    private volatile long presses,releases;
    private volatile String lastInput="none";
    private Field controlMode;
    private boolean modeLookupTried;
    public DeadshotBackend(Context context) {
        app=context.getApplicationContext();Host.events(this);
        Thread.UncaughtExceptionHandler prior=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            fail(error);DeadshotDiagnostics.event("UNCAUGHT THREAD",thread.getName());
            try { ReportStore.preserveLatest(app,ReportStore.compose(app,this)); } catch(Throwable ignored) {}
            if(prior!=null) prior.uncaughtException(thread,error);
        });
    }
    public GameSpec spec() { return SPEC; }
    public boolean ready() { return PayloadStore.ready(app); }
    public String readiness() { return PayloadStore.status(app); }
    public String importButtonLabel() { return ready()?"REPLACE DEADSHOT DATA":"IMPORT DEADSHOT DATA"; }
    public String importDialogTitle() { return "DeadShot data ready"; }
    public String importDialogMessage() { return "Verified game data was copied into DeadShot Bridge private storage. You can now play offline."; }
    public void importPayload(Activity activity,android.net.Uri uri) throws Exception {
        if(running()) throw new IllegalStateException("Return from the live game session before replacing game data.");
        PayloadStore.importZip(activity,uri);
    }
    public View attachDisplay(Activity activity) {
        display=new GameDisplayView(activity);Host.attach(activity,display,null);
        if(fatal!=null) display.setFatal(fatal);
        Host.setForeground(true); // Original onCreate allows start before onResume.
        return display;
    }
    public View createControls(Activity activity,boolean nativeKeypad) {
        if(controls==null) controls=new GameView(activity);Host.attach(activity,display,controls);
        if(nativeKeypad) {
            String[] labels={"L soft","▲","R soft","OK","◀","▼","▶","","1","2","3","*","4","5","6","#","7","8","9","0"};
            int[] keys={21,17,22,20,16,19,18,-1,1,2,3,10,4,5,6,11,7,8,9,0};
            int[] masks=new int[20];for(int i=0;i<20;i++) masks[i]=keys[i]<0?0:1<<keys[i];
            keypad=new KeypadView(activity,labels,masks,new KeypadView.Sink() {
                public void set(int contact,int mask) { if(Host.canRun()) input.set(contact,mask); }
                public void remove(int contact) { input.remove(contact); }
            });return keypad;
        }
        keypad=null;controls.setKeypadMode(false);return controls;
    }
    public void detachViews() { releaseInput();Host.detach();controls=null;display=null;keypad=null; }
    public synchronized void start() {
        if(started&&!ended) return;
        if(started) try { ReportStore.preserveLatest(app,ReportStore.compose(app,this)); } catch(Exception ignored) {}
        input.clear();ergonomicMask=0;presses=releases=0;lastInput="none";fatal=null;ended=false;
        session=UUID.randomUUID().toString();DeadshotDiagnostics.reset();started=true;
        DeadshotDiagnostics.event("SESSION",session+" / original runtime 0.0.12");
        DeadshotDiagnostics.detail("Game JAR SHA256",PayloadStore.GAME_JAR_SHA256);
        DeadshotDiagnostics.detail("Audio implementation","Verified user-supplied DeadShot MIDI payload / MediaPlayer");
        try {
            ScratchpadStore.init(app);
            Object game=PayloadStore.gameClass(app,"DEADSHOTN505").getDeclaredConstructor().newInstance();
            if(!(game instanceof IApplication)) throw new IllegalStateException("Not a DoJa IApplication");
            ((IApplication)game).start();((IApplication)game).resume();
            DeadshotDiagnostics.event("BOOT","DEADSHOTN505.start returned; original game owns its worker thread");
        } catch(Throwable error) {
            if(error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null) error=error.getCause();fail(error);
        }
    }
    public boolean started() { return started; }
    public boolean running() { return started&&!ended&&fatal==null; }
    private void resumeNative() {
        IApplication game=IApplication.getCurrentApp();
        if(game!=null&&running()) try { game.resume();DeadshotDiagnostics.event("RESUME","Original game.resume: clears keys and schedules BGM restart"); }
        catch(Throwable error) { DeadshotDiagnostics.error("RESUME",error); }
    }
    public void setForeground(boolean value) {
        boolean was=Host.canRun();if(value&&!Host.isUserPaused()&&!was) resumeNative();Host.setForeground(value);
        if(was&&!Host.canRun()) AudioPresenter.getAudioPresenter().stop();
        if(started&&was!=Host.canRun()) DeadshotDiagnostics.event("FOREGROUND",value?"entered":"background / graphics boundary blocked");
    }
    public void setUserPaused(boolean value) {
        boolean was=Host.canRun();if(!value&&Host.isForeground()&&!was) resumeNative();Host.setUserPaused(value);
        if(was&&!Host.canRun()) AudioPresenter.getAudioPresenter().stop();
        if(started) DeadshotDiagnostics.event("USER PAUSE",Boolean.toString(value));
    }
    public boolean userPaused() { return Host.isUserPaused(); }
    public void releaseInput() {
        if(controls!=null) controls.cancelContacts();if(keypad!=null) keypad.cancelAll();ergonomicMask=0;input.clear();
    }
    public boolean hardwareKey(int code,boolean down) {
        if(controls==null) return false;int key=controls.mapHardwareKey(code);if(key<0) return false;
        int owner=-code-1;if(down) { if(Host.canRun()) input.set(owner,1<<key); }else input.remove(owner);
        return true;
    }
    public void keyEvent(int type,int key) {
        if(type==0&&!Host.canRun()) return;
        if(type==0) ergonomicMask|=1<<key;else ergonomicMask&=~(1<<key);input.set(10000,ergonomicMask);
    }
    private void sendNative(int type,int key) {
        Frame frame=Host.currentFrame();
        if(!(frame instanceof com.nttdocomo.ui.Canvas)) { DeadshotDiagnostics.count("Input events without active Canvas");return; }
        try {
            ((com.nttdocomo.ui.Canvas)frame).processEvent(type,key);
            if(type==0) presses++;else releases++;
            lastInput=(type==0?"press ":"release ")+keyName(key)+" ("+key+")";DeadshotDiagnostics.event("INPUT",lastInput);
        } catch(Throwable error) { fail(error); }
    }
    public void terminated() {
        releaseInput();ended=true;AudioPresenter.getAudioPresenter().stop();DeadshotDiagnostics.event("SESSION","Game called terminate");
        try { ReportStore.preserveLatest(app,ReportStore.compose(app,this)); } catch(Exception error) { DeadshotDiagnostics.error("PRESERVE",error); }
    }
    private void fail(Throwable error) {
        fatal=error;DeadshotDiagnostics.error("GAME ERROR",error);
        if(display!=null) display.setFatal(error);if(controls!=null) controls.setFatal(error);
    }
    private int mode() {
        if(!started) return -1;
        try {
            if(!modeLookupTried) { modeLookupTried=true;controlMode=PayloadStore.gameClass(app,"InitAll").getDeclaredField("key_con");controlMode.setAccessible(true); }
            return controlMode==null?-1:controlMode.getInt(null);
        } catch(Exception error) { return -1; }
    }
    private String soft(int index) { Frame frame=Host.currentFrame();return frame==null?"unavailable":String.valueOf(frame.__softLabel(index)); }
    private static String keyName(int key) {
        if(key<=9) return Integer.toString(key);
        switch(key) { case 10:return "*";case 11:return "#";case 16:return "LEFT";case 17:return "UP";case 18:return "RIGHT";
            case 19:return "DOWN";case 20:return "OK";case 21:return "SOFT1";case 22:return "SOFT2";default:return "UNKNOWN"; }
    }
    public String status() {
        if(fatal!=null) return "Runtime error · "+fatal.getClass().getSimpleName();if(!started) return "Ready";if(ended) return "Session ended";
        int mode=mode();return (Host.canRun()?"Live":"Paused")+" · "+(mode==1||mode==3?"8-way keypad":"4-way D-pad")+" · "+soft(0)+" / "+soft(1);
    }
    private String appVersion() {
        try { android.content.pm.PackageInfo info=app.getPackageManager().getPackageInfo(app.getPackageName(),0);return info.versionName+" (code "+info.versionCode+")"; }
        catch(Exception error) { return "unavailable"; }
    }
    public String snapshot() {
        if(!started) return "SESSION SUMMARY\nApp version: "+appVersion()+"\nState: NOT STARTED\nNo game was launched by this report. Scratchpad storage was not initialized.\nRuntime baseline: DeadShot 0.0.12\nGame JAR SHA256: "+PayloadStore.GAME_JAR_SHA256+"\n";
        String state=fatal!=null?"FAILED":ended?"ENDED":Host.canRun()?"LIVE / RUNNING":"LIVE / PAUSED";
        Frame frame=Host.currentFrame();
        return "SESSION SUMMARY\nApp version: "+appVersion()+"\nState: "+state+"\nSession ID: "+session+"\nRuntime baseline: DeadShot 0.0.12\n"
            +"Foreground: "+Host.isForeground()+"\nExplicit user pause: "+userPaused()+"\n"
            +"Native press events: "+presses+"\nNative release events: "+releases+"\nLast input: "+lastInput+"\n"
            +"Held native mask: 0x"+Integer.toHexString(input.mask())+"\nInput owners: "+input.contacts()+"\n"
            +"Game key_con mode: "+mode()+"\nSoft1 / Soft2: "+soft(0)+" / "+soft(1)+"\n"
            +"Current frame: "+(frame==null?"none":frame.getClass().getName())+"\nFramebuffer: 240 x 240, original renderer\n"
            +"View attached: "+(display!=null)+"\n\n"+DeadshotDiagnostics.snapshot();
    }
}
