package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameHealthTest {
    @Test public void listenerFreezeNeedsActualArrivalNotTheOcrThrottle(){
        FrameHealth health=new FrameHealth();
        assertFalse(health.shouldRepair(3000,1000,false));assertTrue(health.shouldRepair(4500,1000,false));
        assertFalse(health.shouldRepair(5000,4500,false));
    }
    @Test public void doesNotCloseReaderWhileOcrOrTouchOwnsIt(){
        assertFalse(new FrameHealth().shouldRepair(9000,1000,true));
    }
    @Test public void cooldownAndThreePerMinuteBoundRecovery(){
        FrameHealth health=new FrameHealth();health.repaired(5000);
        assertFalse(health.shouldRepair(6000,0,false));assertTrue(health.shouldRepair(15000,0,false));
        health.repaired(15000);health.repaired(25000);
        assertTrue(health.exhausted(35000));assertFalse(health.shouldRepair(35000,0,false));
        assertTrue(health.shouldRepair(65000,0,false));health.repaired(65000);
        assertFalse(health.exhausted(65001));assertEquals(4,health.repairs());
    }
}
