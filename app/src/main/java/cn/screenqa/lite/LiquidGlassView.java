package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Liquid glass surface: a blurred sample of whatever is behind it, a tint, a specular sheen,
 * a refraction edge and two slowly drifting caustics. Drawn by hand so it needs no library.
 */
final class LiquidGlassView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path shape = new Path();
    private final RectF src = new RectF(), dst = new RectF(), panel = new RectF();
    private final Rect srcRect = new Rect(), dstRect = new Rect();
    private ThemePalette palette = ThemePalette.DEFAULT;
    private Bitmap backdrop;
    private float backdropScale = 1f, originX, originY;
    private float cornerRadiusPx, shadowRoomPx, phase;
    private boolean glass = true;
    private boolean capturingBackdrop;

    LiquidGlassView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    void setPalette(ThemePalette value) { palette = value; invalidate(); }
    void setGlassEnabled(boolean value) { glass = value; invalidate(); }
    void setCornerRadius(float value) { cornerRadiusPx = value; invalidate(); }
    void setShadowRoom(float value) { shadowRoomPx = value; invalidate(); }
    /** 0..1 driver for the liquid caustics; call it from a slow animator. */
    void setLiquidPhase(float value) { phase = value; invalidate(); }
    void setCapturingBackdrop(boolean value) { capturingBackdrop = value; }
    boolean isCapturingBackdrop() { return capturingBackdrop; }

    void setBackdrop(Bitmap bitmap, float scale, float ox, float oy) {
        backdrop = bitmap;
        backdropScale = scale <= 0f ? 1f : scale;
        originX = ox;
        originY = oy;
        invalidate();
    }

    private float contentLeft() { return shadowRoomPx; }
    private float contentTop() { return shadowRoomPx; }
    private float contentRight() { return getWidth() - shadowRoomPx; }
    private float contentBottom() { return getHeight() - shadowRoomPx; }

    @Override
    protected void onDraw(Canvas canvas) {
        if (capturingBackdrop) return;
        float left = contentLeft(), top = contentTop(), right = contentRight(), bottom = contentBottom();
        if (right - left < 4f || bottom - top < 4f) return;
        float radius = Math.min(cornerRadiusPx, Math.min(right - left, bottom - top) / 2f);
        panel.set(left, top, right, bottom);
        paint.setStyle(Paint.Style.FILL);

        drawShadow(canvas, radius);

        shape.reset();
        shape.addRoundRect(panel, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(shape);
        if (glass) drawBackdrop(canvas, radius);
        drawTint(canvas, left, top, right, bottom, radius);
        drawSheen(canvas, left, top, right, bottom, radius);
        if (glass) drawCaustics(canvas, left, top, right, bottom);
        canvas.restore();
        drawEdge(canvas, radius);
    }

    /** Approximated soft shadow: a few expanding low-alpha rounded rectangles, no software layer. */
    private void drawShadow(Canvas canvas, float radius) {
        if (shadowRoomPx <= 0f) return;
        paint.setShader(null);
        for (int i = 6; i >= 1; i--) {
            float grow = shadowRoomPx * i / 6f;
            paint.setColor(ThemePalette.alpha(0xFF000000, 0.028f));
            canvas.drawRoundRect(new RectF(panel.left - grow, panel.top - grow * 0.35f + grow * 0.5f,
                    panel.right + grow, panel.bottom + grow), radius + grow, radius + grow, paint);
        }
    }

    private void drawBackdrop(Canvas canvas, float radius) {
        Bitmap bitmap = backdrop;
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 2) return;
        float scale = backdropScale;
        src.set((originX + panel.left) * scale, (originY + panel.top) * scale,
                (originX + panel.right) * scale, (originY + panel.bottom) * scale);
        src.left = clamp(src.left, 0f, bitmap.getWidth());
        src.top = clamp(src.top, 0f, bitmap.getHeight());
        src.right = clamp(src.right, 0f, bitmap.getWidth());
        src.bottom = clamp(src.bottom, 0f, bitmap.getHeight());
        if (src.right - src.left < 1f || src.bottom - src.top < 1f) return;
        // Magnify around the centre so the panel reads as a lens over the page.
        float cx = panel.centerX(), cy = panel.centerY(), k = 1.07f;
        dst.set(cx + (panel.left - cx) * k, cy + (panel.top - cy) * k,
                cx + (panel.right - cx) * k, cy + (panel.bottom - cy) * k);
        paint.setShader(null);
        paint.setAlpha(255);
        src.round(srcRect);
        dst.round(dstRect);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
    }

    private void drawTint(Canvas canvas, float left, float top, float right, float bottom, float radius) {
        paint.setShader(null);
        // Frosted, not opaque: the sampled backdrop has to stay visible through the tint.
        paint.setColor(ThemePalette.alpha(palette.glassTint, glass ? 0.16f : 1f));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
        if (!glass) {
            paint.setColor(ThemePalette.alpha(palette.surfaceAlt, 0.6f));
            canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
        }
    }

    private void drawSheen(Canvas canvas, float left, float top, float right, float bottom, float radius) {
        paint.setShader(new LinearGradient(left, top, left, top + (bottom - top) * 0.62f,
                ThemePalette.alpha(palette.glassSheen, 0.55f), ThemePalette.alpha(palette.glassSheen, 0f),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
        paint.setShader(new LinearGradient(left, bottom, left, bottom - (bottom - top) * 0.35f,
                ThemePalette.alpha(palette.accent, 0.14f), ThemePalette.alpha(palette.accent, 0f),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
    }

    /** Two accent blobs drifting on different periods; this is what makes the surface feel liquid. */
    private void drawCaustics(Canvas canvas, float left, float top, float right, float bottom) {
        float w = right - left, h = bottom - top;
        double angle = phase * 2 * Math.PI;
        float x1 = left + w * (0.5f + 0.34f * (float) Math.sin(angle));
        float y1 = top + h * (0.5f + 0.22f * (float) Math.cos(angle * 1.4));
        float x2 = left + w * (0.5f + 0.3f * (float) Math.sin(angle * 0.7 + 2.1));
        float y2 = top + h * (0.62f + 0.3f * (float) Math.cos(angle * 1.1 + 0.8));
        float r = Math.max(w, h) * 0.5f;
        paint.setShader(new RadialGradient(x1, y1, r,
                new int[]{ThemePalette.alpha(palette.accent, 0.15f), ThemePalette.alpha(palette.accent, 0f)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(new RadialGradient(x2, y2, r * 0.8f,
                new int[]{ThemePalette.alpha(palette.glassSheen, 0.55f), ThemePalette.alpha(palette.glassSheen, 0f)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(null);
    }

    private void drawEdge(Canvas canvas, float radius) {
        paint.setShader(new LinearGradient(panel.left, panel.top, panel.right, panel.bottom,
                ThemePalette.alpha(palette.glassEdge, 0.95f), ThemePalette.alpha(palette.glassEdge, 0.12f),
                Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, Ui.dp(this, 1.1f)));
        canvas.drawRoundRect(panel, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }
}
