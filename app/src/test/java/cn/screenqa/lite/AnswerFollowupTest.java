package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerFollowupTest {
    @Test public void manualAnswerResumesAt400msAndRemainsReadableUntilNewStem(){
        AnswerFollowup flow=new AnswerFollowup();
        flow.begin("太阳是否属于恒星？",false,1000);
        assertEquals(1,flow.remaining(1399));assertEquals(0,flow.remaining(1400));
        assertTrue(flow.retaining());
        assertEquals(0,flow.remaining(100000));
        assertFalse(flow.located(""));assertFalse(flow.located("太阳 是否属于恒星？"));
        assertTrue(flow.retaining());assertTrue(flow.located("月球是否属于恒星？"));
        assertFalse(flow.retaining());
    }
    @Test public void automaticExecutionResumesAt200msWithoutReadingTimeout(){
        AnswerFollowup flow=new AnswerFollowup();flow.begin("题干原文",true,5000);
        assertEquals(1,flow.remaining(5199));assertEquals(0,flow.remaining(5200));
        assertTrue(flow.retaining());assertTrue(flow.same("题干原文"));
    }
    @Test public void stopAndReplacementCancelThePreviousDeadline(){
        AnswerFollowup flow=new AnswerFollowup();flow.begin("旧题干",false,1000);flow.reset();
        assertEquals(0,flow.remaining(1100));assertFalse(flow.retaining());
        flow.begin("新题干",true,1200);assertEquals(200,flow.remaining(1200));
        assertFalse(flow.same("旧题干"));assertTrue(flow.same("新题干"));
    }
}
