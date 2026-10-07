package cn.screenqa.lite;

import android.content.SharedPreferences;
import org.junit.Test;
import org.json.JSONObject;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Runs the real request pipeline with a fake HTTPS transport, without credentials or live AI. */
public final class ReasoningPipelineTest {
    private static final java.util.List<JSONObject> sent=new java.util.ArrayList<>();
    private static String locator;
    private static int phase;
    private static void check(boolean value){if(!value)throw new AssertionError("pipeline assertion failed");}
    @Test public void realPipelineKeepsLocationFastAndAnswerPolicyFrozen()throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url){return new Fake(url);}
        }:null);
        Settings settings=new Settings(preferences());settings.setEndpoint(Settings.ENDPOINT);settings.setModelId(Settings.MODEL);
        ScreenDocument doc=new ScreenDocument(java.util.Arrays.asList(
                new ScreenDocument.Line("判断题：地球是行星",0,0,500,50)),500,1000);
        String complete="{\"has_question\":true,\"complete\":true,\"question_type\":\"true_false\",\"stem_line_ids\":[1],\"question_line_ids\":[1]}";
        for(int level=1;level<=5;level++){
            sent.clear();phase=0;locator=complete;settings.setReasoningLevel(level);
            ApiRequest call=new ApiRequest(settings,null,"pipeline_probe");
            final boolean[] transitioned={false};
            call.onAnswering(()->{transitioned[0]=true;settings.setReasoningLevel(1);});
            QuestionDetection result=call.detect("fake-key",doc);
            check(transitioned[0]);check(sent.size()==2);check(result.complete);check("正确".equals(result.answer));
            JSONObject locate=sent.get(0),solve=sent.get(1);
            check("disabled".equals(locate.getJSONObject("thinking").getString("type")));check(!locate.has("reasoning_effort"));check(locate.getInt("max_tokens")==768);
            check(locate.getJSONArray("messages").getJSONObject(0).getString("content").contains("不解答"));
            check(solve.getJSONArray("messages").getJSONObject(0).getString("content").equals(ApiRequest.answerSystem(new ReasoningStrategy(level))));
            check((level>=4?"enabled":"disabled").equals(solve.getJSONObject("thinking").getString("type")));
            if(level>=4)check((level==4?"high":"max").equals(solve.getString("reasoning_effort")));else check(!solve.has("reasoning_effort"));
            check(!result.answer.contains("hidden reasoning"));
        }
        for(String raw:new String[]{"{\"has_question\":false}",complete.replace("\"complete\":true","\"complete\":false")}){
            sent.clear();phase=0;locator=raw;settings.setReasoningLevel(5);
            QuestionDetection result=new ApiRequest(settings,null,"pipeline_probe").detect("fake-key",doc);check(!result.complete);check(sent.size()==1);
        }
        // A changed question is rejected after locating and must never submit an answer.
        sent.clear();phase=0;locator=complete;
        ApiRequest changed=new ApiRequest(settings,null,"pipeline_probe");
        changed.onLocated(detection->false);
        try{changed.detect("fake-key",doc);throw new AssertionError("stale location submitted answer");}
        catch(java.io.InterruptedIOException expected){check(sent.size()==1);}
        // References enter the actual answer POST only, never the locator POST.
        sent.clear();phase=0;locator=complete;settings.setReasoningLevel(3);
        String reference="参考 1 · e_eval / sample-id\n参考题：地球是行星\n参考答案：正确";
        ApiRequest ragged=new ApiRequest(settings,null,"rag_probe").withRag((stem,full,stopped)->reference);
        check(ragged.detect("fake-key",doc).complete);check(sent.size()==2);
        check(!sent.get(0).toString().contains(reference));
        check(sent.get(1).getJSONArray("messages").getJSONObject(1).getString("content").contains(reference));
        check(sent.get(1).getJSONArray("messages").getJSONObject(0).getString("content").contains(KnowledgeRag.INSTRUCTION));
        check(Settings.MODEL.equals(sent.get(1).getString("model")));
        // Simulate the repeated header changes from the report during the actual two-stage pipeline.
        sent.clear();phase=0;locator=complete;
        RequestQuestionGuard guard=new RequestQuestionGuard(doc);
        ScreenDocument animated=new ScreenDocument(java.util.Arrays.asList(
                new ScreenDocument.Line("页面标题变化",0,0,500,40),
                new ScreenDocument.Line("判断题：地球是行星",0,50,500,100)),500,1000);
        for(int i=0;i<100;i++)check(guard.accepts(i%2==0?doc:animated));
        ApiRequest stable=new ApiRequest(settings,null,"pipeline_probe");
        stable.onLocated(detection->guard.locate(detection,animated));
        QuestionDetection solved=stable.detect("fake-key",doc);
        check(sent.size()==2);check(guard.rebase(solved,animated).stemIds.equals(java.util.Arrays.asList(2)));
        sent.clear();phase=0;locator=complete;
        ApiRequest cancelled=new ApiRequest(settings,null,"pipeline_probe");cancelled.onAnswering(cancelled::cancel);
        try{cancelled.detect("fake-key",doc);throw new AssertionError("cancelled locator submitted answer");}
        catch(java.io.InterruptedIOException expected){check(sent.size()==1);}
    }
    private static final class Fake extends HttpsURLConnection {
        private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        Fake(URL url){super(url);}
        @Override public java.io.OutputStream getOutputStream(){return output;}
        @Override public int getResponseCode(){return 200;}
        @Override public java.io.InputStream getInputStream()throws java.io.IOException{
            try{
                sent.add(new JSONObject(output.toString("UTF-8")));
                String content=phase++==0?locator:"{\"complete\":true,\"answer\":\"正确\",\"question_summary\":\"地球是否行星\"}";
                JSONObject response=new JSONObject().put("model",Settings.MODEL).put("choices",new org.json.JSONArray().put(new JSONObject()
                        .put("finish_reason","stop").put("message",new JSONObject().put("content",content).put("reasoning_content","hidden reasoning"))))
                        .put("usage",new JSONObject().put("prompt_tokens",10).put("completion_tokens",10).put("total_tokens",20));
                return new ByteArrayInputStream(response.toString().getBytes(StandardCharsets.UTF_8));
            }catch(Exception e){throw new java.io.IOException(e);}
        }
        @Override public void disconnect(){}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
        @Override public String getCipherSuite(){return "test";}
        @Override public java.security.cert.Certificate[] getLocalCertificates(){return null;}
        @Override public java.security.cert.Certificate[] getServerCertificates(){return new java.security.cert.Certificate[0];}
    }
    private static SharedPreferences preferences(){
        java.util.Map<String,Object> values=new java.util.HashMap<>();
        final Object[] editor={null};
        editor[0]=java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(proxy,method,args)->{
            String name=method.getName();
            if(name.startsWith("put")){values.put((String)args[0],args[1]);return editor[0];}
            if(name.equals("remove")){values.remove(args[0]);return editor[0];}
            if(name.equals("commit"))return true;
            if(name.equals("apply"))return null;
            throw new UnsupportedOperationException(name);
        });
        return (SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            String name=method.getName();
            if(name.equals("edit"))return editor[0];
            if(name.equals("contains"))return values.containsKey(args[0]);
            if(name.startsWith("get")&&args!=null&&args.length==2)return values.getOrDefault(args[0],args[1]);
            throw new UnsupportedOperationException(name);
        });
    }

}
