package cn.screenqa.lite;

import org.junit.Test;
import org.json.JSONObject;
import java.util.HashSet;
import static org.junit.Assert.*;

public final class ReasoningStrategyTest {
    @Test public void fivePoliciesHaveDistinctPromptsAndRealParameters() throws Exception {
        HashSet<String> prompts=new HashSet<>();
        for(int level=1;level<=5;level++){
            ReasoningStrategy s=new ReasoningStrategy(level);prompts.add(s.prompt());
            JSONObject body=ApiRequest.strategyRequestBody(Settings.ENDPOINT,Settings.MODEL,s.prompt(),"题目",128,s);
            assertEquals(level>=4?"enabled":"disabled",body.getJSONObject("thinking").getString("type"));
            if(level>=4){assertEquals(new String[]{"high","max"}[level-4],body.getString("reasoning_effort"));assertTrue(body.getInt("max_tokens")>128);}
            else {assertFalse(body.has("reasoning_effort"));assertEquals(128,body.getInt("max_tokens"));}
            assertTrue(s.prompt().contains("不输出思考过程"));assertTrue(s.prompt().contains("判断题仅给正确/错误"));
        }
        assertEquals(3,prompts.size());
        assertEquals(new ReasoningStrategy(1).prompt(),new ReasoningStrategy(4).prompt());
        assertEquals(new ReasoningStrategy(2).prompt(),new ReasoningStrategy(5).prompt());
    }
    @Test public void continuousProgressMirrorsAndSnapsAcrossRapidMoves(){
        for(int i=0;i<10000;i++){
            float progress=1+(i*71%4001)/1000f;
            assertEquals(6,progress+ReasoningStrategy.mirror(progress),0.00001);
            if(i*71%4001%1000!=500)assertEquals(6-ReasoningStrategy.snap(progress),ReasoningStrategy.snap(ReasoningStrategy.mirror(progress)));
        }
        assertEquals(1,ReasoningStrategy.snap(-10));assertEquals(5,ReasoningStrategy.snap(20));
    }
    @Test public void capturedStrategyCannotFollowLaterSelection() throws Exception {
        int selection=4;ReasoningStrategy running=new ReasoningStrategy(selection);selection=1;
        JSONObject body=ApiRequest.strategyRequestBody(Settings.ENDPOINT,Settings.MODEL,running.prompt(),"题目",128,running);
        assertEquals("high",body.getString("reasoning_effort"));assertEquals(4,running.reasoningLevel);
        assertFalse(new ReasoningStrategy(selection).thinking());
    }
    @Test public void customEndpointsAndUnverifiedModelsNeverReceiveInventedEffort() throws Exception {
        for(String endpoint:new String[]{"https://example.com/chat/completions","https://api.deepseek.com.evil.example/chat/completions"}){
            JSONObject body=ApiRequest.strategyRequestBody(endpoint,Settings.MODEL,"协议","题目",128,new ReasoningStrategy(5));
            assertFalse(body.has("reasoning_effort"));assertEquals("enabled",body.getJSONObject("thinking").getString("type"));
        }
        assertFalse(ReasoningStrategy.supportsEffort(Settings.ENDPOINT,"custom-model"));
        assertFalse(ReasoningStrategy.supportsEffort(Settings.ENDPOINT,"deepseek-reasoner"));
    }
    @Test public void fivePoliciesKeepExistingDetectionProtocol() throws Exception {
        ScreenDocument doc=new ScreenDocument(java.util.Arrays.asList(new ScreenDocument.Line("判断题：地球是行星",0,0,500,50)),500,1000);
        for(int level=1;level<=5;level++){
            JSONObject body=ApiRequest.strategyRequestBody(Settings.ENDPOINT,Settings.MODEL,new ReasoningStrategy(level).prompt(),"题目",128,new ReasoningStrategy(level));
            assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("选择题仅输出选项字母"));
            QuestionDetection d=QuestionDetection.parse("{\"has_question\":true,\"complete\":true,\"question_type\":\"true_false\",\"stem_line_ids\":[1],\"question_line_ids\":[1],\"answer\":\"正确\",\"question_summary\":\"地球是否行星\"}",doc);
            assertTrue(d.complete);assertEquals("正确",d.answer);
        }
    }
    @Test public void locatingIsAlwaysFastAndSolvingCannotChangeItsGeometry() throws Exception {
        ScreenDocument doc=new ScreenDocument(java.util.Arrays.asList(new ScreenDocument.Line("判断题：地球是行星",0,0,500,50)),500,1000);
        ApiRequest.LocatedQuestion location=ApiRequest.parseLocated("{\"has_question\":true,\"complete\":true,\"question_type\":\"true_false\",\"stem_line_ids\":[1],\"question_line_ids\":[1]}",doc);
        assertTrue(location.complete);
        for(int level=1;level<=5;level++){
            JSONObject body=ApiRequest.locationRequestBody(Settings.MODEL,"只定位","题目",768);
            assertEquals("disabled",body.getJSONObject("thinking").getString("type"));
            assertFalse(body.has("reasoning_effort"));assertEquals(768,body.getInt("max_tokens"));
            QuestionDetection answer=ApiRequest.parseSolved("{\"complete\":true,\"answer\":\"正确\",\"question_type\":\"choice\",\"stem_line_ids\":[99],\"question_summary\":\"地球是否行星\"}",doc,location.detection);
            assertEquals("true_false",answer.type);assertEquals(java.util.Arrays.asList(1),answer.stemIds);assertEquals("正确",answer.answer);
        }
    }
    @Test public void missingOrInvalidLocatorConditionsDoNotBecomeSolvedQuestions() throws Exception {
        ScreenDocument doc=new ScreenDocument(java.util.Arrays.asList(new ScreenDocument.Line("判断题：地球是行星",0,0,500,50)),500,1000);
        assertFalse(ApiRequest.parseLocated("{\"has_question\":false}",doc).detection.found);
        assertFalse(ApiRequest.parseLocated("{\"has_question\":true,\"complete\":false,\"question_type\":\"true_false\",\"stem_line_ids\":[1],\"question_line_ids\":[1]}",doc).complete);
        try{ApiRequest.parseLocated("{\"has_question\":true,\"complete\":true,\"question_type\":\"true_false\",\"stem_line_ids\":[99],\"question_line_ids\":[99]}",doc);fail("invalid IDs accepted");}catch(org.json.JSONException expected){}
    }

}
