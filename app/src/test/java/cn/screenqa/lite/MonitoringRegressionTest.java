package cn.screenqa.lite;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Synthetic reproduction of the provided log: OCR-only stems and repeated scanning. */
public class MonitoringRegressionTest {
    @Test public void answeredOcrStemRemainsVisibleAfterAnswerStylingAndNextLabelChanges(){
        String stem="哪一个是恒星？";
        ScreenDocument first=doc("下一题");ScreenDocument styled=doc("下一题 →");
        assertEquals(NavigationPolicy.Evidence.SAME,NavigationPolicy.evidence(stem,styled.text(AnswerTargetResolver.everyLine(styled)),""));
        assertEquals(first.fingerprint(),styled.fingerprint());
        assertNotNull(NavigationPolicy.uniqueNext(styled));
        // Accessible tree only exposes navigation, as in old_question_not_visible log.
        assertEquals(NavigationPolicy.Evidence.UNKNOWN,NavigationPolicy.evidence(stem,"下一题", ""));
    }
    private ScreenDocument doc(String next){
        return new ScreenDocument(Arrays.asList(new ScreenDocument.Line("哪一个是恒星？",10,20,450,60),
                new ScreenDocument.Line("A. 地球",10,70,450,110),new ScreenDocument.Line("B. 太阳",10,120,450,160),
                new ScreenDocument.Line(next,10,600,200,650)),500,800);
    }
}
