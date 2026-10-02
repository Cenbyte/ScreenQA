# 1.2.0 开发与构建说明

用户包 cn.screenqa.lite /1.2.0 /code27；开发包 cn.screenqa.lite.dev /1.2.0-dev /code27。
JDK17、Android SDK35、Build Tools34.0.0，使用项目 Gradle Wrapper8.9。配置 JAVA_HOME 和 ANDROID_HOME；首次构建需解析 Google、Maven Central、JitPack 依赖。

用户正式候选：./gradlew.bat :app:assembleUserRelease :app:lintUserRelease :app:testUserReleaseUnitTest
开发测试：./gradlew.bat :app:assembleDeveloperDebug :app:lintDeveloperDebug :app:testDeveloperDebugUnitTest
userRelease 输出 app/build/outputs/apk/user/release/app-user-release-unsigned.apk；使用 tools/sign-release.ps1 配合自己的工程外正式密钥签名。developerRelease 禁用。Debug本机密钥在 .local 生成，不包含在源码包内。

自动识题为主路径，辅助选择、填写和自动下一题默认关闭，2号桌宠沿用手动切题点击。默认象牙白；动画图集已预处理，直接构建无需原始桌面Sprite Sheet。用户使用自己的API Key，Key保存在设备内，不附源码。

更新内容见 RELEASE_NOTES_1.2.0.md；许可证与应用内协议完整保留。该源码分发包省略历史开发日志、研究记录、构建输出和本机缓存。