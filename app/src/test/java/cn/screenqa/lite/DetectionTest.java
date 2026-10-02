package cn.screenqa.lite;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class DetectionTest {
    @Test public void progressHeadingIsRemovedFromNonContiguousModelStem() throws Exception {
        ScreenDocument d=doc("QUESTION","2/10","已答 2 题","CSS 主要负责网页的什么？","A 数据存储","B 页面样式");
        QuestionDetection q=QuestionDetection.parse(response("choice","[1,4]","[1,4,5,6]"),d);
        assertEquals(Collections.singletonList(4),q.stemIds);
        assertEquals(1,NavigationPolicy.stemProof(d,d.text(q.stemIds)).size());
        ScreenDocument english=doc("QUESTION: Explain how CSS affects this page.");
        assertEquals(Collections.singletonList(1),QuestionDetection.parse(response("short_answer","[1]","[1]"),english).stemIds);
    }
    private ScreenDocument doc(String... text) {
        List<ScreenDocument.Line> lines=new ArrayList<>();int y=100;
        for(String s:text){lines.add(new ScreenDocument.Line(s,40,y,900,y+35));y+=50;}
        return new ScreenDocument(lines,1080,2400);
    }
    private String response(String type,String stem,String all) {
        return "{\"has_question\":true,\"complete\":true,\"question_type\":\""+type+"\",\"stem_line_ids\":"+stem+",\"question_line_ids\":"+all+",\"answer\":\"示例答案\"}";
    }
    @Test public void fillBlankRetainsAllBlanksAndActualGeometry() throws Exception {
        ScreenDocument d=doc("返回","填空题","水由____和____两种元素组成。","请输入答案","提交答案");
        assertEquals(2,d.lines.size());
        QuestionDetection q=QuestionDetection.parse(response("fill_blank","[1,2]","[1,2]"),d);
        assertEquals("填空题",q.typeName());assertTrue(d.text(q.stemIds).contains("____和____"));
        assertArrayEquals(new int[]{32,142,908,243},d.bounds(q.questionIds));
    }
    @Test public void trueFalseDoesNotNeedQuestionMarkAndKeepsChoices() throws Exception {
        ScreenDocument d=doc("判断题","所有偶数都是合数。","正确","错误","下一题");
        QuestionDetection q=QuestionDetection.parse(response("true_false","[1,2]","[1,2,3,4]"),d);
        assertEquals("判断题",q.typeName());assertEquals(4,d.lines.size());
        assertFalse(d.text(q.stemIds).contains("错误"));assertTrue(d.text(q.questionIds).contains("错误"));
    }
    @Test public void shortAnswerUsesOnlySelectedQuestionAndMaterial() throws Exception {
        ScreenDocument d=doc("课程练习","请阅读材料：城市绿地面积持续增加。","简述城市绿地的生态作用。","下一题：什么是光合作用？");
        QuestionDetection q=QuestionDetection.parse(response("short_answer","[2,3]","[2,3]"),d);
        assertEquals("简答题",q.typeName());assertFalse(d.text(q.stemIds).contains("光合作用"));
        assertTrue(d.text(q.stemIds).contains("请阅读材料"));
    }
    @Test public void noiseFilteringPreservesMathematicalFraction() {
        ScreenDocument d=doc("倒计时 12:30","提交答案","1/2","填空题","半数表示为____。");
        assertEquals(3,d.lines.size());assertEquals("1/2",d.lines.get(0).text);
        assertEquals(d.fingerprint(),doc("倒计时 12:29","提交答案","1/2","填空题","半数表示为____。").fingerprint());
        assertEquals(doc("倒计时1分30秒","求1分钟等于多少秒？").fingerprint(),doc("倒计时1分29秒","求1分钟等于多少秒？").fingerprint());
    }
    @Test public void noQuestionDoesNotCreateRegion() throws Exception {
        QuestionDetection q=QuestionDetection.parse("{\"has_question\":false}",doc("消息列表","今天下午开会"));
        assertFalse(q.found);assertTrue(q.questionIds.isEmpty());
    }
    @Test public void rejectsInventedOrMalformedLineIds() {
        ScreenDocument d=doc("判断题","太阳是恒星。");
        for(String ids:Arrays.asList("[0]","[3]","[-1]","[1,1]","[1.5]","[\"1\"]","[]")) {
            assertThrows(JSONException.class,()->QuestionDetection.parse(response("true_false",ids,ids),d));
        }
    }
    @Test public void stemMustBeWithinFullQuestion() {
        assertThrows(JSONException.class,()->QuestionDetection.parse(response("short_answer","[2]","[1]"),doc("课程标题","请解释光合作用的意义。")));
    }
    @Test public void incompleteQuestionCannotDisplayGuessedAnswer() throws Exception {
        String raw=response("short_answer","[1]","[1]").replace("\"complete\":true","\"complete\":false");
        QuestionDetection q=QuestionDetection.parse(raw,doc("请根据缺失的图形说明原因。"));
        assertFalse(q.complete);assertFalse(q.answer.contains("示例答案"));assertTrue(q.answer.contains("不完整"));
    }
    @Test public void fencedJsonWorksButMissingTypeFails() throws Exception {
        ScreenDocument d=doc("请说明植物需要阳光的原因。");
        assertTrue(QuestionDetection.parse("```json\n"+response("short_answer","[1]","[1]")+"\n```",d).found);
        assertThrows(JSONException.class,()->QuestionDetection.parse(response("unknown","[1]","[1]"),d));
    }
    @Test public void coordinatesAreNotTrustedFromModel() throws Exception {
        ScreenDocument d=doc("简述光合作用的意义。");
        String raw=response("short_answer","[1]","[1]").replace("\"answer\"","\"box\":[0,0,99999,99999],\"answer\"");
        QuestionDetection q=QuestionDetection.parse(raw,d);
        assertArrayEquals(new int[]{32,92,908,143},d.bounds(q.questionIds));
    }
    @Test public void documentJsonAndStableIdentityRetainLineBoundaries() throws Exception {
        ScreenDocument d=doc("题干内容","后续条件");
        assertEquals(2,new JSONObject(d.json()).getJSONArray("lines").length());
        assertNotEquals(QuestionTracker.normalize(d.fingerprint()),QuestionTracker.normalize(doc("题干内容后续条件").fingerprint()));
    }
    @Test public void changingTypeAndLateResultInvalidateRequest() {
        QuestionTracker t=new QuestionTracker();
        String f=doc("填空题","月球是地球的____。").fingerprint();
        t.observe(f);assertFalse(t.ready(0));t.observe(f);assertTrue(t.ready(0));int token=t.begin();
        t.observe(doc("判断题","月球是地球的卫星。","正确","错误").fingerprint());
        assertFalse(t.complete(token,true,1000));
    }
}
