package cn.screenqa.lite;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AutoSelectionTest {
    private ScreenDocument doc(String... texts) {
        List<ScreenDocument.Line> lines=new ArrayList<>();int y=200;
        for(String text:texts){lines.add(new ScreenDocument.Line(text,40,y,700,y+35));y+=52;}
        return new ScreenDocument(lines,1080,2400);
    }
    private AnswerTargetResolver.Target target(String type,String answer,ScreenDocument doc,int stemCount) {
        List<Integer> stem=new ArrayList<>();for(int i=1;i<=stemCount;i++)stem.add(i);
        return AnswerTargetResolver.resolve(type,answer,doc,AnswerTargetResolver.everyLine(doc),stem);
    }
    @Test public void mapsExactlyOneChoiceToExistingOption() {
        ScreenDocument doc=doc("单选题","哪一个是恒星？","A. 地球","B. 太阳","C. 月球","D. 火星");
        assertEquals(4,target("choice","答案：B",doc,2).lineId);
        assertNull(target("choice","B、C",doc,2));
        assertNull(target("choice","无法判断",doc,2));
    }
    @Test public void mapsAccessibilityCommaLabelsAndSeparateLetterNodes() {
        ScreenDocument described=doc("单选题","哪一个是恒星？","A，地球","B，太阳","C，月球","D，火星");
        assertEquals(4,target("choice","B",described,2).lineId);
        ScreenDocument separated=doc("单选题","哪一个是恒星？","A","地球","B","太阳","C","月球","D","火星");
        assertEquals(5,target("choice","B",separated,2).lineId);
    }
    @Test public void locatesTargetOutsideModelQuestionBoxButNearStem() {
        ScreenDocument doc=doc("单选题","哪一个是恒星？","A. 地球","B. 太阳","C. 月球","D. 火星");
        assertNull(AnswerTargetResolver.resolve("choice","B",doc,Arrays.asList(1,2,3),Arrays.asList(1,2)));
        assertEquals(4,AnswerTargetResolver.resolveNearStem("choice","B",doc,Arrays.asList(1,2)).lineId);
    }
    @Test public void nearbySearchRejectsDuplicateOptionLabels() {
        ScreenDocument doc=doc("单选题","请选择恒星","A. 地球","B. 太阳","B. 另一个","C. 月球");
        assertNull(AnswerTargetResolver.resolveNearStem("choice","B",doc,Arrays.asList(1,2)));
    }
    @Test public void refusesMultiChoiceEvenIfModelReturnsOneLetter() {
        ScreenDocument doc=doc("多选题","下列哪些是行星？","A. 地球","B. 太阳","C. 火星","D. 月球");
        assertNull(target("choice","A",doc,2));
    }
    @Test public void refusesDuplicateOrMergedTargets() {
        assertNull(target("choice","A",doc("单选题","请选择","A. 第一","A. 重复","B. 第二"),2));
        assertNull(target("choice","A",doc("单选题","请选择","A. 第一 B. 第二","C. 第三","D. 第四"),2));
    }
    @Test public void mapsTrueFalseWithoutConfusingStem() {
        ScreenDocument doc=doc("判断题","下面说法正确还是错误？","正确","错误");
        assertEquals(3,target("true_false","正确",doc,2).lineId);
        assertEquals(4,target("true_false","答案：错误",doc,2).lineId);
        assertNull(target("true_false","正确或错误",doc,2));
    }
    @Test public void localJudgmentCandidateKeepsTrueFalseOptionsOutOfStem() {
        LocalQuestionLocator.Candidate c=LocalQuestionLocator.locate(doc("判断题","太阳是一颗恒星。","正确","错误"));
        assertNotNull(c);assertEquals("true_false",c.type);
        assertEquals(2,c.stem.size());
        assertEquals(3,AnswerTargetResolver.resolve(c.type,"正确",c.document,c.all,c.stem).lineId);
    }
    @Test public void refusesMissingOrDuplicateJudgmentOptions() {
        assertNull(target("true_false","正确",doc("判断题","地球是恒星。","正确"),2));
        assertNull(target("true_false","错误",doc("判断题","地球是恒星。","正确","错误","错误"),2));
    }
    @Test public void complexTypesRemainManual() {
        ScreenDocument doc=doc("填空题","地球的卫星是____","A.月球","B.太阳");
        assertNull(target("fill_blank","月球",doc,2));
        assertNull(target("short_answer","月球",doc,2));
    }
    @Test public void strategyValuesUseOneSharedPipeline() {
        assertEquals(AutoAnswerStrategy.HYBRID,AutoAnswerStrategy.fromStored(-1));
        assertTrue(AutoAnswerStrategy.ACCESSIBILITY_FIRST.readNodesFirst());
        assertTrue(AutoAnswerStrategy.ACCESSIBILITY_FIRST.retryNodesBeforeVision());
        assertFalse(AutoAnswerStrategy.HYBRID.retryNodesBeforeVision());
        assertFalse(AutoAnswerStrategy.VISION_FIRST.readNodesFirst());
    }
}
