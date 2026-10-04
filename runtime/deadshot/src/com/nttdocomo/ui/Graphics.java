package com.nttdocomo.ui;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import com.wakka.deadshot.Host;

public class Graphics {
    private final Bitmap target;
    private final boolean root;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Font font=Font.getDefaultFont();
    private int ox,oy;

    Graphics(Bitmap b,boolean root){ this.target=b; this.root=root; paint.setColor(android.graphics.Color.WHITE); }
    public void lock(){ Host.awaitForeground(); }
    public void unlock(boolean repaint){ if(repaint && root) { Host.frameCompleted(); Host.invalidate(); } }
    public static int getColorOfRGB(int r,int g,int b){ return android.graphics.Color.rgb(clamp(r),clamp(g),clamp(b)); }
    private static int clamp(int v){ return Math.max(0,Math.min(255,v)); }
    public void setColor(int c){ paint.setColor(c); }
    public void setFont(Font f){ if(f!=null) font=f; }
    public void setOrigin(int x,int y){ ox=x; oy=y; }

    public void fillRect(int x,int y,int w,int h){ synchronized(target){ android.graphics.Canvas c=new android.graphics.Canvas(target); c.drawRect(x+ox,y+oy,x+ox+w,y+oy+h,paint); } }
    public void drawRect(int x,int y,int w,int h){ synchronized(target){ android.graphics.Canvas c=new android.graphics.Canvas(target); Paint.Style old=paint.getStyle(); paint.setStyle(Paint.Style.STROKE); c.drawRect(x+ox,y+oy,x+ox+w,y+oy+h,paint); paint.setStyle(old); } }
    public void drawLine(int x1,int y1,int x2,int y2){ synchronized(target){ new android.graphics.Canvas(target).drawLine(x1+ox,y1+oy,x2+ox,y2+oy,paint); } }
    public void drawString(String s,int x,int y){ if(s==null)return; synchronized(target){ android.graphics.Canvas c=new android.graphics.Canvas(target); Paint fp=new Paint(font.__paint()); fp.setColor(paint.getColor()); c.drawText(s,x+ox,y+oy,fp); } }
    public void fillPolygon(int[] xs,int[] ys,int n){ if(xs==null||ys==null||n<=0)return; synchronized(target){ Path path=new Path(); path.moveTo(xs[0]+ox,ys[0]+oy); for(int i=1;i<n&&i<xs.length&&i<ys.length;i++) path.lineTo(xs[i]+ox,ys[i]+oy); path.close(); new android.graphics.Canvas(target).drawPath(path,paint); } }
    public void drawImage(Image image,int x,int y){ if(image==null)return; synchronized(target){ new android.graphics.Canvas(target).drawBitmap(image.bitmap,x+ox,y+oy,paint); } }
    public void drawImage(Image image,int x,int y,int sx,int sy,int w,int h){ if(image==null)return; synchronized(target){ Rect src=new Rect(sx,sy,sx+w,sy+h); Rect dst=new Rect(x+ox,y+oy,x+ox+w,y+oy+h); new android.graphics.Canvas(target).drawBitmap(image.bitmap,src,dst,paint); } }
    public void drawScaledImage(Image image,int x,int y,int w,int h,int sx,int sy,int sw,int sh){ if(image==null)return; synchronized(target){ Rect src=new Rect(sx,sy,sx+sw,sy+sh); Rect dst=new Rect(x+ox,y+oy,x+ox+w,y+oy+h); new android.graphics.Canvas(target).drawBitmap(image.bitmap,src,dst,paint); } }
}
