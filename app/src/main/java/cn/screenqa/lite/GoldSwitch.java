package cn.screenqa.lite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Hand drawn switch: a spring driven track and thumb, so the gold theme stays consistent. */
final class GoldSwitch extends View {
    interface Listener { void onChanged(boolean checked); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private ThemePalette palette = ThemePalette.DEFAULT;
    private boolean checked, reduceMotion, enabled = true;
    private float progress;
    private ValueAnimator animator;
    private Listener listener;

    GoldSwitch(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setOnClickListener(view -> toggle());
    }

    void setPalette(ThemePalette value) { palette = value; invalidate(); }
    void setReduceMotion(boolean value) { reduceMotion = value; }
    void setListener(Listener value) { listener = value; }

    boolean isChecked() { return checked; }

    void setChecked(boolean value) { setChecked(value, false); }

    void setChecked(boolean value, boolean animate) {
        if (checked == value) { progress = value ? 1f : 0f; invalidate(); return; }
        checked = value;
        if (animator != null) { animator.cancel(); animator = null; }
        if (!animate || reduceMotion) {
            progress = checked ? 1f : 0f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, checked ? 1f : 0f);
        animator.setDuration(Motion.BASE);
        animator.setInterpolator(Motion.SPRING);
        animator.addUpdateListener(animation -> { progress = (float) animation.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        enabled = value;
        setAlpha(value ? 1f : 0.4f);
        invalidate();
    }

    private void toggle() {
        if (!enabled) return;
        setChecked(!checked, true);
        if (listener != null) listener.onChanged(checked);
    }

    /** Programmatic flips (theme rebuilds, resets) must not re-enter the listener. */
    void setCheckedSilently(boolean value) { setChecked(value, false); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(Ui.dp(this, 54f), widthMeasureSpec),
                resolveSize(Ui.dp(this, 32f), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        track.set(Ui.dp(this, 1f), Ui.dp(this, 1f), w - Ui.dp(this, 1f), h - Ui.dp(this, 1f));
        float radius = track.height() / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ThemePalette.blend(palette.surfaceAlt, palette.accent, progress));
        canvas.drawRoundRect(track, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, Ui.dp(this, 1f)));
        paint.setColor(ThemePalette.blend(palette.border, palette.accent, progress));
        canvas.drawRoundRect(track, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        float thumbRadius = h / 2f - Ui.dp(this, 4f);
        float minX = h / 2f, maxX = w - h / 2f;
        float cx = minX + (maxX - minX) * progress;
        if (progress > 0.02f) {
            paint.setColor(ThemePalette.alpha(palette.accent, 0.18f * progress));
            canvas.drawCircle(cx, h / 2f, thumbRadius * 1.75f, paint);
        }
        paint.setColor(ThemePalette.blend(palette.secondary, palette.onAccent, progress));
        canvas.drawCircle(cx, h / 2f, thumbRadius, paint);
    }
}
