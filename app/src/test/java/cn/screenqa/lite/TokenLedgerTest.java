package cn.screenqa.lite;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TokenLedgerTest {
    private TokenUsageTracker.Usage usage(){return new TokenUsageTracker.Usage(100,20,120,40,60,0);}
    @Test public void categoriesIncludeFailuresThatConsumedTokens() throws Exception {
        TokenLedger l=new TokenLedger("");
        l.add("a","2026-10-01T16:00:00","screen_detect","choice","ocr","test-model","answered",100,usage());
        l.add("b","2026-10-01T16:00:01","local_solve","fill_blank","accessibility","test-model","parse_error",200,usage());
        JSONObject all=l.data.getJSONObject("all");assertEquals(240,all.getLong("total"));
        assertEquals(1,all.getLong("answered"));assertEquals(1,all.getLong("outcome_parse_error"));
        assertEquals(2,l.data.getJSONObject("groups").length());
        assertTrue(l.report("1","2","3").contains("0.00044"));
    }
    @Test public void unknownUsageAndCacheNeverPretendToBeZeroPrice() throws Exception {
        TokenLedger l=new TokenLedger("");
        l.add("a","2026-10-01T16:00:00","screen_detect","unknown","ocr","m","timeout",100,null);
        l.add("b","2026-10-01T16:00:01","screen_detect","choice","ocr","m","answered",100,new TokenUsageTracker.Usage(10,5,15));
        assertEquals(1,l.data.getJSONObject("all").getLong("unknown_usage"));
        assertEquals(1,l.data.getJSONObject("all").getLong("cache_unknown"));
        String csv=l.csv();assertTrue(csv.contains("timeout"));assertFalse(csv.contains("\"-1\""));
        assertTrue(l.report("1","2","3").contains("仅 0 次"));
    }
    @Test public void duplicateIdsAreNotCountedAgain() throws Exception {
        TokenLedger l=new TokenLedger("");
        for(int i=0;i<2;i++)l.add("same","2026-10-01T16:00:00","connection_test","none","ui","m","test_ok",50,usage());
        assertEquals(1,l.data.getJSONObject("all").getLong("attempts"));
        TokenLedger restored=new TokenLedger(l.data.toString());assertEquals(120,restored.data.getJSONObject("all").getLong("total"));
    }
    @Test public void boundedRowsRetainLifetimeAggregatesAndDailyBuckets() throws Exception {
        TokenLedger l=new TokenLedger("");
        for(int i=0;i<505;i++)l.add(Integer.toString(i),"2026-10-01T16:00:00","local_solve","choice","ocr","m","answered",1,usage());
        assertEquals(500,l.data.getJSONArray("recent").length());
        assertEquals(505,l.data.getJSONObject("all").getLong("attempts"));
        assertEquals(505,l.data.getJSONObject("days").getJSONObject("2026-10-01").getLong("attempts"));
    }
    @Test public void csvEscapesFormulasAndContainsOnlyMetadata() throws Exception {
        TokenLedger l=new TokenLedger("");
        l.add("a","2026-10-01T16:00:00","local_solve","choice","ocr","=unsafe","answered",1,usage());
        String csv=l.csv();assertTrue(csv.contains("'=unsafe"));assertFalse(csv.contains("prompt_text"));
        assertFalse(csv.contains("answer_text"));assertEquals(23,csv.split("\r\n")[0].split(",").length);
    }
    @Test public void rejectsInvalidRatesAndHandlesDecimals(){
        assertEquals("0.25",TokenLedger.rate("0.25").toPlainString());
        for(String v:new String[]{"-1","NaN","1000001","0.000000001"}){
            try{TokenLedger.rate(v);fail(v);}catch(IllegalArgumentException expected){}
        }
        assertEquals("—",TokenLedger.average(0,0));assertEquals("12.5",TokenLedger.average(25,2));
    }
    @Test public void parsesOptionalCacheWithoutChangingAuthoritativeTotals() throws Exception {
        TokenUsageTracker.Usage u=TokenUsageTracker.parse(new JSONObject("{\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,\"total_tokens\":123,\"prompt_cache_hit_tokens\":40,\"prompt_cache_miss_tokens\":60,\"completion_tokens_details\":{\"reasoning_tokens\":5}}}"));
        assertNotNull(u);assertEquals(123,u.total);assertEquals(40,u.cacheHit);assertEquals(60,u.cacheMiss);assertEquals(5,u.reasoning);
        u=TokenUsageTracker.parse(new JSONObject("{\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,\"total_tokens\":120,\"prompt_cache_hit_tokens\":90,\"prompt_cache_miss_tokens\":90}}"));
        assertNotNull(u);assertEquals(-1,u.cacheHit);assertEquals(-1,u.cacheMiss);
    }
    @Test public void dailyRetentionDoesNotDeleteLifetimeCosts() throws Exception {
        TokenLedger l=new TokenLedger("");
        java.time.LocalDate start=java.time.LocalDate.of(2026,8,1);
        for(int i=0;i<35;i++)l.add("day"+i,start.plusDays(i)+"T12:00:00","connection_test","none","ui","m","test_ok",10,usage());
        assertEquals(31,l.data.getJSONObject("days").length());
        assertEquals(4200,l.data.getJSONObject("all").getLong("total"));
    }
    @Test public void questionReferencesNormalizeWhitespaceAndNeverStoreQuestionText() throws Exception {
        assertEquals(ApiRequest.questionReference("单选题\n哪一个是恒星？"),ApiRequest.questionReference("单选题 哪一个是恒星？"));
        String ref=ApiRequest.questionReference("单选题哪一个是恒星？");assertEquals(64,ref.length());
        TokenLedger l=new TokenLedger("");l.add("a","2026-10-01T16:00:00","local_solve","choice","ocr","m","answered",1,usage(),ref,2);
        assertEquals(1,l.data.getJSONObject("all").getLong("retry_requests"));
        assertTrue(l.csv().contains(ref));assertFalse(l.csv().contains("恒星"));
    }
}
