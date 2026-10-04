package com.wakka.deadshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import com.nttdocomo.ui.Frame;

/**
 * DeadShot's 240x240 game display only.  The outer activity now owns the shell,
 * exactly like Dirge Bridge: toolbar -> flexible game stage -> status -> pad.
 */
final class GameDisplayView extends View {
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dst = new Rect();
    private Throwable fatal;

    GameDisplayView(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
    }

    void setFatal(Throwable t) {
        fatal = t;
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(android.graphics.Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.BLACK);

        Frame f = Host.currentFrame();
        if (f instanceof com.nttdocomo.ui.Canvas) {
            Bitmap b = ((com.nttdocomo.ui.Canvas) f).__bitmap();
            if (b != null) {
                int w = getWidth();
                int h = getHeight();
                int side = Math.max(1, Math.min(w, h));
                int left = (w - side) / 2;
                int top = (h - side) / 2;
                dst.set(left, top, left + side, top + side);
                synchronized (b) {
                    c.drawBitmap(b, null, dst, bitmapPaint);
                    DeadshotDiagnostics.count("Android game-view bitmap draws");
                }
            }
        }

        if (fatal != null) {
            textPaint.setColor(0xFFFF8080);
            textPaint.setTextSize(Ui.dp(getContext(), 14));
            float x = Ui.dp(getContext(), 10);
            float y = Ui.dp(getContext(), 28);
            c.drawText("DeadShot Bridge boot error", x, y, textPaint);
            y += Ui.dp(getContext(), 22);
            String s = String.valueOf(fatal);
            for (int i = 0; i < s.length(); i += 44) {
                c.drawText(s.substring(i, Math.min(s.length(), i + 44)), x, y, textPaint);
                y += Ui.dp(getContext(), 18);
            }
        }
    }
}
