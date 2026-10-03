package cn.screenqa.lite;

import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public final class GitHubUpdateCheckerTest {
    private String release(String tag, String body) throws Exception {
        return new JSONObject().put("tag_name", tag).put("body", body)
                .put("draft", false).put("prerelease", false)
                .put("html_url", "https://github.com/Cenbyte/ScreenQA/releases/tag/" + tag).toString();
    }
    private GitHubRelease parsed(String tag, String body) throws Exception {
        return GitHubRelease.parse(release(tag, body));
    }
    @Test public void comparesVersionPartsNumerically() throws Exception {
        assertTrue(parsed("v1.10.0", "").isNewer("1.9.0", 27));
        assertFalse(parsed("v1.2.0", "").isNewer("1.10.0", 28));
        assertTrue(parsed("v2.0.0", "").isNewer("1.99.99", 27));
    }
    @Test public void currentDeveloperBuildDoesNotBecomeAnUpdate() throws Exception {
        assertFalse(parsed("v1.2.0", "").isNewer("1.2.0-dev", 27));
        assertFalse(parsed("1.2.0", "versionCode: 27").isNewer("1.2.0-dev", 27));
    }
    @Test public void supportsHigherCodeWithinSameName() throws Exception {
        assertTrue(parsed("1.2.0", "Notes\n- versionCode: 28\n").isNewer("1.2.0-dev", 27));
        assertFalse(parsed("1.2.0", "versionCode = 26").isNewer("1.2.0", 27));
    }
    @Test public void rejectsContradictoryNameAndCode() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> parsed("1.3.0", "versionCode: 27").isNewer("1.2.0", 27));
        assertThrows(IllegalArgumentException.class, () -> parsed("1.1.0", "versionCode: 28").isNewer("1.2.0", 27));
    }
    @Test public void ignoresBuildMetadataAndHandlesLargeParts() throws Exception {
        assertEquals("1.2.0", GitHubRelease.normalize("v1.2.0+build.28"));
        assertTrue(parsed("999999999999999999.0.0", "").isNewer("1.2.0", 27));
    }
    @Test public void refusesDraftPrereleaseAndUnknownTags() throws Exception {
        JSONObject draft = new JSONObject(release("v1.3.0", "")).put("draft", true);
        JSONObject beta = new JSONObject(release("v1.3.0", "")).put("prerelease", true);
        assertThrows(IllegalArgumentException.class, () -> GitHubRelease.parse(draft.toString()));
        assertThrows(IllegalArgumentException.class, () -> GitHubRelease.parse(beta.toString()));
        for (String tag : new String[]{"v1.3.0-beta", "latest", "v1.3", "release-1.3.0"})
            assertThrows(IllegalArgumentException.class, () -> parsed(tag, ""));
    }
    @Test public void refusesMalformedCodes() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> parsed("1.3.0", "versionCode: 0"));
        assertThrows(IllegalArgumentException.class, () -> parsed("1.3.0", "versionCode: 28\nversionCode: 29"));
        assertThrows(NumberFormatException.class, () -> parsed("1.3.0", "versionCode: 9999999999999999999999"));
    }
    @Test public void releaseLinkMustBelongToThisRepository() throws Exception {
        for (String url : new String[]{"http://github.com/Cenbyte/ScreenQA/releases/tag/v1.3.0",
                "https://evil.example/Cenbyte/ScreenQA/releases/tag/v1.3.0",
                "https://github.com/other/repo/releases/tag/v1.3.0",
                "https://github.com/Cenbyte/ScreenQA/releases/tag/",
                "https://github.com/Cenbyte/ScreenQA/releases/tag/v1.3.0?next=other"}) {
            String json = new JSONObject(release("v1.3.0", "")).put("html_url", url).toString();
            assertThrows(IllegalArgumentException.class, () -> GitHubRelease.parse(json));
        }
    }
    @Test public void requestReturnsAvailableOrCurrentAndDisconnects() throws Exception {
        FakeConnection newer = new FakeConnection(200, release("v1.3.0", "versionCode: 28"));
        GitHubUpdateChecker checker = new GitHubUpdateChecker(() -> newer);
        assertEquals(GitHubUpdateChecker.Status.AVAILABLE, checker.check("1.2.0-dev", 27).status);
        assertTrue(newer.disconnected);
        assertEquals(8000, newer.getConnectTimeout());
        assertEquals(8000, newer.getReadTimeout());
        assertFalse(newer.getUseCaches());
        assertFalse(newer.getInstanceFollowRedirects());
        assertNull(newer.getRequestProperty("Authorization"));
        FakeConnection current = new FakeConnection(200, release("v1.2.0", ""));
        assertEquals(GitHubUpdateChecker.Status.CURRENT,
                new GitHubUpdateChecker(() -> current).check("1.2.0", 27).status);
    }
    @Test public void httpAndParseFailuresNeverClaimLatest() throws Exception {
        for (int status : new int[]{403, 404, 429, 500, 302}) {
            FakeConnection connection = new FakeConnection(status, "{}");
            assertEquals(GitHubUpdateChecker.Status.FAILED,
                    new GitHubUpdateChecker(() -> connection).check("1.2.0", 27).status);
            assertTrue(connection.disconnected);
        }
        for (String body : new String[]{"not json", "{}", release("v1.3.0-beta", "")}) {
            FakeConnection connection = new FakeConnection(200, body);
            assertEquals(GitHubUpdateChecker.Status.FAILED,
                    new GitHubUpdateChecker(() -> connection).check("1.2.0", 27).status);
        }
    }
    @Test public void networkTimeoutIsRecoverableByRetry() throws Exception {
        final int[] attempts = {0};
        FakeConnection current = new FakeConnection(200, release("1.2.0", ""));
        GitHubUpdateChecker checker = new GitHubUpdateChecker(() -> {
            if (++attempts[0] == 1) throw new SocketTimeoutException();
            return current;
        });
        assertEquals(GitHubUpdateChecker.Status.FAILED, checker.check("1.2.0", 27).status);
        assertEquals(GitHubUpdateChecker.Status.CURRENT, checker.check("1.2.0", 27).status);
    }
    @Test public void boundedResponsesAndClosedActivityFailSafely() throws Exception {
        FakeConnection huge = new FakeConnection(200, new String(new char[513 * 1024]));
        assertEquals(GitHubUpdateChecker.Status.FAILED,
                new GitHubUpdateChecker(() -> huge).check("1.2.0", 27).status);
        assertTrue(huge.disconnected);
        FakeConnection connection = new FakeConnection(200, release("1.3.0", ""));
        GitHubUpdateChecker checker = new GitHubUpdateChecker(() -> connection);
        checker.close();
        assertEquals(GitHubUpdateChecker.Status.FAILED, checker.check("1.2.0", 27).status);
        assertTrue(connection.disconnected);
    }
    private static final class FakeConnection extends HttpURLConnection {
        final int status; final String body; boolean disconnected;
        FakeConnection(int status, String body) throws Exception {
            super(new URL(GitHubUpdateChecker.ENDPOINT));this.status = status;this.body = body;
        }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
