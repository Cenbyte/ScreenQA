package cn.screenqa.lite;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.util.function.Consumer;

/** Owns an overlay Window so public background-blur APIs can blur other apps, not our content. */
final class FrostedOverlayWindow {
    private final Dialog dialog;
    private final Window window;
    private final WindowManager manager;
    private final FrostedGlassDrawable material;
    private final int blurRadius;
    private final Consumer<Boolean> blurListener = this::setBlurEnabled;
    private boolean listening, closed;

    FrostedOverlayWindow(Context context, View content, WindowManager.LayoutParams params,
                         FrostedGlassDrawable material, int blurRadius) {
        this.material = material; this.blurRadius = Math.min(100, blurRadius);
        manager = context.getSystemService(WindowManager.class);
        dialog = new Dialog(context, R.style.FrostedOverlayTheme);
        dialog.setCancelable(false);
        window = dialog.getWindow();
        if (window == null) throw new IllegalStateException("Missing overlay window");
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        window.setBackgroundDrawable(material);
        params.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        params.flags &= ~(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        params.dimAmount = 0; params.windowAnimations = 0;
        window.setAttributes(params);
        dialog.setContentView(content);
        window.getDecorView().setPadding(0, 0, 0, 0);
        try {
            dialog.show();
            window.setAttributes(params);
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    manager.addCrossWindowBlurEnabledListener(context.getMainExecutor(), blurListener);
                    listening = true;
                    setBlurEnabled(manager.isCrossWindowBlurEnabled());
                } catch (RuntimeException unsupported) {
                    setBlurEnabled(false);
                }
            } else setBlurEnabled(false);
        } catch (RuntimeException failure) {
            dismiss(); throw failure;
        }
    }

    private void setBlurEnabled(boolean enabled) {
        if (closed) return;
        if (Build.VERSION.SDK_INT >= 31) {
            try { window.setBackgroundBlurRadius(enabled ? blurRadius : 0); }
            catch (RuntimeException ignored) { enabled = false; }
        } else enabled = false;
        material.setBlurred(enabled);
    }

    void update(WindowManager.LayoutParams params) { if (!closed) window.setAttributes(params); }
    void setVisible(boolean visible) {
        if (closed) return;
        if (visible) dialog.show(); else dialog.hide();
    }
    View animationView() { return window.getDecorView(); }
    void setPalette(ThemePalette palette) { material.setPalette(palette); }
    void dismiss() {
        if (closed) return;
        closed = true;
        window.getDecorView().animate().cancel();
        if (Build.VERSION.SDK_INT >= 31 && listening) {
            try { manager.removeCrossWindowBlurEnabledListener(blurListener); }
            catch (RuntimeException ignored) { }
            listening = false;
        }
        dialog.dismiss();
    }
}
