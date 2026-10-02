package cn.screenqa.lite;

/** Sliding box filter. Input stays immutable so outgoing samples are never filtered twice. */
final class GlassBlur {
    private GlassBlur() { }

    static void blurLine(int[] input, int[] output, int length, int radius) {
        int span = radius * 2 + 1;
        int a = 0, r = 0, g = 0, b = 0;
        for (int i = -radius; i <= radius; i++) {
            int color = input[clamp(i, length)];
            a += color >>> 24; r += (color >> 16) & 255;
            g += (color >> 8) & 255; b += color & 255;
        }
        for (int i = 0; i < length; i++) {
            output[i] = ((a / span) << 24) | ((r / span) << 16) | ((g / span) << 8) | (b / span);
            int drop = input[clamp(i - radius, length)];
            int add = input[clamp(i + radius + 1, length)];
            a += (add >>> 24) - (drop >>> 24);
            r += ((add >> 16) & 255) - ((drop >> 16) & 255);
            g += ((add >> 8) & 255) - ((drop >> 8) & 255);
            b += (add & 255) - (drop & 255);
        }
    }

    private static int clamp(int index, int length) {
        return index < 0 ? 0 : Math.min(index, length - 1);
    }
}
