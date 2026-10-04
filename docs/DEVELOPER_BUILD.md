# 构建与签名说明

版本：1.2.2 / versionCode 28。工程共用 `app/src/main`，分别提供正式用户版和开发者调试版。界面使用 Java / 原生 Android View，最低 API 26，目标 API 35。

## 环境

- JDK 17
- Android SDK 35、Build Tools 34.0.0
- Gradle Wrapper 8.9、Android Gradle Plugin 8.7.3

配置 `JAVA_HOME` 和 `ANDROID_HOME`，或者在本机 `local.properties` 中配置 `sdk.dir`。本机配置不要提交到仓库。首次构建需要从 Google、Maven Central 和 JitPack 下载依赖。

## 构建类型

| Variant | 包名 | 说明 |
| --- | --- | --- |
| userRelease | cn.screenqa.lite | 正式版；R8、资源收缩；默认未签名 |
| userDebug | cn.screenqa.lite | 用户调试版 |
| developerDebug | cn.screenqa.lite.dev | 开发者调试版，含分类 Token 统计和开发说明 |

`developerRelease` 不启用。Root 与可选日志在用户版和开发者版共用；Root 默认关闭，用户版日志默认关闭。两包配置、API Key 和系统授权独立。

```powershell
./gradlew.bat :app:assembleUserRelease
./gradlew.bat :app:assembleDeveloperDebug
```

输出位于 `app/build/outputs/apk/<flavor>/<buildType>/`。Debug 密钥使用本机 `.local/debug.keystore`，不进入源码分发。

## 正式签名

正式版输出 `app-user-release-unsigned.apk`，可使用 Android SDK 的 zipalign 和 apksigner，或仓库中的 `tools/sign-release.ps1` 签名。脚本需要传入 unsigned APK、工程外的密钥路径、别名、Build Tools、Java 路径和一个尚不存在的输出 APK 路径；密码在本机安全输入，不写入源码。

更新已有安装必须使用同一发布证书。签名后执行 `apksigner verify --verbose --print-certs` 和 `zipalign -c 4` 检查签名与对齐。

## 本地检查命令

```powershell
./gradlew.bat :app:testUserReleaseUnitTest :app:lintUserRelease
./gradlew.bat :app:testUserDebugUnitTest :app:lintUserDebug
./gradlew.bat :app:testDeveloperDebugUnitTest :app:lintDeveloperDebug
```

测试源码保留在工程内；执行生成的报告与日志不属于源码包。编译、单元测试和 Lint 不替代设备上的视觉与端到端验证。

## 当前实现

横屏宽大于高时进入 Pad 布局，三个主页面使用双列纵向排列、右侧 Dock；竖屏使用原单列页面与底部 Dock。主页面互相隐藏，内容没有堆叠卡片或横向翻页手势。

悬浮助手和答案窗在支持的系统上使用跨窗口背景模糊，其他环境提高底色不透明度。自动识题将答案阅读与下一题监测分开；旧答案保留到不同题目定位成功，辅助自动执行及自动下一题仍由独立开关控制。

版本详情见 `RELEASE_NOTES_1.2.2.md`。发布源码不附带本地协作规则、开发过程记录、验证报告、密钥或缓存。
