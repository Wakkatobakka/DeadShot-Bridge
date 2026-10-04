package com.wakka.deadshot;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.TextView;

/** Shared Android host UI helpers. */
final class Ui {
    private Ui() {}

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    /** Exact system-bar inset behavior used by the supplied Dirge Bridge v0.2.4. */
    static void insets(View root) {
        root.setBackgroundColor(0xFF080A10);
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            } else {
                view.setPadding(
                        windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(),
                        windowInsets.getSystemWindowInsetBottom());
            }
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    /** Same toolbar button defaults as Dirge Bridge v0.2.4. */
    static Button button(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15f);
        b.setMinHeight(dp(c, 44));
        return b;
    }

    /** Same small status text defaults as Dirge Bridge v0.2.4. */
    static TextView text(Context c, String text, int sp) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(0xFFE5E9F1);
        int p = dp(c, 5);
        t.setPadding(p, p, p, p);
        return t;
    }
}
