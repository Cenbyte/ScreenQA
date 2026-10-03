# GitHub 上传准备 · 1.2.2 / versionCode 28

目标仓库：Cenbyte/ScreenQA。建议标签 v1.2.2，标题“大学生小帮手 1.2.2”。

本轮生成当前源码快照、正式签名用户 APK、开发者测试 APK、发布文案及 SHA-256 清单。正式用户版证书需与此前 1.2.0 相同；开发者包使用独立 Debug 证书。密钥及本机凭证不进入源码或 ZIP。

上传步骤：

1. 解压 college-helper-1.2.2-GitHub-ready.zip，将 repository/ 中内容合并到仓库根目录并检查差异。
2. 创建 v1.2.2 Release，正文使用 RELEASE_NOTES.md，保留独立的 versionCode: 28。
3. 上传 release/college-helper-1.2.2.apk、college-helper-1.2.2-source.zip、SHA256SUMS.txt。开发者测试 APK 是可选附件。
4. 最新 Dock 修复先在原设备复测，确认后再发布；如希望提前供测试，使用 GitHub Pre-release，客户端 latest 检测不会提供预发布版。

本轮只准备文件，未提交、推送或创建远程 Release；本地构建和签名核验不能替代设备视觉验收。完整证据与边界见 DEVLOG.md。构建方法、分发结构及签名操作见 RELEASE_PREPARATION.md。
