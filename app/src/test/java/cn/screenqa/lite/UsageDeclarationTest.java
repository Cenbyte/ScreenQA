package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UsageDeclarationTest {
    @Test public void freshAndOldAcknowledgementsRequireAcceptance() {
        assertFalse(UsageDeclaration.isAccepted(0));
        assertFalse(UsageDeclaration.isAccepted(UsageDeclaration.REVISION-1));
    }
    @Test public void onlyCurrentDocumentRevisionIsAccepted() {
        assertTrue(UsageDeclaration.isAccepted(UsageDeclaration.REVISION));
        assertFalse(UsageDeclaration.isAccepted(UsageDeclaration.REVISION+1));
    }
}
