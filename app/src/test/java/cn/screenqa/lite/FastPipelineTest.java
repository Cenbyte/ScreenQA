package cn.screenqa.lite;

import org.junit.Test;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import static org.junit.Assert.*;

public class FastPipelineTest {
    private ScreenDocument doc(String... content) {
        List<ScreenDocument.Line> lines=new ArrayList<>();int y=240;
        for(String text:content){lines.add(new ScreenDocument.Line(text,32,y,960,y+36));y+=55;}
        return new ScreenDocument(lines,1080,2400);
    }
    @Test public void groupsLabelNumberMultilineStemAndAllOptions() {
        LocalQuestionLocator.Candidate c=LocalQuestionLocator.locate(doc("课程练习","单选题","1. 下列说法正确的是：","请选择一项。","A. 地球是恒星","B. 太阳是恒星","C. 月球是恒星","D. 火星是恒星","提交答案","答案解析：请参考教材"));
        assertNotNull(c);assertEquals("choice",c.type);
        String text=c.document.text(c.all);assertFalse(text.contains("课程练习"));assertFalse(text.contains("答案解析"));
        assertTrue(text.contains("D. 火星"));assertTrue(c.document.text(c.stem).contains("请选择一项"));
        assertFalse(c.document.text(c.stem).contains("A. 地球"));
    }
    @Test public void optionContinuationsArePreserved() {
        LocalQuestionLocator.Candidate c=LocalQuestionLocator.locate(doc("单选题","以下哪个描述正确？","A. 第一行","选项的第二行内容","B. 第二个","C. 第三个","D. 第四个"));
        assertNotNull(c);assertTrue(c.document.text(c.all).contains("选项的第二行内容"));
        assertFalse(c.document.text(c.stem).contains("选项的第二行内容"));
    }
    @Test public void incompleteChoiceFallsBackInsteadOfCropping() {
        assertNull(LocalQuestionLocator.locate(doc("单选题","请选择正确的物理单位。","A. 米","B. 秒")));
    }
    @Test public void sharedMaterialFallsBack() {
        assertNull(LocalQuestionLocator.locate(doc("请阅读下面材料：某市降水增加。","简答题","简述该现象产生的原因。")));
    }
    @Test public void doesNotPromoteChatOrNavigationToQuestion() {
        assertNull(LocalQuestionLocator.locate(doc("消息列表","今天下午去哪里？","设置","返回")));
    }
    @Test public void fillBlankKeepsEveryBlankAndFiltersInput() {
        LocalQuestionLocator.Candidate c=LocalQuestionLocator.locate(doc("填空题","水由____和____两种元素组成。","请输入答案","提交"));
        assertNotNull(c);assertEquals("fill_blank",c.type);assertEquals(2,c.all.size());
        assertTrue(c.document.text(c.all).contains("____和____"));
    }
    @Test public void blanksInsideChoiceDoNotTurnItIntoFillBlank() {
        LocalQuestionLocator.Candidate c=LocalQuestionLocator.locate(doc("单选题","地球的卫星是____。","A. 月球","B. 太阳","C. 火星","D. 金星"));
        assertNotNull(c);assertEquals("choice",c.type);
        assertEquals("choice",LocalQuestionLocator.type("单选题\n地球的卫星是____。"));
    }
    @Test public void selectsOneCenteredQuestionWithoutMergingNext() {
        LocalQuestionLocator.Candidate c=LocalQuestionLocator.locate(doc("填空题","1. 地球的卫星是____。","填空题","2. 太阳系最大行星是____。"));
        assertNotNull(c);assertFalse(c.document.text(c.all).contains("地球的卫星"));
        assertTrue(c.document.text(c.all).contains("太阳系最大"));
    }
    @Test public void changingOnlyOptionInvalidatesPendingAnswer() {
        QuestionTracker t=new QuestionTracker();
        LocalQuestionLocator.Candidate a=LocalQuestionLocator.locate(doc("单选题","下列哪一个是质数？","A. 2","B. 4","C. 6","D. 8"));
        LocalQuestionLocator.Candidate b=LocalQuestionLocator.locate(doc("单选题","下列哪一个是质数？","A. 4","B. 2","C. 6","D. 8"));
        t.observe(a.document.fingerprint());t.observe(a.document.fingerprint());int pending=t.begin();
        t.observe(b.document.fingerprint());assertFalse(t.complete(pending,true,1200));
    }
    @Test public void movingCoordinatesDoesNotResubmitSameQuestion() {
        ScreenDocument a=doc("填空题","地球的卫星是____。");
        List<ScreenDocument.Line> shifted=new ArrayList<>();
        for(ScreenDocument.Line l:a.lines)shifted.add(new ScreenDocument.Line(l.text,l.left,l.top+10,l.right,l.bottom+10));
        assertEquals(a.fingerprint(),new ScreenDocument(shifted,a.width,a.height).fingerprint());
    }
    @Test public void choicePresentationIsInlineButLongAnswersAreNot() {
        assertEquals("A",AnswerPresentation.compact("choice","答案：A（对应内容）"));
        assertEquals("AC",AnswerPresentation.compact("choice","A、C"));
        assertTrue(AnswerPresentation.inlineChoice("choice","B"));
        assertFalse(AnswerPresentation.inlineChoice("fill_blank","B"));
        assertFalse(AnswerPresentation.inlineChoice("choice","无法确定"));
    }
    @Test public void compactLocatorPayloadKeepsIdsAndLocalGeometry() throws Exception {
        ScreenDocument d=doc("单选题","地球的卫星是？","A. 月球","B. 太阳");
        JSONObject compact=new JSONObject(d.modelJson());
        assertFalse(compact.has("screen_width"));
        JSONArray lines=compact.getJSONArray("lines");
        for(int i=0;i<d.lines.size();i++) {
            assertEquals(i+1,lines.getJSONObject(i).getInt("id"));
            assertEquals(d.lines.get(i).text,lines.getJSONObject(i).getString("text"));
            assertFalse(lines.getJSONObject(i).has("box"));
            assertEquals((int)(500L*(d.lines.get(i).top+d.lines.get(i).bottom)/d.height),lines.getJSONObject(i).getInt("y"));
        }
        assertTrue(d.modelJson().length()<d.json().length());
        QuestionDetection result=QuestionDetection.parse("{\"has_question\":true,\"complete\":true,\"question_type\":\"choice\",\"stem_line_ids\":[1,2],\"question_line_ids\":[1,2,3,4],\"answer\":\"A\"}",d);
        assertArrayEquals(d.bounds(Arrays.asList(1,2,3,4)),d.bounds(result.questionIds));
    }
    @Test public void outputBudgetsKeepRoomForTextAnswers() {
        assertEquals(128,ApiRequest.answerLimit("choice"));
        assertEquals(128,ApiRequest.answerLimit("true_false"));
        assertEquals(384,ApiRequest.answerLimit("fill_blank"));
        assertEquals(768,ApiRequest.answerLimit("short_answer"));
    }
    @Test public void deepSeekOnlyRequestUsesFlashAndDisablesThinking() throws Exception {
        JSONObject body=ApiRequest.requestBody("instruction","question",128);
        assertEquals("https://api.deepseek.com/chat/completions",Settings.ENDPOINT);
        assertEquals("deepseek-flash",body.getString("model"));
        assertEquals(128,body.getInt("max_tokens"));
        assertEquals("question",body.getJSONArray("messages").getJSONObject(1).getString("content"));
        assertEquals("disabled",body.getJSONObject("thinking").getString("type"));
    }
    @Test public void legacyProviderKeyIsNeverTreatedAsDeepSeekKey() {
        assertTrue(Settings.isOfficialLegacyBase("https://api.deepseek.com/v1"));
        assertFalse(Settings.isOfficialLegacyBase("https://api.deepseek.com.evil.example/v1"));
        assertFalse(Settings.isOfficialLegacyBase("http://api.deepseek.com/v1"));
        assertFalse(Settings.isOfficialLegacyBase("https://other-provider.example/v1"));
    }
}
