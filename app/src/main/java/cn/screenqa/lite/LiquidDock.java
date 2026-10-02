package cn.screenqa.lite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import com.example.liquidglass.GlassAccessibilityMode;
import com.example.liquidglass.GlassMaterial;
import com.example.liquidglass.LiquidGlassTabBar;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Floating liquid glass dock backed by QWEA0/Liquid-Glass-Android
 * ({@code com.github.QWEA0:liquidglass:v2.0.11}).
 *
 * <p>The library samples the page layer on scroll events, so the capsule refracts the content
 * passing underneath instead of replaying a pre-blurred bitmap. Values that the library exposes in
 * pixels are converted from dp here; {@code adaptiveLensScale} clamps them to the capsule's short
 * side, so a 68dp panel is never all edge band.
 */
final class LiquidDock extends FrameLayout implements Dock {
    /** Lens band / displacement, in dp — converted to px because the library works in pixels. */
    private static final float BEVEL_DP = 10f;
    private static final float REFRACTION_DP = 14f;
    /** Blur radius as a fraction of the bar size: enough to frost the content, still legible. */
    private static final float BLUR_FRACTION = 0.16f;
    /** Subtle theme tint; the material supplies the remaining contrast. */
    private static final float FROST_STRENGTH_DARK = 0.06f;
    private static final float FROST_STRENGTH_LIGHT = 0.08f;
    private static final float PRESS_SCALE = 0.95f;
    private static final float ELASTICITY = 0.15f;

    private final LiquidGlassTabBar bar;
    private Dock.Listener listener;
    private ThemePalette palette = ThemePalette.DEFAULT;
    private boolean syncing, glass = true, lifted, reduceMotion;
    private int count, selected;

    /** The native blur pipeline ships arm64-v8a + armeabi-v7a only; everything else falls back. */
    static boolean isSupported() {
        if (Build.VERSION.SDK_INT < 24) return false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi) || "armeabi".equals(abi)) return true;
        }
        return false;
    }

    LiquidDock(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        bar = new LiquidGlassTabBar(context);
        bar.setMaterial(GlassMaterial.REGULAR);
        bar.setCornerRadius(Ui.dp(context, Dock.PANEL_HEIGHT_DP / 2f));
        bar.setBevelWidth(Ui.dp(context, BEVEL_DP));
        bar.setRefractionHeight(Ui.dp(context, REFRACTION_DP));
        bar.setRefractionFalloff(1.6f);
        bar.setDispersionStrength(0.12f);
        bar.setBlurAmount(BLUR_FRACTION);
        bar.setSaturation(1.15f);
        bar.setEnableEdgeHighlight(true);
        bar.setEnableSensorHighlight(false);
        bar.setEnableDynamicBackground(false);
        bar.setEnablePressEffect(true);
        bar.setPressScale(PRESS_SCALE);
        bar.setElasticity(ELASTICITY);
        bar.setAccessibilityMode(GlassAccessibilityMode.AUTO);
        bar.setOnTabSelected(new Function1<Integer, Unit>() {
            @Override public Unit invoke(Integer index) {
                onTabPicked(index == null ? -1 : index);
                return Unit.INSTANCE;
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, Dock.PANEL_HEIGHT_DP));
        params.gravity = Gravity.CENTER_VERTICAL;
        int inset = Dock.sideInset(context);
        params.leftMargin = inset;
        params.rightMargin = inset;
        addView(bar, params);
        applyTint();
    }

    // The project never links appcompat, so the framework loader is the intended one here.
    @SuppressLint("UseCompatLoadingForDrawables")
    @Override public void setItems(String[] titles, int[] iconRes) {
        Context context = getContext();
        count = Math.min(titles.length, iconRes.length);
        List<LiquidGlassTabBar.TabItem> tabs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Drawable icon = context.getDrawable(iconRes[i]);
            tabs.add(new LiquidGlassTabBar.TabItem(titles[i], icon));
        }
        // setTabs resets the selection to 0 and selectedIndex fires the callback: keep both silent.
        boolean previous = syncing;
        syncing = true;
        bar.setTabs(tabs);
        applyTint();
        if (selected > 0 && selected < count) bar.setSelectedIndex(selected);
        syncing = previous;
        QaLog.event("dock library=QWEA0/liquidglass v2.0.11 tabs=" + count
                + " api=" + bar.getEffectiveApiLevel()
                + " abi=" + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?"));
    }

    @Override public void setSelected(int index, boolean animate) {
        if (index < 0 || index >= count) return;
        boolean changed = index != selected;
        selected = index;
        if (!changed) return;
        syncing = true;
        bar.setSelectedIndex(index);
        syncing = false;
    }

    @Override public void setPalette(ThemePalette value) {
        palette = value == null ? ThemePalette.DEFAULT : value;
        applyTint();
    }

    @Override public void setGlassEnabled(boolean enabled) {
        glass = enabled;
        bar.setAccessibilityMode(enabled ? GlassAccessibilityMode.AUTO : GlassAccessibilityMode.FORCE_OPAQUE);
        bar.setEnableDynamicBackground(false);
        bar.invalidate();
    }

    @Override public void setReduceMotion(boolean reduce) {
        reduceMotion = reduce;
        bar.setEnablePressEffect(!reduce);
        bar.setElasticity(reduce ? 0f : ELASTICITY);
        bar.setEnableSensorHighlight(false);
    }

    @Override public void setLifted(boolean value) {
        if (lifted == value) return;
        lifted = value;
        animate().cancel();
        if (reduceMotion) {
            setTranslationY(value ? Ui.dp(this, 12f) : 0f);
            setScaleX(value ? 0.96f : 1f);
            setScaleY(value ? 0.96f : 1f);
            refreshBackdrop();
            return;
        }
        animate().translationY(value ? Ui.dp(this, 12f) : 0f)
                .scaleX(value ? 0.96f : 1f).scaleY(value ? 0.96f : 1f)
                .setDuration(Motion.BASE).setInterpolator(Motion.SPRING)
                .setUpdateListener(animation -> refreshBackdrop()).start();
    }

    /** Keep the library's GPU capture path; do not force its CPU custom-bitmap pipeline. */
    @Override public void setBackdrop(Bitmap bitmap, float scale, float originX, float originY) { }

    @Override public void setBackdropSource(View source) {
        if (source == null) return;
        bar.setBackdropSource(source);
        // Scroll listeners refresh the sample; avoid the library's unconditional redraw loop.
        bar.setEnableDynamicBackground(false);
    }

    @Override public void setListener(Listener value) { listener = value; }

    /** The library owns sampling; the host need not create dock bitmaps. */
    @Override public boolean liveSampling() { return true; }

    /** Scroll events already invalidate the library; only slow backdrop animation needs a nudge. */
    void refreshBackdrop() { if (glass && isShown()) bar.invalidate(); }

    private void onTabPicked(int index) {
        if (index < 0 || index == selected) return;
        selected = index;
        if (syncing || listener == null) return;
        listener.onSelected(index);
    }

    private void applyTint() {
        if (palette == null) return;
        // CLEAR on both palettes avoids REGULAR's heavier frost on the light theme.
        bar.setMaterial(GlassMaterial.CLEAR);
        // Surface-based hue avoids adding a milky white veil on dark backdrops.
        int frost = ThemePalette.blend(palette.surface, palette.accent, 0.20f);
        bar.setGlassTint(frost, palette.dark ? FROST_STRENGTH_DARK : FROST_STRENGTH_LIGHT);
        // The selected tab follows the theme; other labels retain automatic luminance contrast.
        bar.setSelectedTintColor(Integer.valueOf(palette.accent));
    }
}
