# 第三方与素材说明

本文件为 1.0.0 发布准备清单，实际许可应以每项上游 LICENSE、SDK 条款与打包内容为准；正式发布前需复核完整传递依赖，不把此清单当作全部许可已经审定的证明。

| 项目 | 用途 | 许可/资料 |
| --- | --- | --- |
| Google ML Kit 中文文字识别 16.0.1 | 本地 OCR 与打包模型 | [Google ML Kit 条款与隐私](https://developers.google.com/ml-kit/terms) |
| QWEA0/Liquid-Glass-Android v2.0.11 | 液态玻璃 UI、native blur | [上游 MIT LICENSE](https://github.com/QWEA0/Liquid-Glass-Android/blob/main/LICENSE) |
| Kotlin 标准库 2.0.21（传递依赖） | 液态玻璃库运行时 | [Kotlin 许可](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| JUnit 4.13.2、org.json 20240303 | JVM 测试；不作为应用顶层运行依赖 | 对应上游许可 |
| Gradle Wrapper、Android 构建插件及传递依赖 | 源码构建工具 | 各自上游许可；不是本项目原创源码 |
| 用户提供绿色角色 PNG | 启动图标、应用内标识 | Cenbyte 确认为其原创，并提供用于本项目公开发布 |

项目自身 LICENSE 原文保留。正式协议中的用途限制不改写既有开源许可。第三方模型、SDK 及图标不因随 APK 打包自动获得本项目的许可。

## 应用内来源说明

应用内完整作者/来源清单的唯一文本来源为 docs/legal/OPEN_SOURCE_INFO.txt，随所有构建打包；OCR 从最早提交起使用 Google ML Kit 16.0.1，适用 Google 服务条款，不能把示例代码的 Apache-2.0 视为全部 SDK 许可。Liquid-Glass-Android v2.0.11 的 MIT 署名为 pandadog，维护账号 QWEA0。导航 home/tune/person 路径与 Google Material Design Icons 上游核对，适用 Apache-2.0。Cenbyte 确认绿色角色图标原创。历史开发 skill 按用户说明标为不明来源，不捏造作者。
