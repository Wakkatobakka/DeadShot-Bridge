package com.nttdocomo.ui;

import android.app.Activity;
import android.media.MediaPlayer;
import com.wakka.deadshot.Host;
import com.wakka.deadshot.PayloadStore;
import com.wakka.deadshot.DeadshotDiagnostics;

public class AudioPresenter extends MediaPresenter {
    private static final AudioPresenter INSTANCE=new AudioPresenter();
    private MediaSound sound;
    private MediaListener listener;
    private MediaPlayer player;
    private volatile int generation;

    public static AudioPresenter getAudioPresenter(){ return INSTANCE; }

    public void stop(){
        DeadshotDiagnostics.count("Audio stop requests");DeadshotDiagnostics.detail("Audio state","STOP REQUESTED");
        final Activity a=Host.activity(); final int gen=++generation;
        if(a==null){ releasePlayer(); return; }
        a.runOnUiThread(new Runnable(){ public void run(){ if(gen!=generation)return; releasePlayer(); }});
    }
    public void setSound(MediaSound s){ sound=s; }
    public void setMediaListener(MediaListener l){ listener=l; }
    public void play(){
        DeadshotDiagnostics.count("Audio play requests");
        final Activity a=Host.activity(); final MediaSound selected=sound; final MediaListener callback=listener; final int gen=++generation;
        if(a==null || !(selected instanceof MediaManager.SimpleMediaSound)) { DeadshotDiagnostics.count("Audio requests without activity/sound");return; }
        final int index=((MediaManager.SimpleMediaSound)selected).index; if(index<0||index>3){ DeadshotDiagnostics.count("Audio unmapped track requests");return; }
        a.runOnUiThread(new Runnable(){ public void run(){
            if(gen!=generation)return;
            if(!Host.canRun()){ DeadshotDiagnostics.count("Audio requests suppressed while paused");return; }
            releasePlayer();
            try {
                java.io.File source=PayloadStore.midi(a,index);
                MediaPlayer mp=new MediaPlayer();
                mp.setDataSource(source.getAbsolutePath());
                mp.prepare();
                player=mp;
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){ public void onCompletion(MediaPlayer completed){
                    if(gen!=generation||completed!=player)return; DeadshotDiagnostics.count("Audio track completions");DeadshotDiagnostics.detail("Audio state","COMPLETED track "+index);releasePlayer();
                    if(callback!=null){ try{ callback.mediaAction(AudioPresenter.this,3,0); }catch(Throwable error){ DeadshotDiagnostics.error("AUDIO CALLBACK",error); } }
                }});
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener(){ public boolean onError(MediaPlayer broken,int what,int extra){ DeadshotDiagnostics.count("Audio player errors");DeadshotDiagnostics.event("AUDIO ERROR", "track="+index+" what="+what+" extra="+extra);DeadshotDiagnostics.detail("Audio state","PLAYER ERROR");if(broken==player)releasePlayer(); return true; }});
                mp.start();DeadshotDiagnostics.count("Audio track starts");DeadshotDiagnostics.detail("Audio state","PLAYING track "+index);DeadshotDiagnostics.event("AUDIO", "Started original MIDI track "+index);
            } catch(Throwable error){ DeadshotDiagnostics.error("AUDIO PLAY",error);DeadshotDiagnostics.detail("Audio state","PLAY FAILED");releasePlayer(); }
        }});
    }
    private void releasePlayer(){ MediaPlayer q=player; player=null; if(q!=null){ try{q.setOnCompletionListener(null);}catch(Throwable ignored){} try{q.setOnErrorListener(null);}catch(Throwable ignored){} try{q.stop();}catch(Throwable ignored){} try{q.release();}catch(Throwable ignored){} } }
}
