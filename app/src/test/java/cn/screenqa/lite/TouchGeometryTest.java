package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchGeometryTest {
    @Test public void swipeScalesWithLandscapeAndPortraitViewports(){
        TouchAction portrait=TouchGeometry.scroll(0,40,1080,2300,new int[0][]);
        TouchAction landscape=TouchGeometry.scroll(30,0,2300,1080,new int[0][]);
        assertEquals(810,portrait.x);assertEquals(1848,portrait.y);assertEquals(944,portrait.endY);
        assertEquals(1732,landscape.x);assertEquals(864,landscape.y);assertEquals(432,landscape.endY);
    }
    @Test public void detectsAnOverlayBetweenOtherwiseClearSwipeEndpoints(){
        TouchAction action=TouchGeometry.scroll(0,0,1000,2000,new int[][]{{700,1100,800,1200}});
        assertNotNull(action);assertEquals(250,action.x);
    }
    @Test public void allCoveredPathsRequireManualScrolling(){
        assertNull(TouchGeometry.scroll(0,0,1000,2000,new int[][]{{0,900,1000,1500}}));
    }
    @Test public void tinyViewportNeverProducesHardcodedCoordinates(){
        assertNull(TouchGeometry.scroll(0,0,30,70,new int[0][]));
    }
}
