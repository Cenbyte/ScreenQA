# 1.2.2 发布准备

正式版：1.2.2 / versionCode 28，包名 `cn.screenqa.lite`；标签 `v1.2.2`。

## 分发文件

- `college-helper-1.2.2-release-20261004.apk`：当前源码构建并沿用发布证书签名的正式 APK。
- `college-helper-1.2.2-source-20261004.zip`：源码、资源、测试源码、Gradle Wrapper、构建文档、许可证与法律文本；解压后根目录为 `ScreenQA/`。
- `RELEASE_NOTES.md`：发布正文。
- `SHA256SUMS.txt`：文件校验清单。

上述文件同时收录在 `college-helper-1.2.2-GitHub-ready-20261004.zip` 中。源码不含 DSH-LOG、DEVLOG、验证记录、调研笔记、本地协作规则、运行日志、构建报告、缓存、本机配置、APK 或签名密钥。

## 上传步骤

1. 将源码 ZIP 中 `ScreenQA/` 内的文件放到仓库根目录。保留远程已有内容并检查差异；文件合并不会自动删除远程旧文件。
2. 当前源码已移除 PadCardStack、PadDeck 及对应资源和测试，仓库中如有这些旧文件，也应同步移除。
3. 创建 `v1.2.2` Release；如标签已经存在，先核对对应源码和版本，再决定更新原 Release 或调整发布版本。
4. 正文使用 `RELEASE_NOTES.md`，保留独立一行 `versionCode: 28`，便于客户端更新比较。
5. 附件上传正式 APK、源码 ZIP 和 `SHA256SUMS.txt`。

## 重新构建与签名

环境及命令见 [构建与签名说明](DEVELOPER_BUILD.md)。userRelease 启用 R8 和资源收缩，默认输出未签名 APK。发布签名需使用工程外密钥；更新已有安装必须沿用同一证书，密码和本机密钥路径不提交。
