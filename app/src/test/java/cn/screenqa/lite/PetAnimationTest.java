package cn.screenqa.lite;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetAnimationTest {
    @Test public void tapBeginsImmediatelyAndCompletesExactlyOnce(){
        PetAnimation p=new PetAnimation();p.idle(100);p.tap(900);
        assertTrue(p.tapping(900));assertEquals(0,p.frame(900));
        assertEquals(15,p.frame(900+1439));assertTrue(p.tapping(900+1439));
        assertFalse(p.tapping(900+1440));assertEquals(0,p.frame(900+1440));
        assertFalse(p.tapping(900+1440*2));
    }
    @Test public void repeatedTapDoesNotCutAnInteractionShort(){
        PetAnimation p=new PetAnimation();p.idle(0);p.tap(20);p.tap(400);
        assertEquals(4,p.frame(400));assertFalse(p.tapping(1460));
        p.tap(1500);assertTrue(p.tapping(1500));assertEquals(0,p.frame(1500));
    }
    @Test public void idleLoopsWithoutBusyFrameUpdatesAndReattachResets(){
        PetAnimation p=new PetAnimation();p.idle(0);
        assertEquals(650,p.delay(0));assertEquals(0,p.frame(649));assertEquals(1,p.frame(650));
        long period=650+110+110+420+550+110+380+150+110+450+160+500;
        assertEquals(0,p.frame(period));assertEquals(1,p.frame(period+650));
        p.tap(period);p.idle(period+200);assertFalse(p.tapping(period+200));assertEquals(0,p.frame(period+200));
    }
}
