package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DiagnosticsConfigTest {
    @Test public void userRecordingRequiresOptInWhileDeveloperKeepsItsDefault() {
        assertEquals("developer".equals(BuildConfig.FLAVOR),QaLog.defaultRecording());
        if("user".equals(BuildConfig.FLAVOR))assertFalse(QaLog.defaultRecording());
    }
    @Test public void releaseBuildKeepsRootAndLogCapabilitiesWithoutDeveloperIdentity() {
        assertTrue(BuildConfig.ROOT_SUPPORTED);
        assertTrue(BuildConfig.DIAGNOSTICS_ENABLED);
        if("release".equals(BuildConfig.BUILD_TYPE)) {
            assertFalse(BuildConfig.DEBUG);
            assertFalse(BuildConfig.DEVELOPER_BUILD);
            assertFalse(QaLog.defaultRecording());
        }
    }
}
