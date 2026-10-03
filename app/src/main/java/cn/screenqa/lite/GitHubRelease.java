package cn.screenqa.lite;

import org.json.JSONObject;
import java.math.BigInteger;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GitHub has no Android versionCode field; an optional release-body marker supplies it. */
final class GitHubRelease {
    private static final Pattern VERSION = Pattern.compile("^[vV]?(\\d+\\.\\d+\\.\\d+)(?:\\+[-A-Za-z0-9.]+)?$");
    private static final Pattern CODE = Pattern.compile(
            "(?im)^[ \\t]*(?:[-*][ \\t]*)?versionCode[ \\t]*[:=][ \\t]*([^\\r\\n]*)$");
    final String version, url;
    final Long versionCode;

    private GitHubRelease(String version, String url, Long versionCode) {
        this.version = version; this.url = url; this.versionCode = versionCode;
    }

    static GitHubRelease parse(String json) throws Exception {
        JSONObject release = new JSONObject(json);
        if (release.getBoolean("draft") || release.getBoolean("prerelease"))
            throw new IllegalArgumentException("Not a stable release");
        String version = normalize(release.getString("tag_name"));
        String url = release.getString("html_url");
        URI uri = new URI(url);
        if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !uri.getRawPath().startsWith("/Cenbyte/ScreenQA/releases/tag/")
                || uri.getRawPath().length() <= "/Cenbyte/ScreenQA/releases/tag/".length()
                || uri.getRawPath().contains("/../"))
            throw new IllegalArgumentException("Invalid release URL");
        Matcher marker = CODE.matcher(release.optString("body", ""));
        Long code = null;
        if (marker.find()) {
            code = Long.parseLong(marker.group(1).trim());
            if (code <= 0 || marker.find()) throw new IllegalArgumentException("Invalid versionCode");
        }
        return new GitHubRelease(version, url, code);
    }

    static String normalize(String value) {
        Matcher matcher = VERSION.matcher(value.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Unrecognized stable version");
        return matcher.group(1);
    }

    boolean isNewer(String installedName, long installedCode) {
        // The developer flavor is the same source version, not a published prerelease.
        String current = normalize(installedName.replaceFirst("-dev$", ""));
        int comparison = compare(version, current);
        if (versionCode != null) {
            // Never offer a downgrade, or an APK Android cannot install over this build.
            if (comparison > 0 && versionCode <= installedCode
                    || comparison < 0 && versionCode > installedCode)
                throw new IllegalArgumentException("Inconsistent release version");
            return versionCode > installedCode;
        }
        return comparison > 0;
    }

    private static int compare(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int comparison = new BigInteger(a[i]).compareTo(new BigInteger(b[i]));
            if (comparison != 0) return comparison;
        }
        return 0;
    }
}
