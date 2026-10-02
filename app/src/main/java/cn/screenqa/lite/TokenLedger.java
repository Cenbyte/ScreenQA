package cn.screenqa.lite;

import org.json.*;
import java.math.*;
import java.util.*;

/** Bounded, content-free pricing observations. Unknown usage/cache is never invented. */
final class TokenLedger {
    final JSONObject data;
    TokenLedger(String saved) throws JSONException {
        data=saved.isEmpty()?new JSONObject():new JSONObject(saved);
    }
    void add(String id,String timestamp,String stage,String type,String source,String model,String outcome,
            long elapsed,TokenUsageTracker.Usage usage) throws JSONException {
        add(id,timestamp,stage,type,source,model,outcome,elapsed,usage,"",1);
    }
    void add(String id,String timestamp,String stage,String type,String source,String model,String outcome,
            long elapsed,TokenUsageTracker.Usage usage,String question,int attempt) throws JSONException {
        JSONArray recent=data.optJSONArray("recent");if(recent==null)recent=new JSONArray();
        for(int i=0;i<recent.length();i++)if(id.equals(recent.getJSONObject(i).getString("id")))return;
        JSONObject row=new JSONObject().put("id",id).put("time",timestamp).put("stage",stage)
                .put("type",type).put("source",source).put("model",model).put("outcome",outcome).put("ms",elapsed)
                .put("question_ref",question).put("question_attempt",attempt);
        if(usage!=null)row.put("input",usage.prompt).put("output",usage.completion).put("total",usage.total)
                .put("hit",usage.cacheHit).put("miss",usage.cacheMiss).put("reasoning",usage.reasoning);
        recent.put(row);JSONArray bounded=new JSONArray();
        for(int i=Math.max(0,recent.length()-500);i<recent.length();i++)bounded.put(recent.get(i));
        data.put("recent",bounded);
        if(!data.has("since"))data.put("since",timestamp);
        accumulate(object(data,"all"),row);
        JSONObject groups=object(data,"groups");
        accumulate(object(groups,stage+" / "+type+" / "+source+" / "+model),row);
        String day=timestamp.substring(0,10);
        JSONObject days=object(data,"days");accumulate(object(days,day),row);
        List<String> names=new ArrayList<>();days.keys().forEachRemaining(names::add);Collections.sort(names);
        for(int i=0;i<names.size()-31;i++)days.remove(names.get(i));
    }
    private static JSONObject object(JSONObject parent,String key) throws JSONException {
        JSONObject value=parent.optJSONObject(key);if(value==null){value=new JSONObject();parent.put(key,value);}return value;
    }
    private static void plus(JSONObject a,String key,long value) throws JSONException {
        a.put(key,Math.addExact(a.optLong(key),value));
    }
    private static void accumulate(JSONObject a,JSONObject row) throws JSONException {
        plus(a,"attempts",1);plus(a,"ms",row.getLong("ms"));
        plus(a,"outcome_"+row.getString("outcome"),1);
        if(row.optInt("question_attempt",1)>1)plus(a,"retry_requests",1);
        if("answered".equals(row.getString("outcome")))plus(a,"answered",1);
        if("test_ok".equals(row.getString("outcome")))plus(a,"tests",1);
        if(!row.has("total")){plus(a,"unknown_usage",1);return;}
        plus(a,"known",1);
        for(String key:new String[]{"input","output","total"})plus(a,key,row.getLong(key));
        if(row.getLong("hit")>=0&&row.getLong("miss")>=0){
            plus(a,"cache_known",1);plus(a,"hit",row.getLong("hit"));plus(a,"miss",row.getLong("miss"));
            plus(a,"priced_output",row.getLong("output"));
        }else plus(a,"cache_unknown",1);
        if(row.getLong("reasoning")>=0)plus(a,"reasoning",row.getLong("reasoning"));
    }
    static String average(long numerator,long denominator){
        return denominator==0?"—":BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator),1,RoundingMode.HALF_UP).toPlainString();
    }
    static BigDecimal rate(String value){
        BigDecimal rate=new BigDecimal(value.trim());
        if(rate.signum()<0||rate.compareTo(new BigDecimal("1000000"))>0||rate.scale()>8)throw new IllegalArgumentException("invalid rate");
        return rate;
    }
    String report(String hitRate,String missRate,String outputRate) {
        JSONObject all=data.optJSONObject("all");if(all==null)return "尚无分类记录；旧版本统计仍保留在上方。";
        StringBuilder out=new StringBuilder("分类记录起始：").append(data.optString("since")).append('\n');
        out.append(summary(all));
        if(!hitRate.isEmpty()&&!missRate.isEmpty()&&!outputRate.isEmpty()){
            BigDecimal cost=rate(hitRate).multiply(BigDecimal.valueOf(all.optLong("hit")))
                    .add(rate(missRate).multiply(BigDecimal.valueOf(all.optLong("miss"))))
                    .add(rate(outputRate).multiply(BigDecimal.valueOf(all.optLong("priced_output"))))
                    .divide(new BigDecimal("1000000"),8,RoundingMode.HALF_UP);
            out.append("\n自设单价预算：").append(cost.stripTrailingZeros().toPlainString())
                    .append("（仅 ").append(all.optLong("cache_known")).append(" 次缓存数据完整的请求；统一自设单价，非账单）\n");
        }
        JSONObject days=data.optJSONObject("days");
        if(days!=null){List<String> dates=new ArrayList<>();days.keys().forEachRemaining(dates::add);Collections.sort(dates);
            out.append("\n每日用量（最近31天）\n");for(String date:dates)out.append(date).append("：").append(summary(days.optJSONObject(date))).append('\n');}
        JSONObject groups=data.optJSONObject("groups");
        if(groups!=null){List<String> keys=new ArrayList<>();groups.keys().forEachRemaining(keys::add);Collections.sort(keys);
            out.append("\n分类：阶段 / 题型 / 来源 / API模型\n");for(String key:keys)out.append(key).append('\n').append(summary(groups.optJSONObject(key))).append("\n\n");}
        return out.toString();
    }
    private static String summary(JSONObject a){
        return "请求尝试 "+a.optLong("attempts")+"，有效答案请求 "+a.optLong("answered")+"，连接成功 "+a.optLong("tests")+"，重试请求 "+a.optLong("retry_requests")+
                "\n已知输入 "+a.optLong("input")+" / 输出 "+a.optLong("output")+" / 总量 "+a.optLong("total")+
                "\n缓存命中 "+a.optLong("hit")+" / 未命中 "+a.optLong("miss")+" / 缓存未知请求 "+a.optLong("cache_unknown")+
                "\n用量未知请求 "+a.optLong("unknown_usage")+"；已知用量请求均值 "+average(a.optLong("total"),a.optLong("known"))+
                "；平均耗时 "+average(a.optLong("ms"),a.optLong("attempts"))+" ms"+
                "\n结果分类："+outcomes(a);
    }
    private static String outcomes(JSONObject a){
        List<String> keys=new ArrayList<>();a.keys().forEachRemaining(k->{if(k.startsWith("outcome_"))keys.add(k);});
        Collections.sort(keys);StringBuilder out=new StringBuilder();
        for(String key:keys)out.append(key.substring(8)).append('=').append(a.optLong(key)).append(' ');return out.toString();
    }
    String csv() throws JSONException {
        StringBuilder out=new StringBuilder("record_kind,id_or_group,time,stage,type,source,model,outcome,elapsed_ms,input_tokens,output_tokens,total_tokens,cache_hit_tokens,cache_miss_tokens,reasoning_tokens,question_ref,question_attempt,attempts,usable_answers,known_usage_requests,unknown_usage_requests,unknown_cache_requests,outcome_counts\r\n");
        JSONArray rows=data.optJSONArray("recent");
        if(rows!=null)for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);List<String> values=new ArrayList<>();values.add("request");
            for(String k:new String[]{"id","time","stage","type","source","model","outcome","ms","input","output","total","hit","miss","reasoning","question_ref","question_attempt"}){
                Object v=r.opt(k);values.add(v==null||JSONObject.NULL.equals(v)||v.toString().equals("-1")?"":v.toString());}
            for(int n=0;n<6;n++)values.add("");row(out,values);
        }
        JSONObject groups=data.optJSONObject("groups");
        if(groups!=null){List<String> keys=new ArrayList<>();groups.keys().forEachRemaining(keys::add);Collections.sort(keys);
            for(String key:keys){JSONObject a=groups.getJSONObject(key);List<String> values=new ArrayList<>(Arrays.asList("aggregate",key,"","","","","",""));
                for(String k:new String[]{"ms","input","output","total","hit","miss","reasoning"})values.add(Long.toString(a.optLong(k)));
                values.add("");values.add("");
                for(String k:new String[]{"attempts","answered","known","unknown_usage","cache_unknown"})values.add(Long.toString(a.optLong(k)));
                values.add(outcomes(a));row(out,values);}}
        return out.toString();
    }
    private static void row(StringBuilder out,List<String> values){
        for(int i=0;i<values.size();i++){
            if(i>0)out.append(',');String v=values.get(i);
            if(v.matches("^[=+@-].*"))v="'"+v; // Spreadsheet formula injection protection.
            out.append('"').append(v.replace("\"","\"\"")).append('"');
        }out.append("\r\n");
    }
}
