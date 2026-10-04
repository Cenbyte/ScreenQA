package cn.screenqa.lite;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Telegram-inspired ease-out reveal, implemented using framework animation primitives. */
final class ChatBubbleMotion {
    private static final PathInterpolator REVEAL = new PathInterpolator(.23f, 1f, .32f, 1f);
    private static final PathInterpolator DISMISS = new PathInterpolator(.4f, 0f, 1f, 1f);
    private ChatBubbleMotion() { }
    static boolean enabled(boolean reduce) { return !reduce && ValueAnimator.areAnimatorsEnabled(); }
    static void show(View view, boolean left, float width, float tailY, float distance, boolean reduce) {
        view.animate().cancel();
        view.setPivotX(left ? 0 : width); view.setPivotY(tailY);
        view.setScaleX(1f); view.setScaleY(1f); view.setAlpha(1f); view.setTranslationX(0f);
        if (!enabled(reduce)) return;
        view.setScaleX(.84f); view.setScaleY(.9f); view.setAlpha(0f);
        view.setTranslationX(left ? -distance : distance);
        view.animate().scaleX(1f).scaleY(1f).translationX(0f).alpha(1f)
                .setDuration(260).setInterpolator(REVEAL).start();
    }
    static void hide(View view, boolean left, float distance, Runnable finished) {
        view.animate().cancel();
        view.animate().scaleX(.9f).scaleY(.94f).translationX(left ? -distance : distance).alpha(0f)
                .setDuration(150).setInterpolator(DISMISS).withEndAction(finished).start();
    }
}
