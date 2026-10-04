package com.nttdocomo.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.wakka.deadshot.ScratchpadStore;
import com.wakka.deadshot.DeadshotDiagnostics;

public final class MediaManager {
    private MediaManager(){}
    public static MediaImage getImage(String uri){
        int pos=parsePos(uri); byte[] d=ScratchpadStore.snapshot();
        DeadshotDiagnostics.count("Image requests");
        if(d==null||pos<0||pos>=d.length) { DeadshotDiagnostics.count("Image decode failures");DeadshotDiagnostics.event("IMAGE", "Invalid scratchpad position: "+pos);return new SimpleMediaImage(null); }
        Bitmap b=BitmapFactory.decodeByteArray(d,pos,d.length-pos);
        DeadshotDiagnostics.count(b==null?"Image decode failures":"Image decode successes");
        DeadshotDiagnostics.event("IMAGE", "position="+pos+" / "+(b==null?"decode failed":b.getWidth()+"x"+b.getHeight()));
        return new SimpleMediaImage(b==null?null:new Image(b));
    }
    public static MediaSound getSound(String uri){
        int pos=parsePos(uri); int index=-1;
        if(pos==71730) index=0; else if(pos==74277) index=1; else if(pos==77432) index=2; else if(pos==78597) index=3;
        DeadshotDiagnostics.count("Sound resource requests");DeadshotDiagnostics.event("SOUND", "position="+pos+" / MIDI index="+index);
        if(index<0) DeadshotDiagnostics.count("Unmapped sound resources");
        return new SimpleMediaSound(index,pos);
    }
    private static int parsePos(String s){ int i=s==null?-1:s.indexOf("pos="); if(i<0)return 0; int start=i+4,end=start; while(end<s.length()&&Character.isDigit(s.charAt(end)))end++; try{return Integer.parseInt(s.substring(start,end));}catch(Exception e){return 0;} }
    private static final class SimpleMediaImage implements MediaImage {
        private Image image; SimpleMediaImage(Image i){image=i;}
        public void use(){} public void unuse(){} public void dispose(){} public Image getImage(){return image;}
    }
    static final class SimpleMediaSound implements MediaSound {
        final int index; final int scratchpadPos;
        SimpleMediaSound(int i,int p){index=i;scratchpadPos=p;}
        public void use(){} public void unuse(){} public void dispose(){}
    }
}
