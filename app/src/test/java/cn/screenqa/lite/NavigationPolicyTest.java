package cn.screenqa.lite;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NavigationPolicyTest {
    private final String stem="单选题哪一个是恒星？";
    @Test public void reproducesMissingNodeTextWithoutFalsePageChange(){
        assertEquals(NavigationPolicy.Evidence.UNKNOWN,NavigationPolicy.evidence(stem,"下一题", ""));
        assertEquals(NavigationPolicy.Evidence.UNKNOWN,NavigationPolicy.evidence(stem,"", ""));
    }
    @Test public void onlyPositiveDifferentQuestionConfirmsChange(){
        assertEquals(NavigationPolicy.Evidence.DIFFERENT,NavigationPolicy.evidence(stem,"判断题地球是恒星。", "判断题地球是恒星。"));
        assertEquals(NavigationPolicy.Evidence.SAME,NavigationPolicy.evidence(stem,"单选题\n哪一个是恒星？\nA地球B太阳", ""));
        assertEquals(NavigationPolicy.Evidence.UNKNOWN,NavigationPolicy.evidence(stem,"哪一个是恒星", "哪一个是恒星"));
    }
    @Test public void multiLineOcrUsesTextRatherThanNumberedFingerprint(){
        ScreenDocument d=new ScreenDocument(Arrays.asList(
                new ScreenDocument.Line("单选题",10,10,150,40),
                new ScreenDocument.Line("哪一个是恒星？",10,50,300,80),
                new ScreenDocument.Line("下一题 →",10,600,200,660)),500,800);
        assertEquals(NavigationPolicy.Evidence.SAME,NavigationPolicy.evidence(stem,d.text(AnswerTargetResolver.everyLine(d)),""));
        assertEquals(2,d.lines.size());assertEquals(1,d.navigationLines.size());
        assertNotNull(NavigationPolicy.uniqueNext(d));
    }
    @Test public void navigationNeverSelectsSubmitOrDuplicateButtons(){
        ScreenDocument d=new ScreenDocument(Arrays.asList(
                new ScreenDocument.Line("下一题",10,600,200,640),new ScreenDocument.Line("下一题",250,600,450,640),
                new ScreenDocument.Line("提交答案",10,680,200,720)),500,800);
        assertNull(NavigationPolicy.uniqueNext(d));assertEquals(2,d.navigationLines.size());
    }
    @Test public void quizLabLastQuestionStopsBeforeResultsWithoutScrolling(){
        ScreenDocument d=new ScreenDocument(Arrays.asList(
                new ScreenDocument.Line("下列哪项最适合存放 PWA 的名称与启动模式？",100,900,980,1130),
                new ScreenDocument.Line("查看成绩 →",553,2094,1031,2225)),1080,2400);
        assertTrue(NavigationPolicy.terminalVisible(d));assertNull(NavigationPolicy.uniqueNext(d));
    }
    @Test public void scanStormCannotStarveOcrIndefinitely(){
        assertTrue(NavigationPolicy.waitForScan(100,0));
        assertFalse(NavigationPolicy.waitForScan(250,0));assertFalse(NavigationPolicy.waitForScan(10000,0));
    }
    @Test public void oneRetryRequiresUnchangedVerifiedQuestion(){
        assertTrue(NavigationPolicy.mayRetryClick(1,true,true));
        assertFalse(NavigationPolicy.mayRetryClick(2,true,true));
        assertFalse(NavigationPolicy.mayRetryClick(1,false,true));assertFalse(NavigationPolicy.mayRetryClick(1,true,false));
    }
    @Test public void obscuredStemIsUnknownAndFreshUnobscuredFrameRestoresProof(){
        ScreenDocument masked=new ScreenDocument(Arrays.asList(new ScreenDocument.Line("A. 地球",10,100,200,140),
                new ScreenDocument.Line("下一题",10,600,200,640)),500,800);
        assertTrue(NavigationPolicy.stemProof(masked,stem).isEmpty());
        assertNotNull(NavigationPolicy.uniqueNext(masked));
        ScreenDocument restored=new ScreenDocument(Arrays.asList(new ScreenDocument.Line("单选题",10,10,200,40),
                new ScreenDocument.Line("哪一个是恒星？",10,50,300,80),new ScreenDocument.Line("A. 地球",10,100,200,140),
                new ScreenDocument.Line("下一题",10,600,200,640)),500,800);
        assertEquals(2,NavigationPolicy.stemProof(restored,stem).size());
        assertTrue(NavigationPolicy.stemProof(restored,"单选题哪一个不是恒星？").isEmpty());
    }
}
