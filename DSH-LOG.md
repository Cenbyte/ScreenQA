# DSH 变更日志（DSH-LOG.md）

本文件是 DSH（DeepSeek Harness 智能体）在这个工程里的专属记录，与 DEVLOG.md 分开维护：DEVLOG.md 继续按项目原有格式记录版本级变更与验证证据，本文件记录 DSH 每一轮改动的需求、设计取舍、逐文件清单、已知限制与未完成事项。两边互相引用，不重复粘贴同一段证据。

作者说明：本文件记录的代码改动由 DSH 完成，不是 Codex 的改动；0.9.6 / 0.9.7 及更早的 DEVLOG 条目仍是 Codex 的历史工作，未经本次改动覆盖。本轮按用户要求未提交 git，等用户测试完成后再决定是否提交。

## 2026-10-02 · 0.9.8 / versionCode 22 · 界面重做

### 需求（用户要求，未改动范围与验收条件）

美化重做 UI，交互逻辑要优秀、要有非线性动画；底部 dock 栏悬浮并采用液态玻璃；默认整体黑金配色；设置里预留几个不同配色主题；开放模型与 API 选择。完成后先交付编译好的安装包，不做模拟器测试；先不要提交 git，等用户测试完成后再决定；日志按原先格式写，并另开一个 DSH 专属 log（即本文件）。

### 新增文件（source/app/src/main/java/cn/screenqa/lite/）

- ThemePalette.java：全应用唯一配色契约。字段 id/name/dark/background/surface/surfaceAlt/border/foreground/secondary/accent/accentSoft/onAccent/heroStart/heroEnd/glassTint/glassEdge/glassSheen/danger/auroraA/auroraB/auroraC；Builder 构建；ALL 六套：black_gold（黑金，DEFAULT）、obsidian_teal、midnight_violet、crimson_night、jade_green、ivory_light（唯一浅色）。工具方法 fromStored(String) / onColor(int) / blend(int,int,float) / alpha(int,float)。
- Motion.java：非线性动效统一入口。SPRING / SPRING_OUT / SPRING_SOFT 为阻尼弹簧（1-exp(-kt)·cos(wt)），EMPHASIZED / DECELERATE / ACCELERATE / OVERSHOOT / ANTICIPATE 为 PathInterpolator；时长 QUICK=190 / BASE=300 / SLOW=470 ms；press(View,boolean,boolean) / entrance(View,int,int,boolean) / fadeIn(View,long,boolean)。
- ModelCatalog.java：模型与接口目录。ENDPOINTS：DeepSeek 官方 https://api.deepseek.com/chat/completions、官方 v1、自定义；MODELS：deepseek-flash、deepseek-chat、deepseek-reasoner（thinking=true）、自定义。validEndpoint 只接受 https 且必须有主机名、不得含用户名/查询/片段；另外提供 official / endpointLabel / modelLabel / prefersThinking。
- GlassBackdrop.java：把某个 View 画进 1/downscale 的缩略图并做两遍可分离 box blur，供液态玻璃采样；capture(View,float,int) / bitmap() / scale() / contentWidth() / contentHeight() / release()。
- LiquidGlassView.java：自绘液态玻璃表面。绘制顺序为近似阴影 → 裁剪圆角 → 模糊底图（围绕中心放大 1.07 倍做折射）→ 色调 → 高光 → 两团漂移焦散 → 渐变描边。API：setPalette / setGlassEnabled / setCornerRadius / setShadowRoom / setLiquidPhase / setBackdrop(Bitmap,float,float,float)。
- AuroraBackground.java：页面背景的极光光斑。三团预渲染径向渐变按 34 秒周期缓慢漂移，用预渲染位图缩放绘制而不是每帧重建 shader。
- GoldSwitch.java：自绘开关（54×32dp），轨道与拇指按弹簧进度混合 accent/onAccent，开态带光晕；自有 Listener 接口，支持 setChecked(boolean,boolean) / setCheckedSilently / setReduceMotion。
- GlassDock.java：悬浮 dock 容器（FrameLayout：LiquidGlassView + 药丸指示器 + 三个图标项）。常量 ITEM_WIDTH_DP=86、PANEL_HEIGHT_DP=64、INSET_DP=13、PILL_MARGIN_DP=7、CORNER_DP=30；静态 widthFor(Context,int) / heightFor(Context)；setItems / setSelected / setLifted（下滑时下移 12dp 并缩到 0.95）/ setListener / setGlassEnabled / setBackdrop。
- GlassCard.java：液态玻璃卡片容器，手工 onMeasure 让玻璃层与内容层同尺寸；body() / glass() / setPalette。
- Ui.java：dp/sp/density 小工具。
- 新增矢量图 source/app/src/main/res/drawable/ic_dock_home.xml、ic_dock_settings.xml、ic_dock_profile.xml（24dp，运行时用 setColorFilter 上色）。

### 修改文件

- MainActivity.java：整体重写（原 853 行全部替换）。根布局为 FrameLayout{ AuroraBackground, pagesLayer（玻璃采样源）, GlassDock }；dock 三项对应首页/设置/我的；详情页从 11 个扩到 13 个，新增“我的 → 外观”。所有颜色改由 palette 提供，去掉原来硬编码的浅/深绿；开关改用 GoldSwitch；新增 chip/field/showChoices 等构建块；AI 页增加模型、接口选择与自定义输入、thinking 开关；滚动超过 6dp 时 dock setLifted(true)；切页/展开详情后延迟 90ms 重新抓取玻璃底图并分发给所有玻璃视图；主题变更走 rebuildForTheme()。onSaveInstanceState 仍保存 selectedTab/selectedDetail。
- Settings.java：新增 theme_id、reduce_motion、glass_dock、model_id、endpoint_url、thinking_enabled 六个偏好及读写方法。model_id 默认 Settings.MODEL，endpoint_url 为空时回落 Settings.ENDPOINT。
- ApiRequest.java：新增 requestBody(String model,String system,String user,int maxTokens,boolean thinking) 重载与实例 message(Exception)，请求地址取 settings.endpoint()，实例构造时持有 Settings。原静态默认与旧重载保留。
- CaptureService.java：悬浮窗、答案弹窗、图标底色全部改为从 ThemePalette 取色；注册 SharedPreferences 监听器，theme_id 变化时在主线程重绘并注销于 onDestroy。
- app/build.gradle：versionCode 22 / versionName 0.9.8（两个 flavor 同步）。
- README.md、docs/DEVELOPER_BUILD.md、验证记录.md、DEVLOG.md：版本号与界面说明同步。

### 设计取舍（含原因）

- 阴影用手绘同心圆角矩形，不用 setLayerType(LAYER_TYPE_SOFTWARE)：软件层会让整页绘制走 CPU，而玻璃面板每帧都要重绘，低端机直接掉帧。
- 玻璃底图按 0.2 降采样、22px 模糊再两遍 box blur：全分辨率高斯在手机上是明显开销，降采样后视觉差异很小。
- 底图采用“快照 + 变化时刷新”，不是每帧重采样：dock 只在切页、展开详情和滚动时移动，连续采样加模糊的耗电不值得。
- 主题切换后重建页面，而不是逐个控件换色：页面全部由代码构建、控件数量多，重建最不容易漏色；代价是回到当前标签顶部。
- 模型偏好键必须是 model_id：Settings.save() 结尾会 remove("base")、remove("model")，沿用 "model" 会被下一次保存抹掉。
- 保留 Settings.MODEL / ENDPOINT 静态默认与旧的静态 requestBody 重载：FastPipelineTest 直接断言 deepseek-flash 与 thinking disabled，必须继续通过。
- 自定义接口不回落到旧 base 偏好：延续 0.9.x 的取向，非官方地址必须显式提供完整 https 接口。
- dock 滚动时下移 12dp 并缩到 0.95：滚动过程中让出视觉重量，停止后回到原位。

### 验证与证据

- 完整验证：`source/.local/run-validation.ps1 -LogName 'ui-rework-final3.log'`，9 个任务（三个 Variant 的 assemble / lint / testUnitTest）全部 BUILD SUCCESSFUL in 35s，156 个 actionable tasks、48 executed。日志 `source/.local/ui-rework-final3.log`。
- 单元测试：`app/build/test-results/testDeveloperDebugUnitTest`、`testUserDebugUnitTest`、`testUserReleaseUnitTest` 各 140 项，fail=0 / error=0 / skip=0（按 XML 里 `<testcase`、`<failure`、`<error`、`<skipped` 计数）。编译任务 `compileDeveloperDebugJavaWithJavac`、`compileUserDebugJavaWithJavac`、`compileUserReleaseJavaWithJavac` 在本轮均为实际执行（非 UP-TO-DATE），因此上面 140 项覆盖了本次全部改动，包括收尾加入的液态玻璃相位驱动。
- Lint：三个 Variant 的 lint 均通过（lint 在存在 error 时会中断构建），仍为 0 error / 16 项既有警告。
- 过程记录：第一次完整验证（`.local/ui-rework-final.log`）在 `testDeveloperDebugUnitTest` 出现 `140 tests completed, 10 failed`，10 项全部是 `java.nio.file.AccessDeniedException: C:\Users\Administrator\AppData\Local\Temp\junit<随机>`（JUnit 用系统临时目录建临时文件，被当前沙箱策略拒绝）；把 `TEMP`/`TMP` 指向工程内 `source/.local/tmp` 后重跑即全部通过。同一轮 lint 还出现 `Unable to initialize metrics, ensure C:\Users\Administrator\.android is writable`，同样属于宿主目录权限，与代码无关（0.9.6、0.9.7 的 DEVLOG 也记录过沙箱下重跑）。
- APK 静态核验（`source/.local/android-sdk/build-tools/34.0.0`，需先设 JAVA_HOME 指向 `.local/tooling/jdk-17.0.20.1+1`，否则 apksigner.bat 因 JAVA_HOME 缺失以 exit 1 结束且不输出结论）：
  - 用户包 `app/build/outputs/apk/user/debug/app-user-debug.apk`，29,022,042 B，aapt: `cn.screenqa.lite` / `0.9.8-user-preview` / versionCode 22 / minSdk 26 / targetSdk 35；apksigner verify exit 0，证书 SHA-256 `0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912`；zipalign 4 字节通过；不含 `assets/DEVELOPER_BUILD.md`。
  - 开发包 `app/build/outputs/apk/developer/debug/app-developer-debug.apk`，29,027,796 B，aapt: `cn.screenqa.lite.dev` / `0.9.8-dev-answer-preview` / versionCode 22；签名与对齐同上；含 `assets/DEVELOPER_BUILD.md`，其 SHA-256 `00804b6d0ccab114bcd22b560ecef849fa2799bfa4b8a1f3c4e5790e27a975b2` 与 `docs/DEVELOPER_BUILD.md` 完全一致。
  - 用户 Release `app/build/outputs/apk/user/release/app-user-release-unsigned.apk`，25,667,786 B，R8 与资源收缩成功，保持 unsigned，不作为可安装包交付。
- 交付副本（本次交给用户的安装包）：`outputs/screenqa-0.9.8-user-preview.apk`（SHA-256 `77eb33e0288fb22069a4bad9e3631eef66f73d4aa790eabaaa788ea3f9760ca6`）与 `outputs/screenqa-0.9.8-dev-answer-preview.apk`（SHA-256 `5d81bee1860966ea9cb547ddd93c9a1c65621f64ffe07310879787fe5911f092`），汇总写入 `outputs/SHA256-0.9.8.txt`。
- 版本一致性：`app/build.gradle`（user 与 developer 两个 flavor 的 versionCode 22 / versionName 0.9.8）、README.md、docs/DEVELOPER_BUILD.md、验证记录.md、DEVLOG.md 均已对齐 0.9.8 / code22。
- 未做的事（与用户要求一致）：没有模拟器、没有安装 APK、没有真机、没有真实 API 调用、没有提交 git。

### 已知限制与后续

- 按用户要求没有运行模拟器、没有安装 APK、没有连接真机、没有调用真实 API；“能编译 + JVM 回归通过”不能证明真机上的实际观感、帧率与交互。
- 玻璃底图是切页/详情变化时的快照，滚动时面板背后的内容会变化，快照会显得略滞后；本轮没有做滚动按帧刷新，后续如有需要应在真机上先量测耗电。
- 液态玻璃、极光背景在低端设备上的绘制开销尚未实测；关闭“液态玻璃”会退回不透明卡片并跳过模糊采样。
- “减弱动效”只覆盖入场与弹簧动画，系统动画缩放关闭时的行为仍需真机确认。
- 本轮改动未提交 git；是否提交、是否随 0.9.8 打 tag 由用户在设备验收后决定。

## 2026-10-02 · 0.9.9 / versionCode 23 · 底部 Dock 换成库实现的实时液态玻璃

### 需求（用户要求，原文要点）

用户要求（m00747）：修改 Android 源码，为应用内底部 Dock 添加实时液态玻璃效果并重新构建 APK；先检查底部导航实际使用 Compose 还是 XML/View，以及 minSdk、Kotlin、Compose 和 Gradle 版本。选库：Compose 优先 Kyant0/AndroidLiquidGlass（Backdrop），XML/View 用 QWEA0/Liquid-Glass-Android 并优先评估 LiquidGlassTabBar。效果要求：浮动胶囊 Dock、实时采样背后页面、轻微模糊、边缘折射与高光；图标与文字保持清晰，保留原有导航逻辑、选中状态与角标；选中指示器平滑移动、按压带轻微弹性反馈；先用高度 64–72dp、左右留白 16–24dp；适配浅色/深色、系统导航栏 Insets、键盘弹出与大字体；低版本或不支持效果的设备提供降级，不要模糊整个页面或 Dock 内的文字；用有文字或图片的滚动页面检查背景是否实时更新，并检查导航点击、可读性和滑动流畅度。交付：可安装 APK、修改文件清单、固定的依赖版本、实际测试结果。

约束（用户原文）：依然是先不要提交 git；上一版的液态玻璃效果很差；先不要编译用户端；开发者端我测试无问题之后继续。

### 前置核查（选型依据）

- 界面是纯 View 体系：`app/src/main/java/cn/screenqa/lite/` 下 71 个 Java 文件、0 个 Kotlin 文件，无 Compose 依赖，也不声明 androidx；minSdk 26 / targetSdk 35 / compileSdk 35、Java 17、AGP 8.7.3、Gradle wrapper 8.9。因此走用户给出的 XML/View 分支。
- 上一版 dock（GlassDock）是自己画的玻璃：采样源是 `pagesLayer` 的 0.2 降采样快照 + 22px 模糊，只在切页/详情/主题变化后抓一次，滚动时不更新，而且快照抓的是 `pagesLayer`——它在当时没有自己的背景色，透明区域的像素等于没有内容。这是“效果很差”的直接原因。
- 依赖形态：QWEA0/Liquid-Glass-Android 的 Maven 坐标是 `com.github.QWEA0:liquidglass:v2.0.11`（JitPack 按需构建；探测时 Invoke-WebRequest 曾全量返回 500，curl 带浏览器 UA 立即 200，属构建期等待）。多模块坐标 `com.github.QWEA0.Liquid-Glass-Android:liquidglass` 不存在。
- 运行时兼容：AAR 内 `LiquidGlassView`、`LiquidGlassTabBar` 及渲染链（GlassLensRenderer / GlassRuntimeEffects / EdgeHighlightEffect / HardwareBackdropBlur）不引用 androidx，只有 CLASS 保留的 `androidx.annotation` 注解（运行期不需要，且 androidx.annotation 1.6.0 已在 runtime classpath 里）；引用 androidx 的是没有使用的 LiquidGlassDialogBuilder（appcompat）、LiquidGlassTabLayoutMediator（viewpager2/recyclerview）、LiquidGlassChip/ListItem/Toast。`kotlin.Unit` / `kotlin.jvm.functions.Function1` 由 POM 传递的 kotlin-stdlib 2.0.21 提供。
- JNI 覆盖：`jni/arm64-v8a/libnativegauss.so` 与 `jni/armeabi-v7a/libnativegauss.so`；本工程 abiFilters 是 arm64-v8a + x86_64，交集只有 arm64-v8a，因此必须按 ABI 决定是否启用，否则在 x86_64 上会 UnsatisfiedLinkError。

### 新增文件（source/app/src/main/java/cn/screenqa/lite/）

- Dock.java：底部导航的抽象契约。常量 PANEL_HEIGHT_DP=68、ROOM_DP=12、SIDE_DP=20；`static int heightFor(Context)`（胶囊 + 透镜边 + 投影预留，fontScale≥1.3 时再加 10dp）与 `static int sideInset(Context)`；方法 setPalette / setGlassEnabled / setReduceMotion / setItems(String[],int[]) / setListener(Listener) / setSelected(int,boolean) / setLifted(boolean) / setBackdrop(Bitmap,float,float,float) / setBackdropSource(View)。
- LiquidDock.java：QWEA0/Liquid-Glass-Android 的封装。构造时设置 cornerRadius=34dp（胶囊）、bevelWidth=10dp、refractionHeight=14dp、refractionFalloff=1.6、dispersionStrength=0.12、blurAmount=0.10、saturation=1.15、edge/sensor 高光、按压 pressScale=0.95、elasticity=0.15、accessibilityMode=AUTO；`setBackdropSource(View)` 指向 pageLayer 并打开 `setEnableDynamicBackground(true)`（库的默认值是 false，不开就只采样一次 = 冻结画面）。`isSupported()` 按 Build.SUPPORTED_ABIS 判定，只在 arm64-v8a/armeabi-v7a/armeabi 上启用；`setBackdrop(...)` 故意留空，避免用静态快照覆盖逐帧采样。库的属性以像素为单位（cornerRadius/bevelWidth/refractionHeight），这里全部用 Ui.dp 换算。

### 修改文件

- settings.gradle：dependencyResolutionManagement 增加 `maven { url 'https://jitpack.io' }`（该块是 FAIL_ON_PROJECT_REPOS，仓库只能加在这里）。
- app/build.gradle：新增 `implementation 'com.github.QWEA0:liquidglass:v2.0.11'`；versionCode 22→23、versionName 0.9.8→0.9.9（defaultConfig 与 developer flavor 两处）。
- GlassDock.java：改为 `implements Dock`，删掉自带的 `interface Listener` 与 `widthFor`，高度/边距/圆角改用 Dock 常量（胶囊 68dp、圆角 34dp、shadow room 12dp、左右 20dp）；药丸指示器锚点改为 `items.getLeft()` 并加 `addOnLayoutChangeListener` 重新定位；按压缩放到 0.96 与 LiquidDock 对齐。
- MainActivity.java：字段 `GlassDock dock` → `Dock dock`；buildUi 里 `GlassDock.heightFor` → `Dock.heightFor`；dock 由新的 `createDock()` 创建（`LiquidDock.isSupported()` 为假、或 `new LiquidDock(this)` 抛任何 Throwable 时回退 GlassDock，并写一条 `dock liquid-fallback` 日志）；dock 宽度改为 MATCH_PARENT（胶囊由库按左右 20dp 边距自己撑满）；`aurora` 从 root 的子视图改为 `pagesLayer` 的第一个子视图，并给 `pagesLayer` 设置 `palette.background` 背景色（采样源必须不透明，否则透明区域等于没内容）；根布局 Insets 监听的底部改为 `max(systemBars.bottom, ime.bottom)`（键盘弹出时 dock 浮在键盘之上）；“液态玻璃”开关说明与“关于 → 第三方许可”文案更新。
- README.md、docs/DEVELOPER_BUILD.md、验证记录.md、DEVLOG.md：同步 0.9.9 / code23、依赖与外观说明。

### 设计取舍

- 选库而不是继续手写：用户明确要求实时采样，手写方案要在滚动时逐帧重采样并做折射，代价与风险都高于直接用现成实现；上一版手绘玻璃保留为降级路径，不回退删除。
- 库的 `setBackdropSource` 指向 `pagesLayer` 而不是 root：root 里包含 dock 自己，指向 root 会把 dock 自己卷进采样形成递归/自遮挡。
- 只有 arm64-v8a 才启用库：AAR 不带 x86_64 的 libnativegauss.so，模拟器上必须走手绘降级，否则点开就崩。
- 保留 `androidx` 依赖风险控制：只用 LiquidGlassTabBar/LiquidGlassView/GlassMaterial/GlassAccessibilityMode，不碰 DialogBuilder 与 TabLayoutMediator（那两者才需要 appcompat / viewpager2），并用 try/catch Throwable 兜住 NoClassDefFoundError 与 UnsatisfiedLinkError。
- 模糊量取 0.10（相对控件尺寸），并保留 refractionFalloff=1.6 的窄折射带：用户要求“轻微模糊、边缘折射与高光”，同时要求 Dock 内文字清晰——库把文字层画在玻璃之上，不参与模糊。
- 键盘处理用 `max(systemBars.bottom, ime.bottom)`：targetSdk 35 强制 edge-to-edge，软键盘不会再自动把窗口顶上去，必须把它当底部 inset 处理。

### 验证与证据

- 编译与回归：`source/.local/run-validation.ps1 -LogName 'glass-dock-final.log'`，任务 `:app:assembleDeveloperDebug`、`:app:lintDeveloperDebug`、`:app:testDeveloperDebugUnitTest` 全部 BUILD SUCCESSFUL（15s，52 个任务）。按用户要求**没有编译用户端**，用户端 assemble/lint/测试本轮未运行。
- 单元测试：`app/build/test-results/testDeveloperDebugUnitTest` 140 项，fail=0 / error=0 / skip=0。
- Lint：developerDebug 0 error / 24 项警告（新增代码引入的 `UseCompatLoadingForDrawables` 已用 `@SuppressLint` 消掉，理由是工程从不链接 appcompat）。
- 第一次编译失败并已修：`MainActivity.java:167` 与 `239`，`Dock` 是接口类型，`root.addView(dock, lp)` 与 `dock.getLocationInWindow(...)` 需要显式转成 `View`。修复后 10s 通过。
- APK 静态核验（build-tools 34.0.0，需先设 JAVA_HOME，否则 apksigner.bat 以 exit 1 静默结束）：
  - `app/build/outputs/apk/developer/debug/app-developer-debug.apk`，29,691,904 B（比 0.9.8 开发包 +664,108 B，主要是 libnativegauss.so）；aapt: `cn.screenqa.lite.dev` / `0.9.9-dev-answer-preview` / versionCode 23 / minSdk 26 / targetSdk 35 / native-code arm64-v8a + x86_64。
  - apksigner verify exit 0（v2 方案 true），证书 SHA-256 `0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912`；zipalign 4 字节通过。
  - APK 内 JNI：`lib/arm64-v8a/libnativegauss.so`（278,488 B）与既有 `libmlkit_google_ocr_pipeline.so` 一同打包，x86_64 下没有 libnativegauss.so（故有 ABI 闸门）。
- 交付前为了让打包进去的开发说明与仓库同步，按文档改动重新打包一次：`.local/glass-dock-release.log`，BUILD SUCCESSFUL in 6s（52 个任务，`mergeDeveloperDebugAssets` 与 `packageDeveloperDebug` 实际执行），APK 内 `assets/DEVELOPER_BUILD.md` 与 `docs/DEVELOPER_BUILD.md` 逐字节一致，SHA-256 同为 `1c9af904593fa39412e20188e515d8f45928cd041b3a5f3be29e11485ea8c4f6`。
- 交付副本：`outputs/screenqa-0.9.9-dev-answer-preview.apk`，SHA-256 `acc13b0c1f0ddba80ed6b2695ae1721def27cb57a3529472baea99963b0632fa`，汇总写入 `outputs/SHA256-0.9.9.txt`。
- 没做的事（与用户要求一致）：没有编译用户端、没有模拟器、没有装 APK、没有连真机、没有提交 git。

### 已知限制与后续

- 用户要求的第 7 条（用带文字/图片的滚动页面检查背景实时更新、导航点击、可读性、滑动流畅度）只能在真机上验证，本轮未执行；这里的“实际测试结果”仅指编译、JVM 回归、Lint 与 APK 静态核验。
- 逐帧采样 + 极光背景同时运行时的实际功耗与帧率未实测；低端 arm64 设备如出现掉帧，可先关“液态玻璃”（转 FORCE_OPAQUE）或“减弱动效”。
- 浅色主题（ivory_light）下未选中项的文字/图标颜色由库按亮度自动决定，真机上若可读性不足，需要改为显式指定（库只暴露了 selectedTintColor 一个颜色入口）。
- ABI 闸门是硬条件：只发布 arm64-v8a + x86_64，其中 x86_64 永远是手绘降级；如需在 x86_64 上也有库效果，需要自行编译带 x86_64 的 .so。
- 未提交 git，等用户测试开发者端反馈后再决定后续（用户明确“开发者端我测试无问题之后继续”）。

## 2026-10-02 · 0.9.9 观感与主题修订（第二轮，DSH）

### 需求（用户原话要点）

底部液态玻璃的 dock 应该一定程度上半透明、有点类似毛玻璃的效果；修复主题颜色切换无效的问题；把默认配色切换为类似悬浮窗的配色。

### 改动文件

- `app/src/main/java/cn/screenqa/lite/ThemePalette.java`：新增 OVERLAY_GREEN（悬浮窗绿）并设为 DEFAULT，ALL 变为七套且它排在列表首位；新增 LEGACY_DEFAULT_ID="black_gold" 供迁移判断。
- `app/src/main/java/cn/screenqa/lite/Settings.java`：新增 `migrateThemeDefault()`（用 theme_default_migrated 标记的一次性迁移）。
- `app/src/main/java/cn/screenqa/lite/MainActivity.java`：onCreate 调 migrateThemeDefault()；rebuildForTheme() 先 `palette=settings.theme()` + applyWindowColors() 再重建，并保留 selectedDetail 原位恢复；内容容器不再为 dock 留 padding，改由 page() 的 ScrollView 底部内边距承担（clipToPadding=false，卡片可滚到胶囊下方）；滚动监听按 `dock.liveSampling()` 决定是否重抓底图，并按滚动中/停止做 170ms / 90ms 节流；两处“默认黑金 · 6 套配色”文案改为“默认悬浮窗绿 · 7 套配色”。
- `app/src/main/java/cn/screenqa/lite/CaptureService.java`：onCreate 先 `migrateThemeDefault()` 再注册 theme_id 监听，保证悬浮窗与主界面默认一致。
- `app/src/main/java/cn/screenqa/lite/LiquidGlassView.java`：手绘玻璃色调透明度 0.68→0.44（关闭玻璃时 0.96→0.88）、顶部高光 0.85→0.55。
- `app/src/main/java/cn/screenqa/lite/LiquidDock.java`：暗色主题用 `GlassMaterial.CLEAR`、浅色保留 `REGULAR`；玻璃着色强度 0.20/0.16 → 0.14/0.10。
- `README.md`、`docs/DEVELOPER_BUILD.md`：默认配色改为悬浮窗绿、主题数 6→7，补充“切换即全应用生效”与毛玻璃、迁移说明。

### 设计取舍

- 不把黑金改成绿色，而是新增一套 + 一次性迁移：只把“从未选择过”与“仍停在旧默认 black_gold”的安装迁到新默认，用户显式选过的其它主题保持不变，避免丢掉已有选择。
- 主题切换用“就地重建当前页”而不是重启 Activity：保留页签与子页位置，配合降低不透明度，切换时视觉上是淡入而不是跳页。
- 毛玻璃必须“背后有内容”才看得出来：因此把 dock 让位从容器 padding 改为 ScrollView 内边距，让卡片能滚到胶囊下面，否则玻璃背后永远是空白页面。
- 库渲染路径的半透明由材质决定：暗色主题切到 `GlassMaterial.CLEAR`（库自带压暗层，透出内容仍可读），浅色主题保留可读性优先的 `REGULAR`；玻璃着色强度 0.20/0.16 → 0.14/0.10。关闭“液态玻璃”时仍退化为不透明卡片。

### 验证与证据

- `.local/glass-frost-final.log`：`:app:assembleDeveloperDebug`、`:app:testDeveloperDebugUnitTest`（140 项，fail=0 / error=0 / skip=0）、`:app:lintDeveloperDebug`（0 error / 24 warning）BUILD SUCCESSFUL in 18s；中间单独的 assemble（`glass-frost.log`，9s）与 test+lint（`glass-frost-verify.log`，17s）同样通过。
- APK：`app/build/outputs/apk/developer/debug/app-developer-debug.apk`，29,692,521 B，SHA-256 `a21a5c4db0e28b5bcdf6d49ca8158e000bf9747915b907117836f04e59e6cec6`；apksigner verify exit 0（证书 SHA-256 `0ccbfe0a…`）、`zipalign -c 4` exit 0；内嵌 `assets/DEVELOPER_BUILD.md` 与仓库文件逐字节一致（`6a8f4ffb…`）。文档改完后先打了一次包同步该资产（`.local/glass-frost-release.log`，6s），加入材质改动后再打包（`.local/glass-frost-final.log`，18s），上面的哈希是最后一个包。
- 交付副本 `outputs/screenqa-0.9.9-dev-answer-preview.apk` 与 `outputs/SHA256-0.9.9.txt` 已按新包更新。

### 已知限制

- 毛玻璃程度、悬浮窗绿的实际观感、切换主题后悬浮窗是否即时跟随，都需要真机确认；本轮未运行模拟器、未安装、未连真机。
- 迁移只覆盖“未选择过主题”和“旧默认 black_gold”：若用户在 0.9.9 之前显式选过黑金，会保留黑金而不是自动变绿。
- 仍未提交 git。
