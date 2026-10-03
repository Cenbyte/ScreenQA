package cn.screenqa.lite;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.View;

/** Short interaction feedback; a missing/disabled vibrator never interrupts the action. */
final class TouchFeedback {
    enum Strength { LIGHT, NORMAL, STRONG }
    private TouchFeedback() { }

    @SuppressWarnings("deprecation")
    static void play(View source, Strength strength) {
        if (!source.isHapticFeedbackEnabled()) return;
        Context context = source.getContext();
        try {
            if (Settings.System.getInt(context.getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return;
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager manager = context.getSystemService(VibratorManager.class);
                vibrator = manager == null ? null : manager.getDefaultVibrator();
            } else vibrator = context.getSystemService(Vibrator.class);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            VibrationEffect effect;
            if (Build.VERSION.SDK_INT >= 29) {
                int preset = strength == Strength.STRONG ? VibrationEffect.EFFECT_HEAVY_CLICK
                        : strength == Strength.LIGHT ? VibrationEffect.EFFECT_TICK : VibrationEffect.EFFECT_CLICK;
                effect = VibrationEffect.createPredefined(preset);
            } else {
                // Predefined click effects arrived in API 29; keep the older fallback brief.
                long duration = strength == Strength.STRONG ? 40 : strength == Strength.LIGHT ? 12 : 25;
                int amplitude = strength == Strength.STRONG ? 180 : strength == Strength.LIGHT ? 50 : 110;
                effect = VibrationEffect.createOneShot(duration, vibrator.hasAmplitudeControl()
                        ? amplitude : VibrationEffect.DEFAULT_AMPLITUDE);
            }
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH).build());
            } else {
                vibrator.vibrate(effect, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            }
        } catch (RuntimeException ignored) {
            // Vendor vibration services may be unavailable; preserve the requested UI action.
        }
    }
}
