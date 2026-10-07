# 构建与签名说明

开发版版本：1.5.0-dev / versionCode 36；用户版 1.5.0 / versionCode 36。工程共用 `app/src/main`，分别提供正式用户版和开发者调试版。界面使用 Java / 原生 Android View，最低 API 26，目标 API 35。

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

版本详情见 `RELEASE_NOTES_1.3.0.md`。发布源码不附带本地协作规则、开发过程记录、验证报告、密钥或缓存。

开发版 1.4.0 新增五档 AI 思考策略：首页双滑轨镜像同步，当前请求固定策略；公告位于版本号旁，首次使用教学和 API 教程移至公告内的离线教程页。默认均衡，旧思考开关开启的安装迁移为谨慎，其余迁移为均衡。

开发版 1.4.1：AI 备用定位统一关闭 thinking，只返回题目类型和范围；定位成功后单独按五档策略求解。1–3 档关闭 thinking，4–5 档开启；4 档复用 1 档提示词，5 档复用 2 档提示词。定位和答题分开记时，日志增加 AI_POLICY 阶段及档位。

开发版 1.4.2：备用 AI 定位期间先完成快速定位，不因整页 OCR 指纹变化反复取消；定位后严格核对当前页面题干、材料及选项，仅题目之外的文字变化可忽略。答题期间换题仍取消；结果显示前再次核对，并将行号与框选位置更新到当前页面。

开发版 1.4.3：首页 AI 策略右侧加入五档 Wattson 装饰图层，按现有连续进度 Crossfade，180ms 共同吸附；左侧渐隐／局部虚化烘焙于透明 WebP，第五档独立魂环按进度提前增强。六张 384×448 资源在独立后台线程一次解码并缓存。AI 策略和其他功能不变。

开发版 1.4.4：上述旧素材与抠图效果被五张新独立海报替换，完整保留顶部档位标注。新增同源整卡环境背景和从左向右减弱的半透明 Scrim，全部视觉限制在策略卡片的原圆角中。整卡、人物和标注共用连续进度；深度档环境与魂环略提前增强。十一张素材一次性后台预载缓存约 11.05MiB，双轨和 AI 功能逻辑不变。

开发版 1.4.5：首次打开一次性将策略置为均衡，之后仍保存用户选择；标题旁提示推荐均衡。增强公告入口，加入蓝奏云更新链接／密码、DeepSeek V4.1 Flash 与均衡建议，缩短使用边界说明。首次教学顶部提示先滚到底部获取 API Key，提供滚到底部按钮。

1.5.0 新增在线知识包安装与本地全文检索，为 AI 作答提供有长度限制的参考。首页两项紧凑开关在主按钮下方同排对齐；知识库加载与桌宠权限检查通过后才能开启。知识包单独下载，APK 和源码包不内置数据集。详见 [知识库说明](KNOWLEDGE_BASE.md)。
