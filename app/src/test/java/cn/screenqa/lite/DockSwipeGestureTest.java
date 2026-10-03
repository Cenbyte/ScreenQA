package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DockSwipeGestureTest {
    private DockSwipeGesture gesture(float x) {
        DockSwipeGesture gesture = new DockSwipeGesture(8);
        gesture.begin(x, 30, 0, 0, 300, 68);
        return gesture;
    }
    private int finish(DockSwipeGesture gesture, float x, float y, int selected) {
        return gesture.finish(x, y, 0, 0, 300, 68, 3, selected);
    }
    @Test public void horizontalDragCanSelectBothDirections() {
        DockSwipeGesture right = gesture(50); right.move(250, 32);
        assertTrue(right.dragging());assertEquals(2, finish(right, 250, 32, 0));
        DockSwipeGesture left = gesture(250); left.move(50, 32);
        assertEquals(0, finish(left, 50, 32, 2));
    }
    @Test public void tapAndSmallJitterRemainClicks() {
        DockSwipeGesture tap = gesture(50);tap.move(56, 33);
        assertFalse(tap.dragging());assertEquals(-1, finish(tap, 56, 33, 0));
    }
    @Test public void draggingWithinSelectedTabDoesNotNotifyAgain() {
        DockSwipeGesture swipe = gesture(20);swipe.move(75, 30);
        assertTrue(swipe.dragging());assertEquals(-1, finish(swipe, 75, 30, 0));
    }
    @Test public void selectionChangesDuringMovementBeforeRelease() {
        DockSwipeGesture swipe = gesture(50);swipe.move(140, 30);
        assertEquals(1, swipe.selectionAt(140, 30, 0, 0, 300, 68, 3, 0));
        assertTrue(swipe.active());
        assertEquals(-1, swipe.selectionAt(145, 30, 0, 0, 300, 68, 3, 1));
        swipe.move(220, 30);
        assertEquals(2, swipe.selectionAt(220, 30, 0, 0, 300, 68, 3, 1));
        swipe.move(140, 30);
        assertEquals(1, swipe.selectionAt(140, 30, 0, 0, 300, 68, 3, 2));
        assertEquals(-1, finish(swipe, 140, 30, 1));
        assertFalse(swipe.active());assertEquals(-1, finish(swipe, 250, 30, 1));
    }
    @Test public void verticalMotionCannotLaterTurnIntoNavigation() {
        DockSwipeGesture swipe = gesture(50);swipe.move(53, 60);swipe.move(250, 60);
        assertFalse(swipe.dragging());assertEquals(-1, finish(swipe, 250, 60, 0));
    }
    @Test public void multitouchAndSystemCancellationDoNotSelect() {
        DockSwipeGesture swipe = gesture(50);swipe.move(150, 30);swipe.abort();
        assertEquals(-1, finish(swipe, 250, 30, 0));
        swipe = gesture(50);swipe.move(150, 30);swipe.reset();
        assertEquals(-1, finish(swipe, 250, 30, 0));
    }
    @Test public void outsideAndInvalidLayoutsCannotStartOrSelect() {
        DockSwipeGesture swipe = gesture(-5);swipe.move(250, 30);
        assertFalse(swipe.active());assertEquals(-1, finish(swipe, 250, 30, 0));
        swipe = gesture(50);swipe.move(250, 30);
        assertEquals(-1, swipe.finish(250, 30, 0, 0, 0, 68, 3, 0));
        swipe = gesture(50);swipe.move(250, 30);
        assertEquals(-1, swipe.finish(250, 30, 0, 0, 300, 68, 0, 0));
    }
    @Test public void horizontalEdgesClampButLeavingVerticallyCancels() {
        DockSwipeGesture swipe = gesture(50);swipe.move(400, 30);
        assertEquals(2, finish(swipe, 400, 30, 0));
        swipe = gesture(250);swipe.move(-100, 30);
        assertEquals(0, finish(swipe, -100, 30, 2));
        swipe = gesture(50);swipe.move(250, 100);
        assertEquals(-1, finish(swipe, 250, 100, 0));
    }
    @Test public void freshGestureResetsPreviousCancellation() {
        DockSwipeGesture swipe = gesture(50);swipe.abort();
        swipe.begin(50, 30, 0, 0, 300, 68);swipe.move(250, 30);
        assertEquals(2, finish(swipe, 250, 30, 0));
    }
    @Test public void liveSelectionUsesPaddedRowBounds() {
        DockSwipeGesture swipe = new DockSwipeGesture(8);
        swipe.begin(70, 50, 20, 16, 300, 68);swipe.move(180, 50);
        assertEquals(1, swipe.selectionAt(180, 50, 24, 16, 292, 68, 3, 0));
        assertEquals(2, swipe.selectionAt(315, 50, 24, 16, 292, 68, 3, 1));
        assertEquals(-1, swipe.selectionAt(315, 110, 24, 16, 292, 68, 3, 1));
    }
    @Test public void cancellationAfterLiveSwitchAddsNoFinalSwitch() {
        DockSwipeGesture swipe = gesture(50);swipe.move(150, 30);
        assertEquals(1, swipe.selectionAt(150, 30, 0, 0, 300, 68, 3, 0));
        swipe.abort();
        assertEquals(-1, swipe.selectionAt(250, 30, 0, 0, 300, 68, 3, 1));
        assertEquals(-1, finish(swipe, 250, 30, 1));
    }
}
