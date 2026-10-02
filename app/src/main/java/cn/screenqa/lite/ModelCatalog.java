package cn.screenqa.lite;

import java.net.URI;

/** Presets and validation for the two settings that used to be compile-time constants. */
final class ModelCatalog {
    private ModelCatalog() {}

    static final class Endpoint {
        final String label, url, note;
        Endpoint(String label, String url, String note) { this.label = label; this.url = url; this.note = note; }
    }

    static final class Model {
        final String label, id, note;
        final boolean thinking;
        Model(String label, String id, String note, boolean thinking) {
            this.label = label; this.id = id; this.note = note; this.thinking = thinking;
        }
    }

    static final Endpoint[] ENDPOINTS = {
            new Endpoint("DeepSeek 官方", "https://api.deepseek.com/chat/completions", "官方 Chat Completions 接口"),
            new Endpoint("DeepSeek 官方 v1", "https://api.deepseek.com/v1/chat/completions", "OpenAI 兼容路径，参数一致"),
            new Endpoint("自定义接口", "", "自行填写任意 OpenAI 兼容的 chat/completions 地址"),
    };

    static final Model[] MODELS = {
            new Model("deepseek-flash", "deepseek-flash", "快速模式，默认；延迟最低", false),
            new Model("deepseek-chat", "deepseek-chat", "通用对话模型", false),
            new Model("deepseek-reasoner", "deepseek-reasoner", "推理模型，需要开启思考模式", true),
            new Model("自定义模型", "", "自行填写模型 ID，由接口决定是否可用", false),
    };

    /** Accepts only https URLs without credentials; http stays blocked by the manifest too. */
    static boolean validEndpoint(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;
        try {
            URI uri = URI.create(trimmed);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean official(String value) {
        if (value == null) return false;
        try {
            String host = URI.create(value.trim()).getHost();
            return host != null && host.toLowerCase(java.util.Locale.ROOT).endsWith("deepseek.com");
        } catch (Exception e) {
            return false;
        }
    }

    static String endpointLabel(String url) {
        for (Endpoint endpoint : ENDPOINTS) if (!endpoint.url.isEmpty() && endpoint.url.equalsIgnoreCase(url)) return endpoint.label;
        return official(url) ? "自定义 DeepSeek 地址" : "自定义接口";
    }

    static String modelLabel(String id) {
        for (Model model : MODELS) if (!model.id.isEmpty() && model.id.equals(id)) return model.label;
        return id == null || id.isEmpty() ? "未设置" : id;
    }

    /** Reasoner-class models only answer with thinking enabled; the UI uses this as a hint. */
    static boolean prefersThinking(String id) {
        return id != null && id.toLowerCase(java.util.Locale.ROOT).contains("reason");
    }
}
