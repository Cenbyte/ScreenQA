# 大学生小帮手版本与开发说明

当前版本为 1.0.0 / versionCode 24（2026-10-02）。核心用途是自动识别题目并展示、复制答案。历史变更与验证证据保留在工程根目录 DEVLOG.md；0.9.8 界面重做与 0.9.9 dock 换库均由 DSH 完成，细节与文件清单见工程根目录 DSH-LOG.md。

## 外观与动效

配色统一由 ThemePalette 提供，七套主题 overlay_green（悬浮窗绿，默认，与悬浮助手同一套色）、black_gold、obsidian_teal、midnight_violet、crimson_night、jade_green、ivory_light；设置页（“我的 → 外观”）写入 theme_id 后当前页面就地重建、保持在原子页，页面、卡片、悬浮 dock、悬浮助手与答案弹窗都从同一份色板取色，CaptureService 监听 theme_id 即时重绘，互不重启。旧安装若从未选过主题或仍停在旧默认 black_gold，会在下一次启动时一次性迁移到悬浮窗绿。

底部 dock 使用 QWEA0/Liquid-Glass-Android `com.github.QWEA0:liquidglass:v2.0.11` 实时渲染：backdropSource 指向页面层，关闭库的自循环逐帧刷新，由库的滚动监听和短时交互触发采样；空闲时跟随极光每 100ms 刷新一次，停用极光后无此定时刷新，玻璃采样 dock 背后的页面，做轻微模糊、边缘折射与高光；图标与文字由库绘制在玻璃之上，不参与模糊。包名仍是 `com.example.liquidglass`。抽象层为 Dock 接口（高度 68dp、左右留白 20dp、圆角 34dp，大字体自动加高），实现类是 LiquidDock；设备 ABI 不含 arm64-v8a/armeabi-v7a，或库初始化抛错时，回退到 GlassDock 自绘面板（降采样模糊底图、色调、高光、边缘折射与漂移焦散），导航逻辑、选中态与图标不变。关闭“液态玻璃”后退化为不透明卡片并跳过模糊采样。深浅主题都使用 CLEAR 材质，表面色混入强调色的弱着色强度为深色 0.06 / 浅色 0.08；保留 AUTO 无障碍降级和前景亮度适配，关闭传感器高光。自绘路径使用 0.16 透明着色，采样时排除已有玻璃底图，修正滑动模糊读取已覆盖像素的问题，并将 0.2 倍底图上的模糊半径从 22 调至 4，保留内容轮廓。页面背景为 AuroraBackground 预渲染光斑，且极光位于被采样的页面层内，保证玻璃采样到的底图始终不透明。开启“减弱动效”或系统关闭动画时，入场与弹簧动画直接落到终态。位移与缩放统一走 Motion 的阻尼弹簧与插值器，不使用系统默认弹跳。

模型与接口选项来自 ModelCatalog；“设置 → AI”可切换模型与接口并单独控制 thinking。默认仍是 deepseek-flash、官方 chat/completions、thinking 关闭，保存键为 model_id、endpoint_url、thinking_enabled，不写旧 base/model 偏好；自定义接口只接受 https 且必须包含主机名，非官方地址不回落到旧 base。

## 使用与识别

配置自己的 DeepSeek API Key，授权悬浮窗和屏幕共享，切到题目页面后开始识题。自动执行总开关默认关闭，升级保留已有设置；自动下一题独立控制且默认关闭。辅助自动执行位于“设置 → 更多 → 辅助自动执行”，分题型选择或填写。关闭执行仍识题并展示答案，手动切题后继续识别。悬浮窗不再提供执行开关或顺序入口；两版执行顺序位于辅助设置，Root 功能位于“设置 → 更多 → Root”，总开关默认关闭。

已授权无障碍节点可用于快速本地定位，即使执行关闭；未授权时使用 OCR。手动框选保留在高级设置作为兼容入口。连续识题进入定位或分析状态会清理旧答案与详情卡，悬浮球显示加载环和识题图标，返回答案后显示答案文字。

本地定位成功时只提交当前题干与选项；AI 定位请求提交行号、文字与归一化纵向中心，完整矩形留在本机。输出预算：选择/判断 128、填空 384、简答 768 Token；AI 定位解题 768。仍关闭 thinking。条件缺失不猜测，截断结果不能当有效答案执行。实际消耗以 API usage 为准，尚未测量真实速度或节省比例。

## Token 用量

两个版本都统计最近一次有效请求、今日与历史实际 Token，包含连接测试。沿用已有历史字段；用户版从本版起入账。缺少或无效 usage 记为未知，仍展示有效答案，不估算消耗。没有余额校验、兑换、充值或扣减系统。

DEV 保留阶段、题型、来源、模型、结果、缓存和 reasoning 分类、CSV 导出及自设 API 单价预算。reasoning 是输出子项，不重复计入总量。明细保留最近 500 次，每日汇总保留 31 天；题标识为脱敏 SHA-256，不存题目、答案或 Key。预算是自设单价下的情景计算，不是 API 账单。用户版显示基础 Token 总量。两版均提供“日志与诊断”的开始/停止、查看、导出和清除；用户版日志默认关闭，DEV 延续默认开启及已有偏好。日志在各自包的私有目录，最多 8 个文件、约 2 MB 总量，可能包含题目与答案片段。

## 两个独立安装包

共用 app/src/main 核心源码，不建立两套长期源码：

| Variant | 包名 | 用途 |
| --- | --- | --- |
| userDebug | cn.screenqa.lite | 用户测试，基础 Token、Root 与可选日志 |
| developerDebug | cn.screenqa.lite.dev | 本地开发，分类统计、日志及 Root |
| userRelease | cn.screenqa.lite | 1.0.0 正式发布候选，当前 unsigned |

开发者身份与分类 Token 由 DEVELOPER_BUILD 决定；ROOT_SUPPORTED 和 DIAGNOSTICS_ENABLED 控制两版共享的 Root 与日志能力，包含 userRelease，不能用 DEBUG 代替。developerRelease 禁用。说明只打包进 DEV；两包独立配置 Key 与授权，可并存。Debug 签名在忽略的 .local/debug.keystore，凭证和私钥不得提交到 Git。

## 编译与验证

本地工具：JDK 17、Android SDK 35、Gradle 8.9、AGP 8.7.3。工具和缓存位于忽略的 .local。JitPack 仓库写在 settings.gradle 的 dependencyResolutionManagement（该块为 FAIL_ON_PROJECT_REPOS），首次构建需要联网解析 `com.github.QWEA0:liquidglass:v2.0.11`。设置 JAVA_HOME、ANDROID_HOME、ANDROID_USER_HOME、GRADLE_USER_HOME 后执行：

```powershell
.\gradlew.bat :app:assembleDeveloperDebug :app:assembleUserDebug :app:lintDeveloperDebug :app:lintUserDebug :app:testDeveloperDebugUnitTest :app:testUserDebugUnitTest
```

依赖已缓存后可加 `--offline` 复用本地缓存。固定的直接依赖：`com.github.QWEA0:liquidglass:v2.0.11`、`com.google.mlkit:text-recognition-chinese:16.0.1`、`org.json:json:20240303`、`junit:junit:4.13.2`。

APK 位于 app/build/outputs/apk/<flavor>/debug/。编译、Lint 与 JVM 回归不等于设备实测。本轮按用户要求不进行模拟器测试，不连接真机，不调用真实 API。长时 OCR、图形题、遮挡、长题、多题同屏与跨 App 节点差异仍需设备验证。

辅助执行沿用题目身份、会话、画面新鲜度与遮挡核验，动作不确定即停止，末题不自动提交或查看成绩。保留上一轮滚动修复：动作后等待新帧、清理旧 OCR 与边框、限定滚动连续性并严格核验题干与选项。具体历史证据见 DEVLOG。

## 1.0.0 发布准备

首次进入需确认用途声明，禁止考试作弊与非法用途；顶部循环滚动用途提示。用户协议和隐私政策由 docs/legal 自动打包到各 Variant，运营者 Cenbyte、联系邮箱 Cenbyte.dev@outlook.com；文档修订 3，旧修订确认需要重新接受。正式 Release 在本机签名，发布流程见 docs/RELEASE_PREPARATION.md。本轮按用户测试通过后的授权提交 Git；不上传 GitHub、不做设备测试。

