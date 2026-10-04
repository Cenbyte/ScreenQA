package cn.screenqa.lite;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Translucent tint, fine grain and a quiet rim, drawn over the system's background blur. */
final class FrostedGlassDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF body = new RectF();
    private final float density, radius, tailInset;
    private ThemePalette palette = ThemePalette.DEFAULT;
    private boolean blurred, tailLeft = true;
    private float tailCenter;
    private int drawableAlpha = 255;
    private float[] grain = new float[0];
    private Shader tint;

    FrostedGlassDrawable(float density, float radiusDp, boolean chat) {
        this.density = density;
        radius = radiusDp * density;
        tailInset = chat ? 8 * density : 0;
    }

    void setPalette(ThemePalette value) { palette = value; updateTint(); invalidateSelf(); }
    void setBlurred(boolean value) { blurred = value; updateTint(); invalidateSelf(); }
    void setTail(boolean left, float center) {
        tailLeft = left; tailCenter = center; updateShape(); invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        body.set(bounds.left + tailInset, bounds.top, bounds.right - tailInset, bounds.bottom);
        updateShape(); updateTint();
        int count = Math.min(700, Math.max(0, (int) (body.width() * body.height() / (22 * density * density))));
        grain = new float[count * 2];
        int seed = 73;
        for (int i = 0; i < count; i++) {
            seed = seed * 1664525 + 1013904223;
            grain[2 * i] = body.left + (seed >>> 1) / (float) Integer.MAX_VALUE * body.width();
            seed = seed * 1664525 + 1013904223;
            grain[2 * i + 1] = body.top + (seed >>> 1) / (float) Integer.MAX_VALUE * body.height();
        }
    }

    private void updateShape() {
        shape.reset();
        if(tailInset>0){
            float small=4*density;
            shape.addRoundRect(body,new float[]{radius,radius,radius,radius,
                    tailLeft?radius:small,tailLeft?radius:small,
                    tailLeft?small:radius,tailLeft?small:radius},Path.Direction.CW);
        }else shape.addRoundRect(body, radius, radius, Path.Direction.CW);
        if (tailInset == 0 || body.height() < 36 * density) return;
        float y = Math.max(body.top + 12 * density, Math.min(body.bottom - 12 * density, body.top + tailCenter));
        float x = tailLeft ? body.left : body.right;
        float tip = tailLeft ? getBounds().left : getBounds().right;
        // A curved chat tail stays attached to the character instead of a sharp triangle.
        shape.moveTo(x, y - 7 * density);
        shape.cubicTo(x, y - 2 * density, tip, y + 2 * density, tip, y + 3 * density);
        shape.quadTo(x, y + 8 * density, x, y + 7 * density);
        shape.close();
    }

    private void updateTint() {
        if (body.height() <= 0) return;
        float alpha = blurred ? (palette.dark ? .56f : .62f) : (palette.dark ? .91f : .93f);
        int top = ThemePalette.blend(palette.surface, 0xFFFFFFFF, palette.dark ? .06f : .16f);
        int bottom = ThemePalette.blend(palette.surface, palette.accent, .035f);
        if(tailInset>0){top=0xFFF3F6F9;bottom=top;alpha=blurred?.78f:.95f;}
        tint = new LinearGradient(0, body.top, 0, body.bottom,
                ThemePalette.alpha(top, alpha), ThemePalette.alpha(bottom, alpha), Shader.TileMode.CLAMP);
    }

    @Override public void draw(Canvas canvas) {
        if (body.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL); paint.setShader(tint); paint.setAlpha(drawableAlpha);
        canvas.drawPath(shape, paint);
        paint.setShader(null);
        int saved = canvas.save(); canvas.clipPath(shape);
        paint.setColor(ThemePalette.alpha(0xFFFFFFFF, .055f * drawableAlpha / 255f));
        paint.setStrokeWidth(Math.max(.65f, density * .45f));
        canvas.drawPoints(grain, paint); canvas.restoreToCount(saved);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1f, density * .65f));
        paint.setColor(ThemePalette.alpha(palette.dark ? 0xFFFFFFFF : palette.border,
                (tailInset>0?.12f:palette.dark ? .22f : .42f) * drawableAlpha / 255f));
        canvas.drawPath(shape, paint); paint.setStyle(Paint.Style.FILL);
    }

    @Override public void getOutline(Outline outline) {
        // The compositor blurs only the rounded body, never the rest of the screen.
        outline.setRoundRect(Math.round(body.left), Math.round(body.top), Math.round(body.right),
                Math.round(body.bottom), radius);
        outline.setAlpha(1f);
    }
    @Override public void setAlpha(int alpha) { drawableAlpha = alpha; invalidateSelf(); }
    @Override public int getAlpha() { return drawableAlpha; }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
