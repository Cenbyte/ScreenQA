package cn.screenqa.lite;

import android.content.Context;
import org.json.JSONObject;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Runs the real request pipeline with a fake HTTPS transport, without credentials or live AI. */
final class ReasoningPipelineProbe {
    private static final java.util.List<JSONObject> sent=new java.util.ArrayList<>();
    private static String locator;
    private static int phase;
    private static void check(boolean value){if(!value)throw new AssertionError("pipeline assertion failed");}
    static void run(Context context)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url){return new Fake(url);}
        }:null);
        Settings settings=new Settings(context);settings.setEndpoint(Settings.ENDPOINT);settings.setModelId(Settings.MODEL);
        ScreenDocument doc=new ScreenDocument(java.util.Arrays.asList(
                new ScreenDocument.Line("判断题：地球是行星",0,0,500,50)),500,1000);
        String complete="{\"has_question\":true,\"complete\":true,\"question_type\":\"true_false\",\"stem_line_ids\":[1],\"question_line_ids\":[1]}";
        for(int level=1;level<=5;level++){
            sent.clear();phase=0;locator=complete;settings.setReasoningLevel(level);
            ApiRequest call=new ApiRequest(context,"pipeline_probe");
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
            QuestionDetection result=new ApiRequest(context).detect("fake-key",doc);check(!result.complete);check(sent.size()==1);
        }
        sent.clear();phase=0;locator=complete;
        ApiRequest cancelled=new ApiRequest(context);cancelled.onAnswering(cancelled::cancel);
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
}
