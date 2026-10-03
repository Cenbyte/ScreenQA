# 致敬最不爱用豆包之人

Android 原生 View / Java 的个人学习研究工具。当前版本 **1.2.2 / versionCode 28**，最低 Android 8.0（API26），目标 API35。无需 androidx、Compose 或 Kotlin 源码；液态玻璃依赖包含 Kotlin 运行时。

**仅限个人学习研究。严禁用于线上或线下考试、测验、考核中的违规答题、代考、协助作弊，以及任何非法用途。AI 输出仅供参考，请独立思考并核验。**

运营者：Cenbyte；联系邮箱：Cenbyte.dev@outlook.com。官方公开仓库：[Cenbyte/ScreenQA](https://github.com/Cenbyte/ScreenQA)。正式分发使用本地签名的 userRelease；调试 APK 仅供测试。

## 能力

- 识别经授权展示的学习材料：本地中文 OCR 或已授权无障碍页面文字，展示、复制 AI 参考解析。
- 首页常驻首次使用教学、DeepSeek API 获取与配置教程及功能公告；自动选择可尝试，不建议无人值守挂机刷题。
- 每次打开应用异步检查 GitHub 最新正式 Release：首页展示新版本入口、最新版确认或网络失败提示，支持重新检查；查看/下载更新打开对应 GitHub Release。
- 小水怪图标常驻，识题/分析/下一题阶段均有加载环与状态标记，答案自动在旁边的紧凑聊天气泡展开；长内容可滚动，停留 10–90 秒，填空/简答至少 20 秒，触摸或复制后重新计时，可主动关闭继续识题、点击角色展开控制面板后点查看答案。
- 完整填空/简答参考答案自动写入系统剪贴板，保留小图标手动复制；“我的 → 赞助支持”展示本地赞赏码和随机祝福语。
- 使用自己的 API Key，可配置 DeepSeek 或完整 HTTPS 接口、模型及 thinking。
- 辅助自动执行默认关闭，选择、填写及自动下一题分别控制；可选 Root 需要单独授权。
- 七套配色，半透明液态玻璃 Dock；不支持原生管线时自绘降级，减弱动效可关闭极光流动。
- 保存实际 Token 用量；用户日志默认关闭，开发者版有分类统计与本地诊断。没有充值、余额或积分系统。
- 首次启动阅读并确认用途声明，支持拒绝退出；顶部循环滚动用途提示。“我的 → 关于应用”可查看声明及协议。

图标采用发布者提供的绿色角色原图，保留完整主体并适配 Android adaptive icon；图标由 Cenbyte 确认为其原创并提供用于本项目公开发布。

## 数据与权限

屏幕图像供本机 OCR 处理，识别文字和必要位置会发送至所选 AI 服务。不要在敏感页面启动助手。AI 服务及 Google ML Kit 具有各自的数据处理规则，本应用不是完全离线工具。日志可能含部分题目和答案，导出前请检查。

首次确认不替代系统授权。悬浮窗、屏幕共享、通知、无障碍和可选 Root 单独控制，可随时暂停或停止。用途声明不构成自动识别并阻断所有考试场景的技术承诺，也不免除任何一方法定责任。

- [用户协议](docs/legal/USER_AGREEMENT.md)
- [隐私政策](docs/legal/PRIVACY_POLICY.md)
- [第三方与素材清单](docs/THIRD_PARTY_NOTICES.md)

## 构建

JDK17、Android SDK35、Build Tools34.0.0、Gradle Wrapper8.9、AGP8.7.3。设置 JAVA_HOME、ANDROID_HOME，或在不提交的 local.properties 配置 sdk.dir。依赖来自 Google/Maven Central/JitPack，首次解析需要网络。

```powershell
./gradlew.bat :app:assembleUserRelease :app:lintUserRelease :app:testUserReleaseUnitTest
```

userRelease 默认未签名，启用 R8 与资源收缩；输出 app/build/outputs/apk/user/release/app-user-release-unsigned.apk。Windows 受限环境应把 TEMP/TMP 指向可写目录，避免 JUnit 临时文件失败。

| Variant | 包名 | 版本名 | 用途 |
| --- | --- | --- | --- |
| userRelease | cn.screenqa.lite | 1.2.2 | 正式发布候选，需本地签名 |
| userDebug | cn.screenqa.lite | 1.2.2 | 调试测试 |
| developerDebug | cn.screenqa.lite.dev | 1.2.2-dev | 本地开发 |

applicationId 和 Java namespace 保持兼容，developerRelease 禁用。正式证书与调试证书不同的安装不能直接覆盖；不要在备份本地数据前卸载旧包。

## 发布与许可

构建与本地签名见 [开发与构建说明](docs/DEVELOPER_BUILD.md)。

更新内容见 [1.2.2 更新说明](docs/RELEASE_NOTES_1.2.2.md)。项目许可见 [LICENSE](LICENSE)，第三方 SDK 与素材按各自权利使用。


首页可开启独立的‘下一题’小悬浮窗（默认关闭）。启动助手同时显示两个可拖动的小窗，手动点‘下题 ›’读取当前画面并尝试切题，无需开启自动选择；需要无障碍或已授权 Root，并露出唯一下一题按钮。导航等待/超时继续识题，保持加载状态提示。
