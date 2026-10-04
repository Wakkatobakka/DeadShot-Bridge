package com.nttdocomo.ui;
import android.graphics.Bitmap;
public class Image {
    final Bitmap bitmap;
    Image(Bitmap b){ bitmap=b; }
    public int getWidth(){ return bitmap.getWidth(); }
    public int getHeight(){ return bitmap.getHeight(); }
    public Graphics getGraphics(){ return new Graphics(bitmap,false); }
    public static Image createImage(int w,int h){ return new Image(Bitmap.createBitmap(Math.max(1,w),Math.max(1,h),Bitmap.Config.ARGB_8888)); }
}
