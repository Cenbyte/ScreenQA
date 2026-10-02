# 大学生小帮手 1.0.0 发布与签名

正式用户包：cn.screenqa.lite / versionName 1.0.0 / versionCode 24；最低 Android 8.0。开发者包 cn.screenqa.lite.dev / 1.0.0-dev 仍为独立调试包，developerRelease 禁用。

运营者 Cenbyte；联系邮箱 Cenbyte.dev@outlook.com；公开仓库 https://github.com/Cenbyte/ScreenQA 。图标由 Cenbyte 确认为其原创并用于本项目公开发布，原 LICENSE 和第三方许可保留。用户反馈自定义模型修复测试 OK；代理没有设备或真实 API 实测。

## 最新 ZIP

college-helper-1.0.0-release-GitHub.zip 包含：

- repository/：当前完整源码快照，包含已验收 UI 修改、自定义模型修复、资源、测试、文档和签名脚本，不含 .git。
- release/college-helper-1.0.0.apk：启用 R8 与资源收缩，使用发布者提供的工程外 keystore 在本机签名并核验的 userRelease。
- release/SHA256SUMS.txt：正式 APK 校验信息。
- RELEASE_NOTES.md：可用于 GitHub Release 的发布文案。
- START_HERE.md、FILE_MANIFEST_SHA256.tsv：上传说明和逐文件校验清单。

ZIP 排除 .local、SDK/JDK、缓存、机器配置、日志、调试 APK、API Key、签名密钥及密码。APK 包含签名验证所需的公开证书，不包含私钥。ZIP 自身 SHA-256 在外侧 SHA256-1.0.0-release.txt，避免循环哈希。

将 repository/ 中的内容放到仓库根目录；将正式 APK 与校验文件作为 GitHub Release 附件。本轮按用户授权提交本地 Git，不推送，也不创建远程 Release。仓库现有 main 需要在实际上传时保留或合并已有内容，不能用整个工作目录覆盖上传。

此前“大学生小帮手-1.0.0-GitHub发布准备.zip”和 api-fix-testing APK 是历史准备/测试文件，不是本轮最终发布附件。

## 构建及重新签名

JDK17、Android SDK35、Build Tools34.0.0、Gradle Wrapper8.9、AGP8.7.3。配置自己的 JAVA_HOME、ANDROID_HOME 或不提交的 local.properties，TEMP/TMP 指向可写目录。首次解析依赖需要联网。

```powershell
./gradlew.bat :app:assembleUserRelease :app:lintUserRelease :app:testUserReleaseUnitTest
```

Gradle 的 userRelease 保持 unsigned 输出，不将本机签名路径或密码写入工程。签名使用 tools/sign-release.ps1，密码由 Read-Host -AsSecureString 本机输入，过程只临时传给 apksigner 环境变量，结束后恢复/清理。示例路径自行替换，不把真实密钥放进源码目录：

```powershell
./tools/sign-release.ps1 -UnsignedApk './app/build/outputs/apk/user/release/app-user-release-unsigned.apk' -Keystore 'D:/private/release.jks' -Alias 'your-alias' -BuildToolsDirectory 'your-sdk/build-tools/34.0.0' -JavaHome 'your-jdk17' -OutputApk 'D:/private/college-helper-1.0.0.apk'
```

脚本要求新输出路径，先 zipalign，再 apksigner sign/verify，最后核验对齐并输出 SHA-256。签名后不得再改动 APK。长期私下保管并备份同一份发布密钥，更新必须沿用同一证书。不同证书的调试安装不能直接覆盖；卸载前由用户自行保护本机配置。

## 法律文本与验收边界

两份发布文本已填入运营者、邮箱、仓库，修订号 3，由 Gradle 自动打包；旧修订确认需要重新接受。以后重大文档变化应同步递增 UsageDeclaration.REVISION，重新构建/签名并更新哈希。

ML Kit 启动 Provider 初始化/诊断联网、第三方接收方及隐私告知适配仍需发布者结合实际设备核验；不把构建通过当作全部法律/许可审查完成。用途声明不等于技术上可靠阻断所有考试场景。新安装、升级、拒绝退出、文档确认及权限撤销等设备流程按发布者验收反馈持续维护。

