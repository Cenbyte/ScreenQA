package cn.screenqa.lite;

/** Editing mode must not be inferred from Settings' effective default values. */
final class ApiSettingsSelection {
    boolean customModel, customEndpoint;

    ApiSettingsSelection(String model, String endpoint) {
        customModel = true;
        customEndpoint = true;
        for (ModelCatalog.Model preset : ModelCatalog.MODELS)
            if (!preset.id.isEmpty() && preset.id.equalsIgnoreCase(model)) customModel = false;
        for (ModelCatalog.Endpoint preset : ModelCatalog.ENDPOINTS)
            if (!preset.url.isEmpty() && preset.url.equalsIgnoreCase(endpoint)) customEndpoint = false;
    }

    void selectModel(String id) { customModel = id.isEmpty(); }
    void selectEndpoint(String url) { customEndpoint = url.isEmpty(); }

    String modelValue(String input, String saved) {
        if (!customModel) return saved;
        String value = input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("请填写自定义模型 ID");
        return value;
    }

    String endpointValue(String input, String saved) {
        if (!customEndpoint) return saved;
        String value = input.trim();
        if (!ModelCatalog.validEndpoint(value))
            throw new IllegalArgumentException("请填写有效的 https 接口地址，不能含账号、查询参数或片段");
        return value;
    }
}
