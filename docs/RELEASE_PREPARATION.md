# 大学生小帮手 1.2.2 发布准备

版本：1.2.2 / versionCode 28；建议 GitHub 标签 v1.2.2。正式包 cn.screenqa.lite，开发者测试包 cn.screenqa.lite.dev / 1.2.2-dev。最低 Android 8.0（API26）。

## 上传文件

outputs/college-helper-1.2.2-GitHub-ready.zip 是本轮上传准备总包：

- repository/：当前完整源码与资源、Gradle Wrapper、测试、开发记录、许可证及法律文本；不含 .git、.local、缓存、构建输出、机器配置或签名材料。
- release/college-helper-1.2.2.apk：当前源码生成、使用既有发布证书签名的 userRelease。
- testing/college-helper-1.2.2-dev-debug.apk：独立开发者测试包，供复测。
- RELEASE_NOTES.md、START_HERE.md、SHA256SUMS.txt、FILE_MANIFEST_SHA256.tsv：发布文案、上传步骤与校验清单。

源码单独分发：college-helper-1.2.2-source.zip。将 repository/ 内文件合并到 Cenbyte/ScreenQA 仓库根目录，保留远程已有内容。GitHub Release 附件上传正式 APK、源码 ZIP 和 SHA256SUMS.txt；testing/ 为可选测试附件。不要把总包直接展开进仓库，也不要把 APK 纳入源码树。

## 构建与签名

JDK17、Android SDK35、Build Tools34.0.0、Gradle Wrapper8.9、AGP8.7.3。配置 JAVA_HOME、ANDROID_HOME，或不提交的 local.properties；首次解析依赖需网络。

```powershell
./gradlew.bat :app:assembleUserRelease :app:lintUserRelease :app:testUserReleaseUnitTest
./gradlew.bat :app:assembleDeveloperDebug :app:lintDeveloperDebug :app:testDeveloperDebugUnitTest
```

userRelease 默认输出 unsigned APK。使用 tools/sign-release.ps1 在本机以工程外的正式密钥签名，密码通过安全输入传入；不将密钥、密码或真实签名路径写入源码。更新需沿用同一正式证书。developerRelease 禁用，Debug 使用独立调试证书。

## 本轮内容与边界

包括 GitHub 更新检测、紧凑更新卡片、点击触感、Dock 即时滑动切页、上沿黑边及选中胶囊遮挡标签修正。发布正文保留独立一行 versionCode: 28，支持客户端更新比较。

本轮只准备本地上传文件，不提交 Git、不推送、不创建 GitHub Release。构建/JVM/Lint及签名核验记录见 DEVLOG.md；最新 Dock 显示改动仍需原设备验收，未连接真机、未运行模拟器、未调用真实 AI。
