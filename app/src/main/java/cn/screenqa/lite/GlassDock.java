package cn.screenqa.lite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Fallback dock for devices where the liquid glass native pipeline is unavailable: the hand-drawn
 * glass panel with the sliding pill, spring press feedback and the scroll lift. {@link LiquidDock}
 * takes over wherever {@link LiquidDock#isSupported()} is true.
 */
final class GlassDock extends FrameLayout implements Dock {
    private static final int ITEM_WIDTH_DP = 86;
    private static final int PILL_MARGIN_DP = 7;

    private final LiquidGlassView glass;
    private final LinearLayout items;
    private final View pill;
    private final List<ImageView> icons = new ArrayList<>();
    private final List<TextView> labels = new ArrayList<>();
    private ThemePalette palette = ThemePalette.DEFAULT;
    private ValueAnimator pillAnimator, liquidDriver;
    private Dock.Listener listener;
    private boolean reduceMotion, lifted;
    private int itemWidth, count, selected = -1;
    private final DockSwipeGesture swipe;

    GlassDock(Context context) {
        super(context);
        swipe = new DockSwipeGesture(ViewConfiguration.get(context).getScaledTouchSlop());
        setWillNotDraw(false);
        setClipChildren(false);
        glass = new LiquidGlassView(context);
        glass.setCornerRadius(Ui.dp(context, Dock.PANEL_HEIGHT_DP / 2f));
        glass.setShadowRoom(Ui.dp(context, Dock.ROOM_DP));
        LayoutParams glassParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, Dock.PANEL_HEIGHT_DP));
        glassParams.gravity = Gravity.CENTER_VERTICAL;
        int inset = Dock.sideInset(context);
        glassParams.leftMargin = inset;
        glassParams.rightMargin = inset;
        addView(glass, glassParams);

        pill = new View(context);
        addView(pill, new LayoutParams(0, 0));

        items = new LinearLayout(context);
        items.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams rowParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, Ui.dp(context, Dock.PANEL_HEIGHT_DP));
        rowParams.gravity = Gravity.CENTER;
        addView(items, rowParams);
        // The row is centred after layout, so the pill anchors to it once its left edge is known.
        items.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (selected >= 0) movePill(selected, false);
        });
    }

    @Override public void setListener(Dock.Listener value) { listener = value; }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swipe.begin(event.getX(), event.getY(), glass.getLeft(), glass.getTop(), glass.getWidth(), glass.getHeight());
                break;
            case MotionEvent.ACTION_MOVE:
                swipe.move(event.getX(), event.getY());
                selectSwipeAt(event);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                swipe.abort();
                break;
            case MotionEvent.ACTION_CANCEL:
                swipe.reset();
                break;
        }
        return swipe.captured() || super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!swipe.active()) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                swipe.move(event.getX(), event.getY());
                selectSwipeAt(event);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                swipe.abort();
                break;
            case MotionEvent.ACTION_UP:
                boolean dragged = swipe.captured();
                int index = swipe.finish(event.getX(), event.getY(), items.getLeft(), glass.getTop(),
                        items.getWidth(), glass.getHeight(), count, selected);
                if (index >= 0) {
                    setSelected(index, true);
                    if (listener != null) listener.onSelected(index);
                } else if (!dragged) performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                swipe.reset();
                break;
        }
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }

    private void selectSwipeAt(MotionEvent event) {
        int index = swipe.selectionAt(event.getX(), event.getY(), items.getLeft(), glass.getTop(),
                items.getWidth(), glass.getHeight(), count, selected);
        if (index >= 0) {
            setSelected(index, true);
            if (listener != null) listener.onSelected(index);
        }
    }

    @Override public void setReduceMotion(boolean value) {
        reduceMotion = value;
        if (value) stopLiquid(); else startLiquid();
    }

    @Override public void setItems(String[] titles, int[] iconRes) {
        Context context = getContext();
        items.removeAllViews();
        icons.clear();
        labels.clear();
        count = Math.min(titles.length, iconRes.length);
        itemWidth = Ui.dp(context, ITEM_WIDTH_DP);
        for (int i = 0; i < count; i++) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes[i]);
            item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(context, 24f), Ui.dp(context, 24f)));
            TextView label = new TextView(context);
            label.setText(titles[i]);
            label.setTextSize(11f);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = Ui.dp(context, 3f);
            item.addView(label, labelParams);
            final int index = i;
            item.setOnClickListener(view -> {
                if (index == selected) return;
                setSelected(index, true);
                if (listener != null) listener.onSelected(index);
            });
            item.setOnTouchListener((view, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) Motion.press(view, true, reduceMotion);
                else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    Motion.press(view, false, reduceMotion);
                return false;
            });
            items.addView(item, new LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.MATCH_PARENT));
            icons.add(icon);
            labels.add(label);
        }
        int margin = Ui.dp(context, PILL_MARGIN_DP);
        LayoutParams pillParams = new LayoutParams(
                itemWidth - margin * 2, Ui.dp(context, Dock.PANEL_HEIGHT_DP - PILL_MARGIN_DP * 2));
        pillParams.gravity = Gravity.CENTER_VERTICAL;
        pillParams.leftMargin = margin;
        pill.setLayoutParams(pillParams);
        applyPalette();
        applyItemColors();
        if (selected >= 0) movePill(selected, false);
    }

    @Override public void setSelected(int index, boolean animate) {
        if (index < 0 || index >= count || index == selected) return;
        selected = index;
        applyItemColors();
        movePill(index, animate);
    }

    int selectedIndex() { return selected; }

    @Override public void setPalette(ThemePalette value) {
        palette = value == null ? ThemePalette.DEFAULT : value;
        applyPalette();
    }

    @Override public void setGlassEnabled(boolean value) { glass.setGlassEnabled(value); }

    @Override public void setBackdrop(Bitmap bitmap, float scale, float originX, float originY) {
        glass.setBackdrop(bitmap, scale, originX + glass.getLeft(), originY + glass.getTop());
    }

    @Override public void setBackdropSource(View source) { }

    /** The hand-drawn panel replays the bitmap the host captured, so the host must keep feeding it. */
    @Override public boolean liveSampling() { return false; }

    /** Slides the whole bar down and shrinks it while the page scrolls, like a physical object. */
    @Override public void setLifted(boolean value) {
        if (lifted == value) return;
        lifted = value;
        animate().cancel();
        animate().translationY(value ? Ui.dp(this, 12f) : 0f)
                .scaleX(value ? 0.96f : 1f).scaleY(value ? 0.96f : 1f)
                .setDuration(Motion.BASE).setInterpolator(Motion.SPRING).start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLiquid();
    }

    @Override
    protected void onDetachedFromWindow() {
        swipe.reset();
        stopLiquid();
        super.onDetachedFromWindow();
    }

    private void startLiquid() {
        if (reduceMotion || liquidDriver != null) return;
        liquidDriver = ValueAnimator.ofFloat(0f, 1f);
        liquidDriver.setDuration(11000L);
        liquidDriver.setRepeatCount(ValueAnimator.INFINITE);
        liquidDriver.addUpdateListener(animation -> glass.setLiquidPhase((float) animation.getAnimatedValue()));
        liquidDriver.start();
    }

    private void stopLiquid() {
        if (liquidDriver != null) { liquidDriver.cancel(); liquidDriver = null; }
    }

    private void movePill(int index, boolean animate) {
        if (itemWidth == 0) return;
        float base = items.getLeft() + Ui.dp(this, PILL_MARGIN_DP);
        float target = base + index * (float) itemWidth;
        if (pillAnimator != null) { pillAnimator.cancel(); pillAnimator = null; }
        if (!animate || reduceMotion) { pill.setTranslationX(target); return; }
        pillAnimator = ValueAnimator.ofFloat(pill.getTranslationX(), target);
        pillAnimator.setDuration(Motion.BASE + 90L);
        pillAnimator.setInterpolator(Motion.SPRING);
        pillAnimator.addUpdateListener(animation -> pill.setTranslationX((float) animation.getAnimatedValue()));
        pillAnimator.start();
    }

    private void applyPalette() {
        glass.setPalette(palette);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(ThemePalette.alpha(palette.accent, 0.16f));
        background.setStroke(Math.max(1, Ui.dp(this, 1f)), ThemePalette.alpha(palette.accent, 0.45f));
        background.setCornerRadius(Ui.dp(this, (Dock.PANEL_HEIGHT_DP - PILL_MARGIN_DP * 2) / 2f));
        pill.setBackground(background);
        applyItemColors();
    }

    private void applyItemColors() {
        for (int i = 0; i < icons.size(); i++) {
            boolean active = i == selected;
            icons.get(i).setColorFilter(active ? palette.accent : palette.secondary);
            labels.get(i).setTextColor(active ? palette.accent : palette.secondary);
            labels.get(i).setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }
}
