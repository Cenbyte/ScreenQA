package cn.screenqa.lite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

/**
 * Samples the page behind the glass into a small, blurred bitmap.
 * The dock is never inside the sampled view, so the capture cannot feed back on itself.
 */
final class GlassBackdrop {
    private Bitmap blurred;
    private float scale = 1f;
    private int contentWidth, contentHeight;
    private int[] pixels, output, line, lineOutput;

    Bitmap bitmap() { return blurred; }
    float scale() { return scale; }
    int contentWidth() { return contentWidth; }
    int contentHeight() { return contentHeight; }

    /** Returns true when a new frame was produced. */
    boolean capture(View source, float downscale, int blurRadius) {
        int width = source.getWidth(), height = source.getHeight();
        if (width <= 4 || height <= 4) return false;
        int w = Math.max(8, Math.round(width * downscale));
        int h = Math.max(8, Math.round(height * downscale));
        if (blurred == null || blurred.getWidth() != w || blurred.getHeight() != h) {
            if (blurred != null) blurred.recycle();
            blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        blurred.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(blurred);
        canvas.scale((float) w / width, (float) h / height);
        source.draw(canvas);
        contentWidth = width;
        contentHeight = height;
        scale = (float) w / width;
        if (pixels == null || pixels.length != w * h) {
            pixels = new int[w * h];
            output = new int[w * h];
            line = new int[Math.max(w, h)];
            lineOutput = new int[line.length];
        }
        blurred.getPixels(pixels, 0, w, 0, 0, w, h);
        boxBlur(pixels, w, h, blurRadius);
        boxBlur(pixels, w, h, blurRadius);
        blurred.setPixels(pixels, 0, w, 0, 0, w, h);
        return true;
    }

    void release() {
        if (blurred != null) { blurred.recycle(); blurred = null; }
        pixels = output = line = lineOutput = null;
    }

    /** Separable box blur; three accumulating passes are visually close to a gaussian. */
    private void boxBlur(int[] pixels, int w, int h, int radius) {
        if (radius < 1) return;
        for (int y = 0; y < h; y++) {
            int base = y * w;
            System.arraycopy(pixels, base, line, 0, w);
            GlassBlur.blurLine(line, lineOutput, w, radius);
            System.arraycopy(lineOutput, 0, output, base, w);
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) line[y] = output[y * w + x];
            GlassBlur.blurLine(line, lineOutput, h, radius);
            for (int y = 0; y < h; y++) output[y * w + x] = lineOutput[y];
        }
        System.arraycopy(output, 0, pixels, 0, pixels.length);
    }

}
