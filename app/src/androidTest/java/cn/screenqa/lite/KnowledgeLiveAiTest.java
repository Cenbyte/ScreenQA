package cn.screenqa.lite;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.net.ssl.*;

/** Explicitly authorized live API verification. Captures payload, forwards real TLS unchanged. */
public final class KnowledgeLiveAiTest extends Instrumentation {
    private final JSONObject report=new JSONObject();
    private final List<JSONObject> requests=new ArrayList<>();
    private String phase="";
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Context context=getTargetContext();Settings settings=new Settings(context);
        Map<String,?> saved=settings.prefs.getAll();File keyFile=new File(context.getFilesDir(),"live-ai.key");
        Bundle result=new Bundle();boolean success=false;
        try {
            check(settings.endpoint().equals(Settings.ENDPOINT) && settings.modelId().equals(Settings.MODEL),"Official existing DeepSeek configuration");
            String key=new String(Files.readAllBytes(keyFile.toPath()),StandardCharsets.UTF_8).trim();
            settings.save(key);Files.deleteIfExists(keyFile.toPath());key=null;
            check(!settings.key().isEmpty(),"Normal Android Keystore credential retrieval");
            KnowledgeLibrary library=KnowledgeLibrary.get(context);library.setRagEnabled(true);
            String full="1. 人体内含有多种多样的蛋白质，每种蛋白质（    ）\nA. 都含有21种氨基酸\nB. 都是在细胞内发挥作用\nC. 都能调节生物体的生命活动\nD. 都具有一定的空间结构";
            List<KnowledgeIndex.Hit> hits=library.search(new KnowledgeText.Query(null,full,null,null),5,()->false);
            check(hits.size()==5 && hits.get(0).exact,"Installed index yields same-question Top-5");
            report.put("endpoint",settings.endpoint()).put("configured_model",settings.modelId()).put("top_k",hits.size())
                    .put("first_source_id",hits.get(0).id).put("reference_chars",KnowledgeRag.context(hits).length());
            URL direct=new URL(settings.endpoint());
            URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){
                @Override protected URLConnection openConnection(URL url)throws IOException {
                    if(!url.toExternalForm().equals(direct.toExternalForm()))throw new IOException("Unexpected endpoint in live test");
                    return new Forwarded((HttpsURLConnection)direct.openConnection(),phase);
                }
            }:null);
            List<ScreenDocument.Line> lines=new ArrayList<>();String[] values=full.split("\n");
            for(int i=0;i<values.length;i++)lines.add(new ScreenDocument.Line(values[i],0,650+i*80,1000,700+i*80));
            ScreenDocument document=new ScreenDocument(lines,1080,1920);
            LocalQuestionLocator.Candidate candidate=LocalQuestionLocator.locate(document);check(candidate!=null,"Production local locator detects simulated OCR question");
            phase="local_solve";status("Real DeepSeek: local locator -> solve()");
            QuestionDetection local=new ApiRequest(context,"knowledge_live_verification").solve(settings.key(),candidate);
            report.put("local_app_response_parsed",local.complete);
            phase="ai_locate_then_solve";status("Real DeepSeek: AI locate -> solveLocatedBody()");
            QuestionDetection located=new ApiRequest(context,"knowledge_live_verification").detect(settings.key(),document,0);
            report.put("ai_located_app_response_parsed",located.found && located.complete);
            phase="manual_answer";status("Real DeepSeek: manual text -> run()");
            String manual=new ApiRequest(context,"knowledge_live_verification").run(settings.key(),full,false);
            report.put("manual_response_nonempty",!manual.trim().isEmpty());
            int answerRequests=0,withReferences=0,accepted=0,locationRequests=0;
            for(JSONObject request:requests) {
                if(request.getBoolean("locator_only")){locationRequests++;check(!request.getBoolean("rag_in_payload"),"Location request has no RAG");continue;}
                answerRequests++;if(request.getBoolean("rag_in_payload"))withReferences++;
                if(request.optInt("http_status")==200 && request.optBoolean("server_response_received"))accepted++;
                check(request.getBoolean("source_id_in_payload") && request.getBoolean("caution_in_system"),"Production wire payload contains source and caution");
            }
            report.put("answer_requests",answerRequests).put("answer_requests_with_rag",withReferences).put("answer_requests_http200",accepted)
                    .put("locator_requests",locationRequests).put("accuracy_evaluated",false).put("transport","real platform HTTPS to api.deepseek.com, no mock response");
            check(answerRequests==3 && withReferences==3 && accepted==3 && locationRequests==1,"All three real answer paths included RAG and received HTTP 200");
            success=true;
        } catch(Throwable error){try{report.put("failure",android.util.Log.getStackTraceString(error));}catch(Exception ignored){}}
        finally {
            try{Files.deleteIfExists(keyFile.toPath());SharedPreferences.Editor edit=settings.prefs.edit();
                for(String name:new String[]{"key","iv","base","model"}){Object value=saved.get(name);if(value instanceof String)edit.putString(name,(String)value);else edit.remove(name);}edit.commit();
                report.put("temporary_key_removed",true).put("requests",new JSONArray(requests));
                try(OutputStream out=new FileOutputStream(new File(context.getFilesDir(),"knowledge-live-ai.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
            }catch(Exception error){success=false;result.putString("cleanup_error",error.getClass().getSimpleName());}
        }
        result.putString("stream",(success?"Live RAG PASS":"Live RAG FAIL")+"; sanitized evidence: files/knowledge-live-ai.json\n");finish(success?-1:0,result);
    }
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void status(String text){Bundle bundle=new Bundle();bundle.putString("stream",text+"\n");sendStatus(0,bundle);}
    private final class Forwarded extends HttpsURLConnection {
        private final HttpsURLConnection real;private final JSONObject evidence=new JSONObject();
        private final ByteArrayOutputStream sent=new ByteArrayOutputStream(),received=new ByteArrayOutputStream();
        Forwarded(HttpsURLConnection real,String phase)throws IOException {
            super(real.getURL());this.real=real;try{evidence.put("path",phase);requests.add(evidence);}catch(JSONException error){throw new IOException(error);}
        }
        @Override public void setConnectTimeout(int n){real.setConnectTimeout(n);}
        @Override public void setReadTimeout(int n){real.setReadTimeout(n);}
        @Override public void setInstanceFollowRedirects(boolean follow){real.setInstanceFollowRedirects(follow);}
        @Override public void setRequestMethod(String method)throws ProtocolException{real.setRequestMethod(method);}
        @Override public void setDoOutput(boolean output){real.setDoOutput(output);}
        @Override public void setFixedLengthStreamingMode(int length){real.setFixedLengthStreamingMode(length);}
        @Override public void setRequestProperty(String name,String value){real.setRequestProperty(name,value);}
        @Override public OutputStream getOutputStream()throws IOException {
            OutputStream actual=real.getOutputStream();return new FilterOutputStream(actual){
                @Override public void write(int value)throws IOException{out.write(value);sent.write(value);}
                @Override public void write(byte[] bytes,int offset,int length)throws IOException{out.write(bytes,offset,length);sent.write(bytes,offset,length);}
                @Override public void close()throws IOException{super.close();capturePayload();}
            };
        }
        private void capturePayload()throws IOException {
            try {JSONObject body=new JSONObject(sent.toString("UTF-8"));JSONArray messages=body.getJSONArray("messages");
                String system=messages.getJSONObject(0).getString("content"),user=messages.getJSONObject(1).getString("content");
                evidence.put("payload",body).put("payload_bytes",sent.size()).put("payload_sha256",KnowledgeArchive.hex(java.security.MessageDigest.getInstance("SHA-256").digest(sent.toByteArray())))
                        .put("locator_only",system.contains("只定位题干与选项"))
                        .put("rag_in_payload",user.contains("<本地知识库参考资料>"))
                        .put("source_id_in_payload",user.contains("e_eval:multiple_choice:335489e946eb9f3e386f79f8"))
                        .put("caution_in_system",system.contains(KnowledgeRag.INSTRUCTION));
            }catch(Exception error){throw new IOException(error);}
        }
        @Override public int getResponseCode()throws IOException{int code=real.getResponseCode();try{evidence.put("http_status",code).put("tls_cipher",real.getCipherSuite());}catch(JSONException error){throw new IOException(error);}return code;}
        @Override public InputStream getInputStream()throws IOException {
            return new FilterInputStream(real.getInputStream()){
                @Override public int read()throws IOException{int value=in.read();if(value!=-1)received.write(value);return value;}
                @Override public int read(byte[] buffer,int offset,int length)throws IOException{int n=in.read(buffer,offset,length);if(n>0)received.write(buffer,offset,n);return n;}
                @Override public void close()throws IOException{super.close();try{JSONObject response=new JSONObject(received.toString("UTF-8"));evidence.put("server_response_received",true).put("response_id",response.optString("id")).put("response_model",response.optString("model")).put("usage",response.optJSONObject("usage"));}catch(Exception error){throw new IOException(error);}}
            };
        }
        @Override public void connect()throws IOException{real.connect();}
        @Override public void disconnect(){real.disconnect();}
        @Override public boolean usingProxy(){return real.usingProxy();}
        @Override public String getCipherSuite(){return real.getCipherSuite();}
        @Override public java.security.cert.Certificate[] getLocalCertificates(){return real.getLocalCertificates();}
        @Override public java.security.cert.Certificate[] getServerCertificates()throws SSLPeerUnverifiedException{return real.getServerCertificates();}
    }
}
