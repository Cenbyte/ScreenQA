package cn.screenqa.lite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A density-independent native loading indicator with no animation dependency. */
final class LoadingRingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 360f);
    private int color;
    private float angle;
    private boolean running;

    LoadingRingView(Context context) {
        super(context);
        setVisibility(GONE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        animator.setDuration(850);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> { angle = (float) a.getAnimatedValue(); invalidate(); });
    }

    void setRingColor(int value) { color = value; invalidate(); }
    void setRunning(boolean value) {
        if (running == value) return;
        running = value;
        if (value) animator.start(); else animator.cancel();
        setVisibility(value ? VISIBLE : GONE);
    }

    @Override protected void onDetachedFromWindow() {
        animator.cancel(); running = false; super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = paint.getStrokeWidth() / 2f + 1f;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        paint.setColor(color);
        canvas.drawArc(bounds, angle - 90f, 265f, false, paint);
    }
}
