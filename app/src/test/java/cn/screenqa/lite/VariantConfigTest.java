package cn.screenqa.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class VariantConfigTest {
    @Test public void previewPackagesStaySeparate() {
        assertEquals(BuildConfig.DEVELOPER_BUILD ? "cn.screenqa.lite.dev" : "cn.screenqa.lite",
                BuildConfig.APPLICATION_ID);
    }
    @Test public void rootAndDiagnosticsAreAvailableInBothAudiences() {
        assertTrue(BuildConfig.DIAGNOSTICS_ENABLED);
        assertTrue(BuildConfig.ROOT_SUPPORTED);
        assertEquals("developer".equals(BuildConfig.FLAVOR), BuildConfig.DEVELOPER_BUILD);
        if("release".equals(BuildConfig.BUILD_TYPE)) {
            assertFalse(BuildConfig.DEBUG);
            assertFalse(BuildConfig.DEVELOPER_BUILD);
            assertTrue(BuildConfig.DIAGNOSTICS_ENABLED);
            assertTrue(BuildConfig.ROOT_SUPPORTED);
        }
    }
}
