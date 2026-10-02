package cn.screenqa.lite;

/** Explicitly unauthenticated preview state; no credentials or fake login are persisted. */
final class PreviewAccountGateway implements AccountGateway {
    @Override public AccountState currentState() {
        return BuildConfig.DEVELOPER_BUILD
                ? new AccountState("本地开发者账户",false,"开发者本机身份 · 未连接云端")
                : new AccountState("访客 · 本地预览",false,"账号系统接入准备中");
    }
}
