package cn.screenqa.lite;

import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Non-linear motion language: damped springs plus emphasized easing, never linear. */
final class Motion {
    private Motion() {}

    /** Critically damped spring with a small settle overshoot; s(0)=0, s(1)~1. */
    static final TimeInterpolator SPRING = t -> (float) (1 - Math.exp(-6.2 * t) * Math.cos(9.4 * t));
    /** Faster spring for direct-manipulation feedback. */
    static final TimeInterpolator SPRING_OUT = t -> (float) (1 - Math.exp(-8.0 * t) * Math.cos(11.6 * t));
    /** Slow, calm spring for large surfaces. */
    static final TimeInterpolator SPRING_SOFT = t -> (float) (1 - Math.exp(-5.0 * t) * Math.cos(7.2 * t));
    static final TimeInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    static final TimeInterpolator DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    static final TimeInterpolator ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    static final TimeInterpolator OVERSHOOT = new PathInterpolator(0.18f, 0.89f, 0.32f, 1.22f);
    static final TimeInterpolator ANTICIPATE = new PathInterpolator(0.5f, -0.35f, 0.2f, 1f);

    static final long QUICK = 190, BASE = 300, SLOW = 470;

    /** Press feedback: fast contraction, springy release. */
    static void press(View target, boolean pressed, boolean reduce) {
        if (target == null) return;
        float to = pressed && !reduce ? 0.94f : 1f;
        target.animate().cancel();
        target.animate().scaleX(to).scaleY(to)
                .setDuration(pressed ? QUICK : SLOW)
                .setInterpolator(pressed ? DECELERATE : SPRING)
                .start();
    }

    /** Staggered entrance used when a page becomes visible. */
    static void entrance(View target, int index, int distance, boolean reduce) {
        if (target == null) return;
        target.animate().cancel();
        if (reduce) {
            target.setAlpha(1f); target.setTranslationY(0f); target.setScaleX(1f); target.setScaleY(1f);
            return;
        }
        target.setAlpha(0f);
        target.setTranslationY(distance);
        target.setScaleX(0.985f);
        target.setScaleY(0.985f);
        target.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(Math.min(index, 9) * 26L)
                .setDuration(SLOW)
                .setInterpolator(EMPHASIZED)
                .start();
    }

    static void fadeIn(View target, long duration, boolean reduce) {
        if (target == null) return;
        target.animate().cancel();
        if (reduce) { target.setAlpha(1f); return; }
        target.setAlpha(0f);
        target.animate().alpha(1f).setDuration(duration).setInterpolator(DECELERATE).start();
    }
}
