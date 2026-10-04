# 大学生小帮手

大学生小帮手是我维护的 Android 学习辅助工具，用于识别学习材料中的题目、展示参考答案，并支持复制和连续识题。项目采用 Java 和原生 Android View 开发。

当前版本：**1.3.0 / versionCode 29**。支持 Android 8.0 及以上，提供 arm64-v8a 和 x86_64 安装包。维护者：Cenbyte；联系邮箱：Cenbyte.dev@outlook.com。

**仅限个人学习研究，严禁用于考试、测验、考核中的违规答题、代考、协助作弊及任何非法用途。AI 参考答案可能有误，请独立思考并核验。**

## 功能

- 使用本地中文 OCR 或已授权的无障碍页面文字识别题目，调用所配置的 AI 接口生成参考答案。
- 答案以半透明磨砂聊天气泡显示，题目摘要采用小字号，答案采用醒目的大字号；支持复制、长答案滚动和重新查看。
- 得出答案后继续监测下一题：关闭辅助自动执行时等待 0.4 秒，开启对应题型执行时等待 0.2 秒安排扫描；实际识别还需处理时间。成功定位不同题目后才关闭旧答案气泡。
- 首页提供主控制台、公告、首次使用教学和 DeepSeek API 配置教程；应用打开后检查 GitHub 最新正式版本。
- 横屏宽大于高时启用 Pad 布局：首页、设置、我的统一为双列纵向排列，右侧 Dock 支持点击或上下滑动切换。竖屏保留底部 Dock 和单列布局。
- 七套主题，默认象牙白；液态玻璃 Dock 支持即时滑动切换和触感反馈，可关闭玻璃或减弱动效。
- 1 号小水怪悬浮助手与答案窗使用磨砂背景，角色图案保持清晰。Android 12 及以上且系统支持时使用跨窗口背景模糊，其余环境采用提高不透明度的材质回退。
- 2 号 Wattson 桌宠可单独开关、拖动，点击尝试切换下一题；需要无障碍或已授权 Root，并能定位唯一明确的下一题按钮。
- 辅助自动选择、填写和自动下一题分别控制，默认关闭；关闭后仍可查看和复制答案。Root 增强需要单独授权。
- 记录实际 Token 用量，提供可选本地日志；开发者版额外提供分类统计。应用内没有充值、余额或积分系统。

## 开始使用

1. 在首页教程中查看 DeepSeek API Key 获取方法，在“设置 → AI 与模型”填写自己的 Key、保存配置并测试连接。
2. 点击“开启悬浮助手”，按系统提示授权悬浮窗和屏幕共享。
3. 切到学习练习页面，在悬浮助手中开始识别。保持题干、选项完整可见，将助手移开文字区域。
4. 查看并核验答案，需要时复制；可随时暂停或停止助手。

## 数据与权限

屏幕图像用于本机 OCR，识别文字及必要位置会发送至所选 AI 服务。请勿在敏感页面启动助手。API Key 使用本机 Android Keystore 保护；不要公开 Key 或上传含 Key 的截图。

悬浮窗、屏幕共享、通知、无障碍和 Root 分别授权。用户版日志默认关闭，日志可能包含题目和答案片段，导出前请检查内容。应用不承诺自动识别或阻止所有考试场景。

- [用户协议](docs/legal/USER_AGREEMENT.md)
- [隐私政策](docs/legal/PRIVACY_POLICY.md)
- [第三方与素材说明](docs/THIRD_PARTY_NOTICES.md)

## 构建

需要 JDK 17、Android SDK 35、Build Tools 34.0.0。工程使用 Gradle Wrapper 8.9 和 Android Gradle Plugin 8.7.3，依赖来自 Google、Maven Central 和 JitPack。

设置 `JAVA_HOME`、`ANDROID_HOME`，或在本机 `local.properties` 中配置 SDK 路径，然后执行：

```powershell
./gradlew.bat :app:assembleUserRelease
```

正式构建启用 R8 和资源收缩，默认输出 `app/build/outputs/apk/user/release/app-user-release-unsigned.apk`。发布前需使用自己的发布证书签名，签名方法见 [构建与签名说明](docs/DEVELOPER_BUILD.md)。

| 构建类型 | 包名 | 版本名 |
| --- | --- | --- |
| userRelease | cn.screenqa.lite | 1.3.0 |
| userDebug | cn.screenqa.lite | 1.3.0 |
| developerDebug | cn.screenqa.lite.dev | 1.3.0-dev |

开发者包可以与正式包并存，各自保存配置和授权。更新正式包需要沿用相同发布证书；调试证书不能覆盖正式安装。

## 发布与许可

官方仓库：[Cenbyte/ScreenQA](https://github.com/Cenbyte/ScreenQA)。更新内容见 [1.3.0 更新说明](docs/RELEASE_NOTES_1.3.0.md)，上传方式见 [发布准备](docs/RELEASE_PREPARATION.md)。

源码包包含代码、资源、测试源码、Gradle Wrapper、文档和许可证，不含运行日志、验证记录、本机工具缓存或签名密钥。项目许可见 [LICENSE](LICENSE)，第三方组件及素材保留各自署名与许可。
