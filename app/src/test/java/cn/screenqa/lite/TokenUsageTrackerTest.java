package cn.screenqa.lite;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;

public final class TokenUsageTrackerTest {
    @Test public void readsThreeCountsFromDeepSeekUsage() throws Exception {
        JSONObject response=new JSONObject("{\"choices\":[{\"message\":{\"content\":\"OK\"}}],"+
                "\"usage\":{\"prompt_tokens\":86,\"completion_tokens\":14,\"total_tokens\":101}}");
        TokenUsageTracker.Usage usage=TokenUsageTracker.parse(response);
        assertNotNull(usage);
        assertEquals(86,usage.prompt);
        assertEquals(14,usage.completion);
        assertEquals(101,usage.total); // Trust the API's total rather than recalculating it.
    }

    @Test public void neverEstimatesMissingOrInvalidUsage() throws Exception {
        assertNull(TokenUsageTracker.parse(new JSONObject("{\"choices\":[]}")));
        assertNull(TokenUsageTracker.parse(new JSONObject("{\"usage\":{\"prompt_tokens\":4,"+
                "\"completion_tokens\":2}}")));
        assertNull(TokenUsageTracker.parse(new JSONObject("{\"usage\":{\"prompt_tokens\":\"4\","+
                "\"completion_tokens\":2,\"total_tokens\":6}}")));
        assertNull(TokenUsageTracker.parse(new JSONObject("{\"usage\":{\"prompt_tokens\":-1,"+
                "\"completion_tokens\":2,\"total_tokens\":1}}")));
    }

    @Test public void averageUsesCompletedRequestCount() {
        TokenUsageTracker.Snapshot snapshot=new TokenUsageTracker.Snapshot(
                5,5,10,20,2,25,2,1);
        assertEquals("12.5",snapshot.averageTokens());
    }
}
