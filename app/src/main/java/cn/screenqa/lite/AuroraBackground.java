package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.os.SystemClock;

/**
 * Three huge, slow colour fields behind every page. They exist so the glass dock always has
 * something to refract, and they are pre-rendered blobs so drawing stays cheap.
 */
final class AuroraBackground extends View {
    private static final int BLOB_PX = 220;
    private static final float[] SEED_X = {0.18f, 0.82f, 0.50f};
    private static final float[] SEED_Y = {0.16f, 0.42f, 0.94f};
    private static final float[] SPAN = {0.20f, 0.17f, 0.26f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap[] blobs = new Bitmap[3];
    private final int[] colors = new int[3];
    private ThemePalette palette = ThemePalette.DEFAULT;
    private boolean running;
    private Runnable backdropChanged;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            phase = (SystemClock.uptimeMillis() % 34000L) / 34000f;
            invalidate();
            if (backdropChanged != null) backdropChanged.run();
            postDelayed(this, 100L);
        }
    };
    private float phase;
    private boolean reduceMotion;

    AuroraBackground(Context context) {
        super(context);
        setWillNotDraw(false);
        colors[0] = palette.auroraA;
        colors[1] = palette.auroraB;
        colors[2] = palette.auroraC;
    }

    void setPalette(ThemePalette value) {
        palette = value;
        colors[0] = value.auroraA;
        colors[1] = value.auroraB;
        colors[2] = value.auroraC;
        buildBlobs();
        invalidate();
    }

    void setReduceMotion(boolean value) {
        reduceMotion = value;
        if (value) stop(); else start();
    }

    void setBackdropChanged(Runnable value) { backdropChanged = value; }

    /** Call from onAttachedToWindow / onDetachedFromWindow through the host activity. */
    void start() {
        if (reduceMotion || running || getWidth() == 0 || !isAttachedToWindow()) return;
        running = true;
        post(tick);
    }

    void stop() {
        running = false;
        removeCallbacks(tick);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildBlobs();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void buildBlobs() {
        for (int i = 0; i < blobs.length; i++) {
            if (blobs[i] != null) blobs[i].recycle();
            int core = colors[i];
            Bitmap bitmap = Bitmap.createBitmap(BLOB_PX, BLOB_PX, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setShader(new RadialGradient(BLOB_PX / 2f, BLOB_PX / 2f, BLOB_PX / 2f,
                    new int[]{core, ThemePalette.alpha(core, 0f)}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(BLOB_PX / 2f, BLOB_PX / 2f, BLOB_PX / 2f, fill);
            blobs[i] = bitmap;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float shortest = Math.min(w, h);
        double angle = phase * 2 * Math.PI;
        for (int i = 0; i < blobs.length; i++) {
            if (blobs[i] == null || blobs[i].isRecycled()) continue;
            double drift = reduceMotion ? 0 : angle * (0.6 + i * 0.35);
            float cx = w * (SEED_X[i] + SPAN[i] * (float) Math.sin(drift + i * 1.7));
            float cy = h * (SEED_Y[i] + SPAN[i] * 0.8f * (float) Math.cos(drift * 0.8 + i));
            float radius = shortest * (i == 2 ? 1.15f : 0.95f);
            float half = radius / 2f;
            paint.setAlpha(255);
            canvas.drawBitmap(blobs[i], null,
                    new android.graphics.RectF(cx - half, cy - half, cx + half, cy + half), paint);
        }
    }
}
