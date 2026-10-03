package cn.screenqa.lite;

/** Single-finger horizontal drag arbitration, independent of Android for boundary regression tests. */
final class DockSwipeGesture {
    private final float slop;
    private float downX, downY;
    private boolean active, dragging, cancelled;

    DockSwipeGesture(float slop) { this.slop = slop; }
    boolean active() { return active; }
    boolean dragging() { return dragging; }
    boolean captured() { return active && (dragging || cancelled); }
    boolean cancelled() { return cancelled; }

    void begin(float x, float y, float left, float top, float width, float height) {
        reset();
        if (width <= 0 || height <= 0 || x < left || x > left + width || y < top || y > top + height) return;
        downX = x; downY = y; active = true;
    }

    void move(float x, float y) {
        if (!active || cancelled || dragging) return;
        float horizontal = Math.abs(x - downX), vertical = Math.abs(y - downY);
        if (vertical > slop && vertical >= horizontal) { cancelled = true; return; }
        if (horizontal > slop && horizontal > vertical * 1.2f) dragging = true;
    }

    void abort() {
        // Capture the remainder so a second finger cannot turn a cancelled drag into a click.
        if (active) { cancelled = true; dragging = true; }
    }

    int selectionAt(float x, float y, float left, float top, float width, float height, int count, int selected) {
        int target = -1;
        if (active && dragging && !cancelled && count > 0 && width > 0 && height > 0
                && y >= top - slop && y <= top + height + slop) {
            int index = Math.max(0, Math.min(count - 1, (int) Math.floor((x - left) * count / width)));
            if (index != selected) target = index;
        }
        return target;
    }

    int finish(float x, float y, float left, float top, float width, float height, int count, int selected) {
        int target = selectionAt(x, y, left, top, width, height, count, selected);
        reset();
        return target;
    }

    void reset() { active = dragging = cancelled = false; }
}
