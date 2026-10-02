package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerRetryTest {
    @Test public void onlyOneFreshRetryOfTheSameCachedAnswer(){
        AnswerRetry retry=new AnswerRetry("choice","B","原题题干完全相同",1,2,1000);
        retry.failed(1100,false);
        assertFalse(retry.canRetry(1300,1,2,retry.key));assertTrue(retry.canRetry(1400,1,2,retry.key));
        retry.retrying();assertEquals(2,retry.attempts);assertEquals("B",retry.answer);
        retry.failed(1500,false);assertFalse(retry.canRetry(1800,1,2,retry.key));
    }
    @Test public void gestureCancellationOrRootUncertaintyNeverRetries(){
        AnswerRetry retry=new AnswerRetry("choice","A","完全相同的题干",1,2,1000);
        retry.failed(1100,true);assertFalse(retry.canRetry(1500,1,2,retry.key));
    }
    @Test public void otherQuestionPausedSessionOrChangedPriorityInvalidatesRetry(){
        AnswerRetry retry=new AnswerRetry("choice","A","完全相同的题干",1,2,1000);
        retry.failed(1100,false);
        assertFalse(retry.canRetry(1500,1,2,"不同的题干"));assertFalse(retry.canRetry(1500,2,2,retry.key));
        assertFalse(retry.canRetry(1500,1,3,retry.key));assertFalse(retry.canRetry(7000,1,2,retry.key));
    }
}
