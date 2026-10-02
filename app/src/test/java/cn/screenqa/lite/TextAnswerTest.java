package cn.screenqa.lite;

import org.junit.Test;
import org.json.JSONException;
import java.util.*;
import static org.junit.Assert.*;

public class TextAnswerTest {
    private ScreenDocument doc() {
        return new ScreenDocument(Collections.singletonList(
                new ScreenDocument.Line("中国的首都是____，日本的首都是____。",20,100,900,150)),1080,2400);
    }
    @Test public void multiBlankKeepsOrderedAnswersAndCopyHasNoUiLabels() throws Exception {
        String raw="{\"has_question\":true,\"complete\":true,\"question_type\":\"fill_blank\","+
                "\"stem_line_ids\":[1],\"question_line_ids\":[1],\"answers\":[\"答案：北京\",\"东京\"]}";
        QuestionDetection question=QuestionDetection.parse(raw,doc());
        assertEquals(Arrays.asList("北京","东京"),question.answers);
        assertEquals("北京\n东京",TextAnswer.copyPayload(question.answers));
        assertEquals("1. 北京\n2. 东京",TextAnswer.display(question.answers));
    }
    @Test public void singleTextAnswerIsDirectlyCopyable() {
        assertEquals("光合作用产生有机物。",TextAnswer.copyPayload(
                Collections.singletonList("**答案：光合作用产生有机物。**")));
    }
    @Test public void blankAnswerArrayCannotBeExecuted() {
        String raw="{\"has_question\":true,\"complete\":true,\"question_type\":\"fill_blank\","+
                "\"stem_line_ids\":[1],\"question_line_ids\":[1],\"answers\":[\"北京\",\"\"]}";
        assertThrows(JSONException.class,()->QuestionDetection.parse(raw,doc()));
    }
}
