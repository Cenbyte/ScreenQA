package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerPresentationTest {
    @Test public void summaryBoundaryIs20UnicodeCharactersAndDoesNotSplitEmoji(){
        String exact="😀".repeat(20);
        assertEquals(exact,AnswerPresentation.questionSummary(exact,"改写"));
        String clipped=AnswerPresentation.questionSummary(exact+"？","😀".repeat(21));
        assertEquals("😀".repeat(19)+"…",clipped);
        assertEquals("保留否定条件",AnswerPresentation.questionSummary("长".repeat(21),"保留否定条件"));
    }
    @Test public void readingTimeGivesTextAnswersTimeToCopyAndCapsLongAnswers(){
        assertEquals(10000L,AnswerPresentation.visibleMillis("A",false));
        assertEquals(20000L,AnswerPresentation.visibleMillis("氢、氧",true));
        assertEquals(46000L,AnswerPresentation.visibleMillis("答".repeat(200),true));
        assertEquals(90000L,AnswerPresentation.visibleMillis("答".repeat(1000),true));
    }
    @Test public void unicodeCharactersDoNotDoubleReadingTimeOrWidth(){
        assertEquals(AnswerPresentation.visibleMillis("答".repeat(100),true),
                AnswerPresentation.visibleMillis("😀".repeat(100),true));
        assertEquals(184,AnswerPresentation.bubbleWidthDp("A"));
        assertEquals(320,AnswerPresentation.bubbleWidthDp("答".repeat(1000)));
    }
    @Test public void blessingsAreLocalAndDoNotRepeatOnConsecutiveOpens(){
        String previous=SponsorBlessings.next();
        for(int i=0;i<100;i++){
            String next=SponsorBlessings.next();assertFalse(next.isEmpty());
            assertNotEquals(previous,next);previous=next;
        }
    }
}
