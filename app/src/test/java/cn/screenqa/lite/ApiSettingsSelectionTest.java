package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class ApiSettingsSelectionTest {
    private static final String URL = "https://api.deepseek.com/chat/completions";

    @Test public void customChoiceRemainsEditableWithDefaultEffectiveSettings() {
        ApiSettingsSelection state = new ApiSettingsSelection("deepseek-flash", URL);
        state.selectModel("");
        state.selectEndpoint("");
        assertTrue(state.customModel);
        assertTrue(state.customEndpoint);
        assertEquals("my-model", state.modelValue(" my-model ", "deepseek-flash"));
        assertEquals("https://example.com/v1/chat/completions",
                state.endpointValue(" https://example.com/v1/chat/completions ", URL));
    }

    @Test public void savedCustomConfigurationReopensInCustomMode() {
        ApiSettingsSelection state = new ApiSettingsSelection("my-model", "https://example.com/chat/completions");
        assertTrue(state.customModel);
        assertTrue(state.customEndpoint);
    }

    @Test public void presetSelectionIgnoresStaleCustomText() {
        ApiSettingsSelection state = new ApiSettingsSelection("my-model", "https://example.com/chat/completions");
        state.selectModel("deepseek-chat");
        state.selectEndpoint(URL);
        assertFalse(state.customModel);
        assertFalse(state.customEndpoint);
        assertEquals("deepseek-chat", state.modelValue("old-model", "deepseek-chat"));
        assertEquals(URL, state.endpointValue("https://old.example.com", URL));
    }

    @Test(expected = IllegalArgumentException.class) public void emptyCustomModelDoesNotSilentlyUseDefault() {
        ApiSettingsSelection state = new ApiSettingsSelection("deepseek-flash", URL);
        state.selectModel("");
        state.modelValue("  ", "deepseek-flash");
    }

    @Test(expected = IllegalArgumentException.class) public void emptyCustomEndpointDoesNotSilentlyUseDefault() {
        ApiSettingsSelection state = new ApiSettingsSelection("deepseek-flash", URL);
        state.selectEndpoint("");
        state.endpointValue("  ", URL);
    }

    @Test public void customEndpointRejectsCredentialsAndInsecureUrls() {
        ApiSettingsSelection state = new ApiSettingsSelection("my-model", "https://example.com");
        for (String url : new String[]{"http://example.com", "https://user:pass@example.com", "https://example.com?key=test", "https://example.com#key"}) {
            try {
                state.endpointValue(url, URL);
                fail("Unsafe endpoint accepted");
            } catch (IllegalArgumentException expected) { /* Nothing was persisted. */ }
        }
    }
}
