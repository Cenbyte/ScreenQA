package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerPresentationTest {
    @Test public void readingTimeGivesTextAnswersTimeToCopyAndCapsLongAnswers(){
        assertEquals(10000L,AnswerPresentation.visibleMillis("A",false));
        assertEquals(20000L,AnswerPresentation.visibleMillis("氢、氧",true));
        assertEquals(46000L,AnswerPresentation.visibleMillis("答".repeat(200),true));
        assertEquals(90000L,AnswerPresentation.visibleMillis("答".repeat(1000),true));
    }
    @Test public void unicodeCharactersDoNotDoubleReadingTimeOrWidth(){
        assertEquals(AnswerPresentation.visibleMillis("答".repeat(100),true),
                AnswerPresentation.visibleMillis("😀".repeat(100),true));
        assertEquals(144,AnswerPresentation.bubbleWidthDp("A"));
        assertEquals(272,AnswerPresentation.bubbleWidthDp("答".repeat(1000)));
    }
    @Test public void blessingsAreLocalAndDoNotRepeatOnConsecutiveOpens(){
        String previous=SponsorBlessings.next();
        for(int i=0;i<100;i++){
            String next=SponsorBlessings.next();assertFalse(next.isEmpty());
            assertNotEquals(previous,next);previous=next;
        }
    }
}
