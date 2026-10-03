package cn.screenqa.lite;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Unauthenticated, bounded HTTPS request; call only from the update worker. */
final class GitHubUpdateChecker {
    static final String ENDPOINT = "https://api.github.com/repos/Cenbyte/ScreenQA/releases/latest";
    enum Status { CHECKING, AVAILABLE, CURRENT, FAILED }
    static final class Result {
        final Status status;
        final GitHubRelease release;
        Result(Status status, GitHubRelease release) { this.status = status; this.release = release; }
    }
    private volatile HttpURLConnection active;
    private volatile boolean closed;
    interface ConnectionFactory { HttpURLConnection open() throws java.io.IOException; }
    private final ConnectionFactory connections;

    GitHubUpdateChecker() { this(() -> (HttpURLConnection) new URL(ENDPOINT).openConnection()); }
    GitHubUpdateChecker(ConnectionFactory connections) { this.connections = connections; }

    Result check(String versionName, long versionCode) {
        HttpURLConnection connection = null;
        try {
            connection = connections.open();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "ScreenQA/" + versionName);
            active = connection;
            if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new java.io.IOException("Release request failed");
            long deadline = System.nanoTime() + 16_000_000_000L;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (closed || Thread.currentThread().isInterrupted()
                            || System.nanoTime() > deadline || output.size() + count > 512 * 1024)
                        throw new java.io.IOException("Release response exceeded limits");
                    output.write(buffer, 0, count);
                }
                GitHubRelease release = GitHubRelease.parse(output.toString(StandardCharsets.UTF_8.name()));
                return new Result(release.isNewer(versionName, versionCode)
                        ? Status.AVAILABLE : Status.CURRENT, release);
            }
        } catch (Exception error) {
            return new Result(Status.FAILED, null);
        } finally {
            if (connection != null) connection.disconnect();
            active = null;
        }
    }

    void close() {
        closed = true;
        HttpURLConnection connection = active;
        if (connection != null) connection.disconnect();
    }
}
