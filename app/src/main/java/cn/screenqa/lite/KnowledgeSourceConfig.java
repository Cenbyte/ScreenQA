package cn.screenqa.lite;

import java.net.URI;
import java.util.Locale;

/** Public share settings, unrelated to API credentials. Change the source here only. */
final class KnowledgeSourceConfig {
    static final String URL = "https://wwapn.lanzoul.com/b01gid4pdc";
    static final String EXTRACTION_CODE = "6666";
    static final long MAX_DOWNLOAD_BYTES = 2L * 1024 * 1024 * 1024;

    static boolean isPasswordPageOrigin(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.matches("(?:[a-z0-9-]+\\.)*(?:lanzou[a-z]?|lanzov|lanzn|lanznx)\\.(?:com|cn)");
        } catch (IllegalArgumentException e) { return false; }
    }
    private KnowledgeSourceConfig() { }
}
