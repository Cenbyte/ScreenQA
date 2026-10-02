package cn.screenqa.lite;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class QuestionStabilityTest {
    private ScreenDocument doc(String... values){
        List<ScreenDocument.Line> lines=new ArrayList<>();int y=80;
        for(String value:values){lines.add(new ScreenDocument.Line(value,20,y,900,y+30));y+=45;}
        return new ScreenDocument(lines,1080,2400);
    }
    private LocalQuestionLocator.Candidate candidate(ScreenDocument d,int stemCount){
        List<Integer> all=new ArrayList<>(),stem=new ArrayList<>();
        for(int i=1;i<=d.lines.size();i++){all.add(i);if(i<=stemCount)stem.add(i);}
        return new LocalQuestionLocator.Candidate(d,"choice",stem,all);
    }
    @Test public void animatedHeaderDoesNotResetStableQuestionOrCancelRequest(){
        ScreenDocument first=doc("同步中 12%","单选题","1. 下列哪个数是质数？","A. 2","B. 4","C. 6","D. 8");
        ScreenDocument second=doc("同步中 13%","学习练习","单选题","1. 下列哪个数是质数？","A. 2","B. 4","C. 6","D. 8");
        assertNotEquals(first.fingerprint(),second.fingerprint());
        String a=QuestionStability.signature(first,LocalQuestionLocator.locate(first));
        String b=QuestionStability.signature(second,LocalQuestionLocator.locate(second));
        assertEquals(a,b);
        QuestionTracker tracker=new QuestionTracker();tracker.observe(a);
        assertTrue(tracker.waitingForStability());assertFalse(tracker.ready(0));
        tracker.observe(b);assertTrue(tracker.ready(0));int token=tracker.begin();
        tracker.observe(a);assertTrue(tracker.isCurrent(token));assertFalse(tracker.ready(1000));
    }
    @Test public void wrappingAndOptionLabelFormattingAcrossSourcesStayStable(){
        ScreenDocument first=doc("单选题","月球是地球的卫星。","A. 正确的描述","B. 错误","C. 其他","D. 不确定");
        ScreenDocument second=doc("【单选题】","月球是地球的","卫星。","Ａ、正确的","描述","B：错误","C. 其他","D. 不确定");
        assertNotEquals(QuestionTracker.normalize(first.fingerprint()),QuestionTracker.normalize(second.fingerprint()));
        assertEquals(QuestionStability.signature(first,candidate(first,2)),
                QuestionStability.signature(second,candidate(second,3)));
    }
    @Test public void changedLastOptionRejectsLateResult(){
        ScreenDocument first=doc("单选题","下列哪个数是质数？","A. 2","B. 4","C. 6","D. 8");
        ScreenDocument changed=doc("单选题","下列哪个数是质数？","A. 2","B. 4","C. 6","D. 9");
        QuestionTracker tracker=new QuestionTracker();String key=QuestionStability.signature(first,candidate(first,2));
        tracker.observe(key);tracker.observe(key);int token=tracker.begin();
        tracker.observe(QuestionStability.signature(changed,candidate(changed,2)));
        assertFalse(tracker.isCurrent(token));assertFalse(tracker.complete(token,true,100));
        assertTrue(tracker.waitingForStability());
    }
    @Test public void singleAndMultipleChoiceAndOptionOrderStillInvalidate(){
        ScreenDocument single=doc("单选题","请选择质数。","A. 2","B. 3","C. 4","D. 6");
        ScreenDocument multi=doc("多选题","请选择质数。","A. 2","B. 3","C. 4","D. 6");
        ScreenDocument swapped=doc("单选题","请选择质数。","B. 3","A. 2","C. 4","D. 6");
        assertNotEquals(QuestionStability.signature(single,candidate(single,2)),QuestionStability.signature(multi,candidate(multi,2)));
        assertNotEquals(QuestionStability.signature(single,candidate(single,2)),QuestionStability.signature(swapped,candidate(swapped,2)));
    }
    @Test public void ambiguousPageStillChecksWholePageAndBlankNeverSubmits(){
        ScreenDocument first=doc("共同材料：条件一","请选择答案。"),changed=doc("共同材料：条件二","请选择答案。");
        assertFalse(QuestionStability.signature(first,null).isEmpty());
        assertNotEquals(QuestionStability.signature(first,null),QuestionStability.signature(changed,null));
        QuestionTracker tracker=new QuestionTracker();tracker.observe("");tracker.observe("");
        assertFalse(tracker.ready(10000));assertFalse(tracker.waitingForStability());
    }
    @Test public void unlocatedLongLineWrappingDoesNotCancelCurrentRequest(){
        ScreenDocument first=doc("1. 请根据材料说明植物如何适应环境并举例解释。","A. 通过改变叶片形状适应水分条件","B. 其他");
        ScreenDocument wrapped=doc("1. 请根据材料说明植物如何", "适应环境并举例解释。","Ａ、通过改变叶片形状适应水分条件","B：其他");
        String key=QuestionStability.signature(first,null);
        assertEquals(key,QuestionStability.signature(wrapped,null));
        QuestionTracker tracker=new QuestionTracker();tracker.observe(key);tracker.observe(key);int token=tracker.begin();
        tracker.observe(QuestionStability.signature(wrapped,null));assertTrue(tracker.isCurrent(token));
    }
    @Test public void unlocatedPageKeepsNumbersMaterialAndOrder(){
        ScreenDocument first=doc("共同材料：水温保持恒定", "12", "34", "A. 第一种情况", "B. 第二种情况");
        assertNotEquals(QuestionStability.signature(first,null),QuestionStability.signature(doc("共同材料：水温发生变化","12","34","A. 第一种情况","B. 第二种情况"),null));
        assertNotEquals(QuestionStability.signature(first,null),QuestionStability.signature(doc("共同材料：水温保持恒定","1234","A. 第一种情况","B. 第二种情况"),null));
        assertNotEquals(QuestionStability.signature(first,null),QuestionStability.signature(doc("共同材料：水温保持恒定","12","35","A. 第一种情况","B. 第二种情况"),null));
        assertNotEquals(QuestionStability.signature(first,null),QuestionStability.signature(doc("共同材料：水温保持恒定","12","34","B. 第二种情况","A. 第一种情况"),null));
        assertEquals("",QuestionStability.signature(doc(),null));
    }
    @Test public void retryWaitIsDistinctFromStabilityAndRemainsBounded(){
        QuestionTracker tracker=new QuestionTracker();tracker.observe("完整题目和选项");tracker.observe("完整题目和选项");
        tracker.complete(tracker.begin(),false,100);
        assertFalse(tracker.waitingForStability());assertEquals(10000,tracker.retryDelay(100));
        assertFalse(tracker.ready(9999));assertTrue(tracker.ready(10100));
        tracker.complete(tracker.begin(),false,10100);tracker.complete(tracker.begin(),false,20100);
        assertTrue(tracker.retriesExhausted());assertFalse(tracker.ready(999999));
        tracker.reset();assertFalse(tracker.retriesExhausted());
    }
}
