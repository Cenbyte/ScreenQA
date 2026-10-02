package cn.screenqa.lite;

import android.content.Context;
import android.content.SharedPreferences;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.json.JSONObject;

/** Actual API token observations. Never contains prompts, answers, or API keys. */
final class TokenUsageTracker {
    private static final String PREFS = "developer_token_usage_v1";
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;

    TokenUsageTracker(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static final class Usage {
        final long prompt, completion, total;
        final long cacheHit,cacheMiss,reasoning;
        Usage(long prompt, long completion, long total) {
            this(prompt,completion,total,-1,-1,-1);
        }
        Usage(long prompt,long completion,long total,long hit,long miss,long reasoning){
            this.prompt=prompt;this.completion=completion;this.total=total;
            this.cacheHit=hit;this.cacheMiss=miss;this.reasoning=reasoning;
        }
    }

    static Usage parse(JSONObject response) {
        if (response == null) return null;
        JSONObject usage = response.optJSONObject("usage");
        if (usage == null) return null;
        try {
            long prompt = exactNonnegative(usage.opt("prompt_tokens"));
            long completion = exactNonnegative(usage.opt("completion_tokens"));
            long total = exactNonnegative(usage.opt("total_tokens"));
            long hit=optional(usage,"prompt_cache_hit_tokens"),miss=optional(usage,"prompt_cache_miss_tokens");
            if(hit<0||miss<0||hit>prompt||miss>prompt||hit!=prompt-miss){hit=-1;miss=-1;}
            JSONObject details=usage.optJSONObject("completion_tokens_details");
            long reasoning=details==null?-1:optional(details,"reasoning_tokens");
            if(reasoning>completion)reasoning=-1;
            return new Usage(prompt, completion, total,hit,miss,reasoning);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    private static long optional(JSONObject value,String key){
        try{return exactNonnegative(value.opt(key));}catch(IllegalArgumentException e){return -1;}
    }
    void recordDetail(String id,String stage,String type,String source,String model,String outcome,long elapsed,Usage usage,String question,int attempt) throws Exception {
        if(!BuildConfig.DEVELOPER_BUILD)return;
        synchronized(LOCK){
            TokenLedger ledger=new TokenLedger(prefs.getString("classified_v2",""));
            ledger.add(id,java.time.LocalDateTime.now().toString(),stage,type,source,model,outcome,elapsed,usage,question,attempt);
            if(!prefs.edit().putString("classified_v2",ledger.data.toString()).commit())throw new IllegalStateException("usage detail persistence failed");
        }
        QaLog.event("DEEPSEEK_COST request_id="+id+" stage="+stage+" type="+type+" source="+source+
                " model="+model+" outcome="+outcome+" question_ref="+question+" question_attempt="+attempt+" elapsed_ms="+elapsed+
                " input="+(usage==null?"unknown":usage.prompt)+" output="+(usage==null?"unknown":usage.completion)+
                " cache_hit="+(usage==null?"unknown":usage.cacheHit)+" cache_miss="+(usage==null?"unknown":usage.cacheMiss));
    }
    String detailedReport(){
        if(!BuildConfig.DEVELOPER_BUILD)return "";
        synchronized(LOCK){try{return new TokenLedger(prefs.getString("classified_v2","")).report(
                prefs.getString("rate_hit",""),prefs.getString("rate_miss",""),prefs.getString("rate_output",""));}
            catch(Exception e){return "分类记录读取失败，请导出诊断日志。";}}
    }
    void saveRates(String hit,String miss,String output){
        if(!BuildConfig.DEVELOPER_BUILD)return;
        TokenLedger.rate(hit);TokenLedger.rate(miss);TokenLedger.rate(output);
        if(!prefs.edit().putString("rate_hit",hit).putString("rate_miss",miss).putString("rate_output",output).commit())throw new IllegalStateException("rate persistence failed");
    }
    String rateValue(String key){return prefs.getString("rate_"+key,"");}
    String exportCsv() throws Exception {
        if(!BuildConfig.DEVELOPER_BUILD)return "";
        synchronized(LOCK){return new TokenLedger(prefs.getString("classified_v2","")).csv();}
    }

    private static long exactNonnegative(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("missing token count");
        try {
            long count = new BigDecimal(value.toString()).longValueExact();
            if (count < 0) throw new IllegalArgumentException("negative token count");
            return count;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("invalid token count", e);
        }
    }

    void record(Usage usage) {
        String day = LocalDate.now().toString();
        synchronized (LOCK) {
            boolean sameDay = day.equals(prefs.getString("today_date", ""));
            long todayTokens = sameDay ? prefs.getLong("today_tokens", 0) : 0;
            long todayRequests = sameDay ? prefs.getLong("today_requests", 0) : 0;
            boolean saved = prefs.edit()
                    .putString("today_date", day)
                    .putLong("last_prompt", usage.prompt)
                    .putLong("last_completion", usage.completion)
                    .putLong("last_total", usage.total)
                    .putLong("today_tokens", Math.addExact(todayTokens, usage.total))
                    .putLong("today_requests", Math.addExact(todayRequests, 1))
                    .putLong("history_tokens", Math.addExact(prefs.getLong("history_tokens", 0), usage.total))
                    .putLong("history_requests", Math.addExact(prefs.getLong("history_requests", 0), 1))
                    .commit();
            if (!saved) throw new IllegalStateException("token usage persistence failed");
        }
        QaLog.event("DEEPSEEK_USAGE prompt_tokens="+usage.prompt+
                " completion_tokens="+usage.completion+" total_tokens="+usage.total);
    }

    void recordMissing() {
        synchronized (LOCK) {
            boolean saved=prefs.edit().putLong("missing_usage_responses",
                    Math.addExact(prefs.getLong("missing_usage_responses", 0), 1)).commit();
            if(!saved)throw new IllegalStateException("missing usage persistence failed");
        }
        QaLog.event("DEEPSEEK_USAGE missing_or_invalid");
    }

    Snapshot snapshot() {
        synchronized (LOCK) {
            boolean sameDay = LocalDate.now().toString().equals(prefs.getString("today_date", ""));
            return new Snapshot(prefs.getLong("last_prompt",0),prefs.getLong("last_completion",0),
                    prefs.getLong("last_total",0),sameDay?prefs.getLong("today_tokens",0):0,
                    sameDay?prefs.getLong("today_requests",0):0,prefs.getLong("history_tokens",0),
                    prefs.getLong("history_requests",0),prefs.getLong("missing_usage_responses",0));
        }
    }

    static final class Snapshot {
        final long lastPrompt,lastCompletion,lastTotal,todayTokens,todayRequests;
        final long historyTokens,historyRequests,missingUsageResponses;
        Snapshot(long prompt,long completion,long total,long today,long todayRequests,
                 long history,long historyRequests,long missing) {
            lastPrompt=prompt;lastCompletion=completion;lastTotal=total;
            todayTokens=today;this.todayRequests=todayRequests;
            historyTokens=history;this.historyRequests=historyRequests;missingUsageResponses=missing;
        }
        String averageTokens() {
            if (historyRequests == 0) return "0";
            return BigDecimal.valueOf(historyTokens).divide(BigDecimal.valueOf(historyRequests),
                    1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
    }
}
