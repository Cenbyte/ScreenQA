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
    @Test public void independentNextOverlayAlsoBlocksTheEntireSwipePath(){
        TouchAction action=TouchGeometry.scroll(0,0,1000,2000,new int[][]{
                {700,1000,800,1300},{200,1000,300,1300},{450,1000,550,1300}});
        assertNull(action);
        assertEquals(500,TouchGeometry.scroll(0,0,1000,2000,new int[][]{
                {700,1000,800,1300},{200,1000,300,1300},{850,1000,950,1300}}).x);
    }
}
