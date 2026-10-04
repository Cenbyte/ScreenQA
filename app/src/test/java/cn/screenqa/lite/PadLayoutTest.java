package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class PadLayoutTest {
    @Test public void onlyWidthGreaterThanHeightEnablesPad(){
        assertFalse(PadLayout.usesPadLayout(1080,2400));assertFalse(PadLayout.usesPadLayout(1000,1000));
        assertTrue(PadLayout.usesPadLayout(2560,1600));assertFalse(PadLayout.usesPadLayout(1000,0));
    }
    @Test public void sidebarUsesVerticalGestureWithoutReactingToHorizontalMotion(){
        DockSwipeGesture g=new DockSwipeGesture(8);
        // The sidebar transposes x/y to reuse the dock arbitration.
        g.begin(20,30,0,0,240,72);g.move(180,30);
        assertEquals(2,g.selectionAt(180,30,0,0,240,72,3,0));
        g.abort();assertEquals(-1,g.selectionAt(180,30,0,0,240,72,3,0));
        g.begin(20,30,0,0,240,72);g.move(20,60);
        assertFalse(g.dragging());assertTrue(g.cancelled());
    }
}
