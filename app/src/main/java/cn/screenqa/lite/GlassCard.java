package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Liquid glass container. The layout is measured by hand because a match-parent glass child inside a
 * wrap-content parent would claim the whole screen; here the glass always takes the content height.
 */
final class GlassCard extends FrameLayout {
    private final LiquidGlassView glass;
    private final LinearLayout body;
    private final int inset;

    GlassCard(Context context, ThemePalette palette, boolean glassEnabled, float cornerDp) {
        super(context);
        inset = Ui.dp(context, 13f) + Ui.dp(context, 18f);
        glass = new LiquidGlassView(context);
        glass.setCornerRadius(Ui.dp(context, cornerDp));
        glass.setShadowRoom(Ui.dp(context, 13f));
        glass.setPalette(palette);
        glass.setGlassEnabled(glassEnabled);
        addView(glass, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(inset, inset, inset, inset);
        addView(body, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    LinearLayout body() { return body; }
    LiquidGlassView glass() { return glass; }

    void setPalette(ThemePalette palette) { glass.setPalette(palette); }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // The backdrop must contain what is behind this card, never its own icon/text.
        // Otherwise the blurred, magnified copy shows through beneath the sharp foreground.
        if (!glass.isCapturingBackdrop()) super.dispatchDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) width = Ui.dp(this, 320f);
        body.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
        int height = body.getMeasuredHeight();
        glass.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        glass.layout(0, 0, width, height);
        body.layout(0, 0, width, height);
    }

    @Override
    public void setElevation(float value) {
        super.setElevation(value);
        View child = getChildAt(0);
        if (child != null) child.setElevation(value);
    }
}
