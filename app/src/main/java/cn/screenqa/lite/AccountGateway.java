package cn.screenqa.lite;

/** Read-only shell for the future authenticated account provider. */
interface AccountGateway {
    AccountState currentState();
    record AccountState(String displayName,boolean authenticated,String description) {}
}
