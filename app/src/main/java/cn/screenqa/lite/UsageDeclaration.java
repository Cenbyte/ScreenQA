package cn.screenqa.lite;

/** Versioned acknowledgement; no account, permissions or network grant is implied. */
final class UsageDeclaration {
    static final int REVISION = 3;
    static final String KEY = "usage_declaration_revision";
    static final String MARQUEE = "仅限个人学习研究 · 严禁用于线上考试、作弊及任何非法用途 · AI 内容仅供参考，请独立核验";
    static final String BODY = "大学生小帮手仅供个人学习与研究，例如课后复习、公开练习题解析及经授权的技术研究。\n\n"
            + "禁止用于任何线上或线下考试、测验、考核中的违规答题、代考、协助作弊，禁止非法获取或传播试题、答案，禁止侵犯他人隐私、知识产权或用于任何其他非法途径。请遵守法律法规以及学校、考试机构和平台规则。\n\n"
            + "AI 生成的内容可能错误，请独立思考并核验，不得将本应用用于要求独立完成的任务。\n\n"
            + "启动识题后，屏幕内容在设备上识别；相关文字及必要位置会发送至你选择的 AI 接口。请勿展示个人敏感信息、未公开试题或无权处理的内容。悬浮窗、屏幕共享、无障碍和可选 Root 均需要另行授权。\n\n"
            + "运营者：Cenbyte；联系邮箱：Cenbyte.dev@outlook.com。请阅读用户协议与隐私政策后再确认。用途声明不免除任何一方法定责任，也不替代系统权限授权。";

    private UsageDeclaration() { }
    static boolean isAccepted(int revision) { return revision == REVISION; }
}
