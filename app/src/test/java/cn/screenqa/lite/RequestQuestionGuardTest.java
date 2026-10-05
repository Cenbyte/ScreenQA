package cn.screenqa.lite;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RequestQuestionGuardTest {
    private ScreenDocument doc(String... text){
        List<ScreenDocument.Line> lines=new ArrayList<>();int y=50;
        for(String t:text){lines.add(new ScreenDocument.Line(t,20,y,900,y+30));y+=45;}
        return new ScreenDocument(lines,1080,2400);
    }
    private QuestionDetection located(ScreenDocument doc,int... ids){
        List<Integer> all=new ArrayList<>();for(int id:ids)all.add(id);
        return QuestionDetection.located(new LocalQuestionLocator.Candidate(doc,"choice",Arrays.asList(ids[0],ids[1]),all));
    }
    private ScreenDocument page(String header,String stem,String option){
        return doc(header,"单选题",stem,"A. 2","B. 4","C. 6",option,"同步状态");
    }
    @Test public void alternatingWholePageHashesCannotStarveLocatorAndHeaderStaysIrrelevant(){
        ScreenDocument a=page("同步中 12%","下列哪个数是质数？","D. 8");
        ScreenDocument b=page("同步中 13%","下列哪个数是质数？","D. 8");
        assertNotEquals(QuestionStability.signature(a,null),QuestionStability.signature(b,null));
        RequestQuestionGuard guard=new RequestQuestionGuard(a);
        for(int i=0;i<100;i++)assertTrue(guard.accepts(i%2==0?a:b));
        assertTrue(guard.locate(located(a,2,3,4,5,6,7),b));
        for(int i=0;i<100;i++)assertTrue(guard.accepts(i%2==0?a:b));
    }
    @Test public void realChangeDuringLocateRejectsBeforeSolving(){
        ScreenDocument a=page("同步中 12%","下列哪个数是质数？","D. 8");
        RequestQuestionGuard guard=new RequestQuestionGuard(a);
        assertFalse(guard.locate(located(a,2,3,4,5,6,7),page("同步中 12%","下列哪个数不是质数？","D. 8")));
    }
    @Test public void changedOptionNegationNumberAndBlankPageInvalidateSolvedScope(){
        ScreenDocument a=page("同步中 12%","条件为 x=2，下列哪个数是质数？","D. 8");
        RequestQuestionGuard guard=new RequestQuestionGuard(a);
        assertTrue(guard.locate(located(a,2,3,4,5,6,7),a));
        assertFalse(guard.accepts(page("同步中 13%","条件为 x=2，下列哪个数是质数？","D. 9")));
        assertFalse(guard.accepts(page("同步中 13%","条件为 x=2，下列哪个数不是质数？","D. 8")));
        assertFalse(guard.accepts(page("同步中 13%","条件为 x=3，下列哪个数是质数？","D. 8")));
        assertFalse(guard.accepts(doc()));
        assertFalse(guard.accepts(doc("同步中 12%","单选题","条件为 x=2，下列哪个数是质数？","A. 2","B. 4","C. 6","D. 8","E. 11","同步状态")));
    }
    @Test public void insertedHeaderRebasesAnswerLineIdsAndCoordinates()throws Exception{
        ScreenDocument a=page("同步中 12%","下列哪个数是质数？","D. 8");
        RequestQuestionGuard guard=new RequestQuestionGuard(a);
        assertTrue(guard.locate(located(a,2,3,4,5,6,7),a));
        ScreenDocument shifted=doc("页面标题","同步中 13%","单选题","下列哪个数是质数？","A. 2","B. 4","C. 6","D. 8","同步状态");
        QuestionDetection answer=ApiRequest.parseSolved("{\"complete\":true,\"answer\":\"A\",\"question_summary\":\"选择质数\"}",a,located(a,2,3,4,5,6,7));
        QuestionDetection rebased=guard.rebase(answer,shifted);
        assertNotNull(rebased);assertEquals(Arrays.asList(3,4),rebased.stemIds);
        assertEquals(Arrays.asList(3,4,5,6,7,8),rebased.questionIds);assertEquals("A",rebased.answer);
        assertEquals(answer.summary,rebased.summary);assertEquals(shifted.lines.get(2).top-8,shifted.bounds(rebased.questionIds)[1]);
    }
    @Test public void materialBetweenSelectedLinesAndDuplicateQuestionCannotBeIgnored(){
        ScreenDocument a=doc("单选题","根据材料选择答案。","材料：水温恒定","A. 第一种情况","B. 第二种情况");
        RequestQuestionGuard guard=new RequestQuestionGuard(a);
        assertTrue(guard.locate(located(a,1,2,4,5),a));
        assertFalse(guard.accepts(doc("单选题","根据材料选择答案。","材料：水温变化","A. 第一种情况","B. 第二种情况")));
        assertFalse(guard.accepts(doc("单选题","根据材料选择答案。","材料：水温恒定","A. 第一种情况","B. 第二种情况",
                "单选题","根据材料选择答案。","材料：水温恒定","A. 第一种情况","B. 第二种情况")));
    }
    @Test public void noQuestionResultDoesNotMarkChangedPageAnswered()throws Exception{
        ScreenDocument a=doc("页面标题","加载中请稍候");RequestQuestionGuard guard=new RequestQuestionGuard(a);
        QuestionDetection none=QuestionDetection.parse("{\"has_question\":false}",a);
        assertTrue(guard.locate(none,a));assertFalse(guard.accepts(doc("新页面","下列哪个数是质数？")));
        assertNull(guard.rebase(none,doc("新页面","下列哪个数是质数？")));
    }
}
