package com.nttdocomo.ui;
import android.util.Log;
public class Dialog {
    private final String title; private String text="";
    public Dialog(int type,String title){ this.title=title; }
    public void setText(String t){ text=t; }
    public int show(){ Log.w("DeadShotBridge","DoJa Dialog: "+title+" / "+text); return 0; }
}
