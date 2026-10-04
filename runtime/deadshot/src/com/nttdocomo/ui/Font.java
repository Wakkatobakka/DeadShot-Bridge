package com.nttdocomo.ui;

import android.graphics.Paint;
import android.graphics.Typeface;

public class Font {
    private static final int SIZE_SMALL  = 0x70000100;
    private static final int SIZE_MEDIUM = 0x70000200;
    private static final int SIZE_LARGE  = 0x70000300;
    private static final int SIZE_TINY   = 0x70000400;

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Font(float size){
        paint.setTextSize(size);
        paint.setTypeface(Typeface.MONOSPACE);
    }
    public static Font getFont(int spec){
        int sizeBits=spec & 0x70000F00;
        if(sizeBits==SIZE_MEDIUM) return new Font(24f);
        if(sizeBits==SIZE_SMALL) return new Font(18f);
        if(sizeBits==SIZE_LARGE) return new Font(30f);
        if(sizeBits==SIZE_TINY) return new Font(12f);
        return new Font(12f);
    }
    public static Font getDefaultFont(){ return new Font(12f); }
    public int stringWidth(String s){ return s==null?0:(int)Math.ceil(paint.measureText(s)); }
    Paint __paint(){ return paint; }
}
