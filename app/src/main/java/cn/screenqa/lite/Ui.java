package cn.screenqa.lite;

import android.content.Context;
import android.view.View;

/** Density helpers shared by the custom views. */
final class Ui {
    private Ui() {}
    static float density(Context context) { return context.getResources().getDisplayMetrics().density; }
    static int dp(Context context, float value) { return Math.round(value * density(context)); }
    static int dp(View view, float value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }
    static float sp(Context context, float value) { return value * context.getResources().getDisplayMetrics().scaledDensity; }
}
