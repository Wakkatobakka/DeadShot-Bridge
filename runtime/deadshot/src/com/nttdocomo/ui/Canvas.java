package com.nttdocomo.ui;

import android.graphics.Bitmap;

public class Canvas extends Frame {
    private final Bitmap bitmap;
    private final Graphics graphics;
    public Canvas(){
        bitmap=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888);
        graphics=new Graphics(bitmap,true);
    }
    public Graphics getGraphics(){ return graphics; }
    public void paint(Graphics g){}
    public void processEvent(int type,int param){}
    public Bitmap __bitmap(){ return bitmap; }
}
