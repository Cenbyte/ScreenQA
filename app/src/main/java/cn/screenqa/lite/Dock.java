package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

/**
 * Bottom navigation surface. Two implementations:
 * {@link LiquidDock} (QWEA0/Liquid-Glass-Android, live backdrop refraction) and
 * {@link GlassDock} (hand-drawn fallback for devices without the native pipeline).
 */
interface Dock {
    interface Listener { void onSelected(int index); }

    /** Capsule height of the panel itself. */
    int PANEL_HEIGHT_DP = 68;
    /** Transparent room around the capsule: lens rim ring + drop shadow live here. */
    int ROOM_DP = 12;
    /** Side air between the capsule and the screen edge. */
    int SIDE_DP = 20;

    /** Total height the host must reserve, including the room and a large-font allowance. */
    static int heightFor(Context context) {
        float scale = context.getResources().getConfiguration().fontScale;
        int extra = scale >= 1.3f ? 10 : 0;
        return Ui.dp(context, PANEL_HEIGHT_DP + ROOM_DP * 2 + extra);
    }

    static int sideInset(Context context) { return Ui.dp(context, SIDE_DP); }

    void setPalette(ThemePalette palette);

    /** false: opaque frosted fallback (cheaper, no live sampling). */
    void setGlassEnabled(boolean enabled);

    void setReduceMotion(boolean reduce);

    void setItems(String[] titles, int[] iconRes);

    void setListener(Listener listener);

    void setSelected(int index, boolean animate);

    /** Slides the bar down and shrinks it while the page scrolls. */
    void setLifted(boolean lifted);

    /** Pre-captured backdrop for docks that render their own glass; ignorable otherwise. */
    void setBackdrop(Bitmap bitmap, float scale, float originX, float originY);

    /** View the dock samples live as its backdrop (the scrolling page layer). */
    void setBackdropSource(View source);

    /**
     * true when the dock owns page sampling: the host can skip a separate dock bitmap.
     */
    boolean liveSampling();
}
