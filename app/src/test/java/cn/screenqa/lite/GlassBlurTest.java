package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public final class GlassBlurTest {
    @Test public void impulseIsSymmetricAndDoesNotFeedBack() {
        int[] input = {0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFF000000, 0xFF000000};
        int[] original = input.clone(), output = new int[5];
        GlassBlur.blurLine(input, output, 5, 1);
        assertArrayEquals(new int[]{0xFF000000, 0xFF555555, 0xFF555555, 0xFF555555, 0xFF000000}, output);
        assertArrayEquals(original, input);
    }

    @Test public void matchesReferenceIncludingAlphaAndOversizedRadius() {
        int[] input = {0x2040A010, 0xFF1020F0, 0x00905060, 0xAAFFFFFF};
        for (int radius : new int[]{0, 1, 4, 22}) {
            int[] actual = new int[input.length], expected = new int[input.length];
            GlassBlur.blurLine(input, actual, input.length, radius);
            for (int i = 0; i < input.length; i++) {
                for (int shift : new int[]{0, 8, 16, 24}) {
                    int sum = 0;
                    for (int j = i - radius; j <= i + radius; j++)
                        sum += (input[Math.max(0, Math.min(j, input.length - 1))] >>> shift) & 255;
                    expected[i] |= (sum / (radius * 2 + 1)) << shift;
                }
            }
            assertArrayEquals(expected, actual);
        }
    }
}
