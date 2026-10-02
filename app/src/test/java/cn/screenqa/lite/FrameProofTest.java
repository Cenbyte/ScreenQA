package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameProofTest {
    @Test public void noNewFrameMayUseOnlyVeryRecentPositiveProof(){
        FrameProof proof=new FrameProof();assertFalse(proof.recent(1000));
        proof.observe(1000,true);assertTrue(proof.recent(1450));assertFalse(proof.recent(1451));
    }
    @Test public void changedFrameImmediatelyRevokesPreviousProof(){
        FrameProof proof=new FrameProof();proof.observe(1000,true);proof.observe(1050,false);
        assertFalse(proof.recent(1100));proof.observe(1200,true);assertTrue(proof.recent(1300));
    }
    @Test public void drainingMatchingFramesKeepsSuWaitProofCurrent(){
        FrameProof proof=new FrameProof();for(long now=1000;now<=2500;now+=50)proof.observe(now,true);
        assertTrue(proof.recent(2550));proof.observe(2550,false);assertFalse(proof.recent(2551));
    }
}
