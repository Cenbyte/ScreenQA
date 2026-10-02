package cn.screenqa.lite;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic layout based on the supplied APK's DOM: split key/label, scrollable page, no type anchor. */
public class PwaNavigationTest {
    @Test public void innerCharacterVariantKeepsStemButDifferentQuestionDoesNot(){
        ScreenDocument page=doc("网页内容通常由哪种语言描述结构?",0,"下一题 →","HTML语言","CSS样式");
        assertFalse(NavigationPolicy.stemProof(page,"网页內容通常由哪种语言描述结构?").isEmpty());
        assertTrue(NavigationPolicy.stemProof(page,"网页内容通常由哪种语言描述样式?").isEmpty());
    }
    private final String stem="下列哪种天体属于恒星？";
    private ScreenDocument doc(String question,int offset,String next,String a,String b){
        return new ScreenDocument(Arrays.asList(
                new ScreenDocument.Line("答题实验室",20,30,250,70,1),
                new ScreenDocument.Line("已答 5 题",20,80,250,110,2),
                new ScreenDocument.Line(question,20,200+offset,450,240+offset,3),
                new ScreenDocument.Line("A",20,300+offset,45,330+offset,4),new ScreenDocument.Line(a,70,300+offset,450,330+offset,5),
                new ScreenDocument.Line("B",20,400+offset,45,430+offset,6),new ScreenDocument.Line(b,70,400+offset,450,430+offset,7),
                new ScreenDocument.Line(next,250,700,450,750,8)),500,800);
    }
    @Test public void missingAccessibilityAndTypeAnchorStillPermitOcrProof(){
        ScreenDocument doc=doc(stem,0,"下一题 →","太阳星球","地球行星");
        assertNull(LocalQuestionLocator.locate(doc));
        assertEquals(1,NavigationPolicy.stemProof(doc,stem).size());
        assertEquals(stem,NavigationPolicy.stemProof(doc,stem).get(0).text);
        assertNotNull(NavigationPolicy.uniqueNext(doc));
    }
    @Test public void proofExcludesChangingProgressCountersAndSelectedOptions(){
        ScreenDocument doc=doc(stem,0,"下一题 →","太阳星球","地球行星");
        for(ScreenDocument.Line line:NavigationPolicy.stemProof(doc,stem))assertEquals(3,line.visualSignature);
    }
    @Test public void ourScrollCanRetainTwoSplitOptionRows(){
        ScreenDocument old=doc(stem,0,"下一题 →","太阳星球","地球行星");
        ScreenDocument scrolled=doc("",-200,"下一题 →","太阳星球","地球行星");
        assertTrue(NavigationPolicy.stemProof(scrolled,stem).isEmpty());
        assertTrue(NavigationPolicy.scrollContinuation(old,scrolled));
        assertEquals(4,NavigationPolicy.continuationProof(old,scrolled).size());
    }
    @Test public void changedOptionsDoNotAuthorizeOldQuestionNavigation(){
        ScreenDocument old=doc(stem,0,"下一题 →","太阳星球","地球行星");
        assertFalse(NavigationPolicy.scrollContinuation(old,doc("新题",-200,"下一题 →","水星行星","金星行星")));
        assertFalse(NavigationPolicy.scrollContinuation(old,doc("新题",-200,"下一题 →","太阳星球","金星行星")));
    }
    @Test public void clippedOldStemRetainsContinuityWithTwoUnchangedRows(){
        ScreenDocument old=doc(stem,0,"下一题 →","太阳星球","地球行星");
        assertTrue(NavigationPolicy.scrollContinuation(old,doc("哪种天体属于恒星？",-200,"下一题 →","太阳星球","地球行星")));
        assertFalse(NavigationPolicy.scrollContinuation(old,doc("哪种天体属于恒星？",-200,"下一题 →","太阳星球","金星行星")));
    }
    @Test public void bareLettersAndGenericJudgmentLabelsAreNotProof(){
        ScreenDocument old=doc(stem,0,"下一题 →","对","错");
        assertFalse(NavigationPolicy.scrollContinuation(old,doc("",-200,"下一题 →","对","错")));
    }
    @Test public void newStemWithCoincidentallyIdenticalOptionsDoesNotContinueOldQuestion(){
        ScreenDocument old=doc(stem,0,"下一题 →","太阳星球","地球行星");
        assertFalse(NavigationPolicy.scrollContinuation(old,doc("另一道完全不同的题干？",-200,"下一题 →","太阳星球","地球行星")));
    }
    @Test public void finalPwaResultsButtonNeverBecomesNext(){
        ScreenDocument last=doc(stem,0,"查看成绩 →","太阳星球","地球行星");
        assertNull(NavigationPolicy.uniqueNext(last));assertTrue(NavigationPolicy.terminalVisible(last));
        assertEquals(1,last.terminalLines.size());
    }
    @Test public void multiLineStemUsesOnlyMinimalMatchingSpan(){
        ScreenDocument doc=new ScreenDocument(Arrays.asList(new ScreenDocument.Line("已答 5 题",0,10,300,40),
                new ScreenDocument.Line("下列哪种天体",0,100,300,140),new ScreenDocument.Line("属于恒星？",0,150,300,190)),500,800);
        assertEquals(2,NavigationPolicy.stemProof(doc,stem).size());
    }
}
