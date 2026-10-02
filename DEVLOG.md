# DEVLOG · 看题助手 Lite

本文件是项目交接入口。从 0.8.1 起，每完成一个版本，必须在文件末尾追加完整版本记录，保留所有历史段落，不覆盖旧版记录。记录必须包括功能变化、修改文件、实现方式、关键逻辑、Bug 修复、验证证据、已知问题及后续建议。修订历史错误时追加勘误。

## 项目基线与阅读地图（首次建档）

- Android 原生 Java 应用，包名 `cn.screenqa.lite`，minSdk 26、target/compile SDK 35；Gradle 8.9 / AGP 8.7.3 / JDK 17。APK 包含 arm64-v8a、x86_64，不含 32 位 ABI。
- 上一交付为 0.8.0 / versionCode 9。本文件从 0.8.1 建立；以下旧版本摘要是交接背景，不能视为重新执行过旧版测试。
- 0.6.0：单选/判断自动选择；0.7.x：连续答题及点击目标修正；0.8.0：文本题填写、四题型执行开关、复制按钮、手动框选降为兼容入口。历史安装包、源码包保留在原聊天 outputs，未覆盖。
- 默认链路：屏幕共享 → 优先读取无障碍节点或 ML Kit 中文 OCR → 本地题目区域定位 / DeepSeek 语义兜底 → DeepSeek 生成答案 → 按题型设置执行 → 校验 → 等待页面变化 / 下一题 / 有限滚动。
- `CaptureService.java` 是悬浮窗、识屏、请求生命周期、连续答题状态机的总协调器。`ScreenQaAccessibilityService.java` 管节点采集、点击、文本目标定位、下一题和滚动。`TextAnswerExecutor.java` 执行文本写入与回读。`TextQuestionIdentity.java`（0.8.1 新增）统一文本题身份判断。
- `ScreenDocument.java` 保存带坐标的文字行并过滤常见导航/计时器。`LocalQuestionLocator.java` 做保守本地分组。`QuestionDetection.java` 校验模型返回的题型、行号、答案。`QuestionTracker.java` 做连续两帧稳定、请求代次、成功去重和最多三次请求重试。
- `ApiRequest.java` 调用 `Settings.ENDPOINT=https://api.deepseek.com/chat/completions`，当前代码固定模型 `deepseek-flash`，`thinking.type=disabled`，文本题生成可直接填写的答案。本版未改模型或请求协议。
- `AnswerTargetResolver.java` 处理单选/判断点击目标；`NextButtonMatcher.java` 仅识别下一题、下一页等，不点击交卷/提交/结束考试按钮。`TextAnswer.java` 处理文本答案显示及复制。
- `MainActivity.java` 是设置和启动入口；`Settings.java` 用名为 `settings` 的 SharedPreferences 保存开关、策略及加密 Key；Key 用 Android Keystore AES/GCM，不在源码或日志中记录。总开关 `auto_select` 默认 false；`auto_choice`、`auto_true_false`、`auto_fill_blank` 默认 true；`auto_short_answer` 默认 false。
- `QaLog.java` 写应用内部 `files/Q&A/qa_*.log`，256 KiB 轮转，最多 8 个。设置页可查看/导出。日志会记录部分题目答案及运行信息，诊断时只索取复现当次文件，不能据旧日志推断新版实际结果。
- 总开关或题型开关关闭：继续识题、请求答案和显示；不填写、不点击、不自动跳题。填写失败保留答案供复制。手动框选是 Legacy fallback，不作为新增自动执行能力的适配重点。

---

## 0.8.1 / versionCode 10 · 2026-09-30 · 通用流程收尾与专用 APP 占位

### 本版目标与开发范围

处理用户反馈的填空题获得答案后无法续答、启动时没有主动引导无障碍授权。增加“某 APP 专用自动答题”占位入口。从本版开始建立追加式 DEVLOG。按用户决定，通用自动答题的新增功能开发暂告一段落，后续优先针对指定 APP 适配。没有配置任何具体目标包名或冒充已经实现专用模式。

### 日志证据、根因与判断边界

本轮读取用户指定 `qa_20260930_232851_001.txt`（0.8.0，Android 16 / SDK 36）：

1. 23:28:57 启动记录 `auto_select=true accessibility_connected=false`，23:29:04 才出现 `ACCESSIBILITY connected`。旧 `MainActivity.startCapture()` 只检查悬浮窗、通知、录屏权限，没有检查已保存的自动执行开关所需无障碍权限。旧悬浮窗临时开启总开关虽会跳系统设置，却会把开关恢复成关闭，授权回来后还需再次开启。
2. 23:33:00 与 23:33:02 两次返回 `type=fill_blank answer=蒸腾作用`，均以 `TextInput failed reason=question_changed_or_stem_not_exposed TargetInputCount=0 completed=0` 结束。没有文本写入成功或 `text_input_verified` 记录，因此实际首先失败在填写前核对，不能把日志描述为“已经自动填写成功但下一题失败”。
3. 旧填写核对只取 OCR 中最长一行的字母数字，要求它完整出现在节点树；节点树又只允许 180 节点 / 14 层 / 100ms。断行、标签、编号、输入槽文字或树截断容易造成失配，且旧日志未区分是哪一种。旧日志只给输入节点计数，未含节点正文，不能确定该页面是否真正暴露可编辑控件。
4. 旧已答题去重有两套不一致判定：完整题干与插入 `1:...|2:...` 行号的 fingerprint 比较，以及仅保留下划线之前最长前缀。前者会被多行打断，后者会把相同前缀的另一题判成同题。下一题查找又使用包含下划线等排版的原始完整题干。填写改变输入内容后可能误判页变更，或重复生成同题答案。
5. 旧 `frame()` 在答案弹窗盖住题干且 `nextPending=false` 时直接停止 OCR。填写失败/手动等待分支正是该条件，答案弹窗可能阻断后续换题监测。

### 功能变化与实现方式

**文本题身份与续答**

- 新增纯 Java `TextQuestionIdentity`：从模型/本地定位选中的题干行构造身份，去独立题型标签和行首题号，NFKC 统一全角字符，拼接 OCR 断行，按下划线、空括号等空位拆成有序固定片段。允许空位内出现填写文字，要求所有固定片段依次匹配；至少六个有效字符；保留数字、负号、小数点及常见运算符，防止把不同数值题当成同题。
- 比较直接使用文档正文，不使用带人工行号的 fingerprint。填写前验证、填后去重和文本题下一题检查共用此身份。保留题干后半部分，避免只看第一空之前的前缀。选择题原目标解析器和坐标/节点点击链路未重写。
- 无障碍采集增加按需 detailed 模式，文本填写和文本导航使用最多 600 节点 / 24 层 / 300ms；常规扫描保持 180 / 14 / 100ms。截断会显式记录并拒绝文本写入，不把不完整树当完整页面。未遍历的已获取队列节点及时 recycle。
- 排序节点文字并将可编辑框的当前答案从识题正文中排除。对于本地已可靠定位且存在输入框的填空/简答题，允许提供节点候选供快速解题，减少只走 OCR 的机会；不对任意输入框猜测题干。
- 仍要求当前题干匹配、答案数与附近输入框数一致、逐框重新读树、同包名、当前 epoch/设置代次有效，才写入。首选 ACTION_SET_TEXT，失败仍只允许原有安全粘贴兜底。回读改为最多四次，间隔 60ms，最长额外等待 180ms，运行于服务 worker，避免把 WebView 异步更新过早判失败。
- 只有全部输入回读成功才进入 `WAITING_PAGE_CHANGE → FINDING_NEXT`。导航前自动收起文本答案卡防止盖住按钮；原答案仍保存在悬浮球详情和复制入口。节点树暂时缺失/文本树截断最多追加两次 250ms 重试；下一题按钮仍最多点击一次、滚动仍最多两次。
- 导航 worker 在点击/滑动前检查暂停、销毁、总开关、epoch、执行代次和当前题目，防止旧回调执行。类型切换回选择题时清理文本身份，避免污染原选择题导航。
- 填写失败或题型自动执行关闭时保留题目身份，不自动跳题。答案卡遮挡旧题干超过既有 4.5 秒显示期后自动收起以恢复监测；完整答案和复制按钮可再从悬浮球打开。暂停/重新开始显式清理已答题去重状态，允许用户主动重试。

**无障碍授权引导**

- `MainActivity` 开启自动执行总开关、自动执行开启时点击“开启悬浮窗”、悬浮窗内开启自动执行，都会检查服务连接并主动弹出授权说明。
- 新增“启用 / 检查无障碍服务”按钮和三态提示：未启用、已启用但未连接、已连接。
- 新增 `AccessibilityPermission`：优先尝试系统服务详情页；ROM 不支持时回退标准 `ACTION_ACCESSIBILITY_SETTINGS` 列表并传服务定位参数；没有可用设置 Activity 时明确提示，不崩溃。
- 系统授权页返回后检查真实 `ScreenQaAccessibilityService.active`，已启用但连接尚未完成时最多等待两秒。因启动悬浮窗触发的授权成功后继续原启动流程；取消或未开启则停止继续，不循环弹窗。已有的悬浮窗/通知/录屏权限流程保持。
- 悬浮窗授权前保留用户已开启的总开关意图；返回后同步设置，不要求再开一次开关。服务标题从“看题助手自动选择”改为“看题助手自动答题”，服务组件类名未变；增加文本变化/焦点事件监听，描述涵盖填写与切题。
- Android 无障碍需要用户在系统页面明确开启，应用不能静默授予。本版“主动申请”是主动说明、跳转、核验并续接。官方参考：https://developer.android.com/guide/topics/ui/accessibility/views/service 。

**APP 专用占位与交接约定**

- 设置页新增“某 APP 专用自动答题 · 待开发”和“查看专用答题计划”，点击只展示说明，不启动当前通用执行器、不保存伪目标应用、不请求额外权限。
- 后续确定目标 APP、包名和版本后，从此入口接入专用识题/填写/切题流程。优先复用 DeepSeek、答案模型、执行开关、日志和显示模块；本轮不预建未使用的适配框架。
- 根目录新增本 `DEVLOG.md` 和 `AGENTS.md`，将每次版本完成后追加开发日志、保留历史、明确实测与未测范围写为后续工作规则。

### 修改文件清单

| 文件（相对项目根目录） | 变化 |
| --- | --- |
| app/build.gradle | 0.8.1 / versionCode 10 |
| app/src/main/java/cn/screenqa/lite/TextQuestionIdentity.java | 新增统一文本题身份匹配 |
| app/src/main/java/cn/screenqa/lite/CaptureService.java | 同题去重、弹窗遮挡恢复、文本切题重试、过期导航拦截、授权入口衔接 |
| app/src/main/java/cn/screenqa/lite/ScreenQaAccessibilityService.java | 详细节点采集、截断诊断、节点候选、文本核对及导航取消检查 |
| app/src/main/java/cn/screenqa/lite/TextAnswerExecutor.java | 有界异步回读等待 |
| app/src/main/java/cn/screenqa/lite/AccessibilityPermission.java | 新增系统授权页跳转与启用状态查询 |
| app/src/main/java/cn/screenqa/lite/MainActivity.java | 主动授权引导、状态和手动入口、返回续接、专用 APP 占位 |
| app/src/main/AndroidManifest.xml | 无障碍服务显示名称更新 |
| app/src/main/res/xml/accessibility_service.xml | 增加文本变更及焦点事件 |
| app/src/main/res/values/strings.xml | 无障碍用途描述更新 |
| app/src/test/java/cn/screenqa/lite/TextQuestionIdentityTest.java | 11 个文本身份与换题回归用例 |
| README.md、验证记录.md | 当前版使用说明与验证边界 |
| DEVLOG.md、AGENTS.md | 追加式版本交接及维护约定 |

### 验证结果与复现方式

- 最终 `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest` 成功；52 项 JVM 测试全部通过，0 failure / 0 error；Lint 0 错误、17 警告（旧式 API/样式等提示仍在报告中，不宣称零警告）。
- 新增 11 项覆盖：多行 OCR 对合并节点；空位填前/填后同题；相同前缀不同后缀的新题；fingerprint 行号干扰；全角/空括号；有效数字变化；缺失题干；仅题型标签；手动编辑答案；片段顺序；负号/小数保护。原 41 项识题、选项、下一题按钮及文本答案测试继续通过。
- APK v2 签名和 zipalign 4 字节校验通过。证书 SHA-256：`b5cbd72bb2fb3dee36f28fc0216d94c52f1462233f985f69c898458278611c73`，与 0.8.0 相同，可覆盖升级。源码包不包含本地密钥、Gradle 缓存、构建输出或用户日志。
- 构建期间首轮沙箱拒绝读取既有 Gradle 依赖，后使用获准的原有离线工具链完成构建；修复了一处 popup 局部变量被 lambda 捕获的编译错误。这里记录的通过结果指最终源码。
- 本环境没有 Android emulator/system-images，未启动模拟器、未连接真机、未调用真实 DeepSeek API。因此 JVM/编译结果不等于手机端端到端验证；日志中失败页面是否已被完全修复仍待用户复测。
- 标准可移植构建：安装 JDK 17、SDK platform/build-tools 35，配置 ANDROID_HOME，项目根运行 `gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`。本机复用上一聊天 `work/build.ps1`，其工具链位于 Documents/Codex/2026-09-29/ban-2/work/toolchain，Gradle 以 --offline 运行。分发源码不包含 `.local/debug.keystore`，新环境生成的 debug 签名不能覆盖本机旧包，需保留开发者自己的签名文件。
- 测试报告位于 `app/build/test-results/testDebugUnitTest/`；Lint 位于 `app/build/reports/lint-results-debug.xml`。本版交付 APK、源码 ZIP、DEVLOG 副本、使用说明、验证记录及 SHA256 文件。

### 真机复测建议、已知问题与后续方向

1. 覆盖升级后关掉系统无障碍，开启总开关/点击启动，确认出现引导；分别测试取消、返回未授权、授权成功、已启用但连接延迟，以及授权后原开关保留。部分 ROM 或侧载限制可能仍需要用户在系统设置允许受限设置；未实测所有 ROM 的服务详情页。
2. 单空、多空：取得答案 → `TextInput tree ... stem_match=true` → 输入节点数与答案数一致 → `TextInput success` → `NextQuestion transition reason=text_input_verified` → 下一题按钮或有限滚动 → 新题继续答题。
3. 若仍停留，导出新版最新日志，优先看 `no_application_root`、`incomplete_accessibility_tree`、`stem_not_matched`、`input_answer_count_mismatch`、`write_or_verification_failed`。旧日志的 TargetInputCount=0 发生在匹配检查之前，不足以证明实际没有输入框；新版会在核对前统计树中可见 editable。
4. 原生控件不暴露节点、PWA/WebView 虚拟树缺失、Canvas 输入、复杂公式、键盘造成布局位移、多题混排、题干完全相同但题号不同等仍属通用方案边界。身份匹配不做任意 OCR 错字模糊容忍；固定片段无法匹配时宁可显示答案并等待用户操作，不盲目填写或跳题。
5. 简答题用户尚未实测，仍默认关闭自动填写；本版共享了核对与回读修复，没有声称简答题真机通过。开关关闭时必须显示和复制答案、等待手动操作，不能自动跳过。
6. 选择题日志存在早期节点过期后回退失败及 900ms 导航确认偏短的记录，后续识题仍继续；本轮未重构既有选择题链路。仅此类实际反馈再安排针对性修复。
7. 通用自动答题停止主动扩展；下一步先收集指定 APP 的包名/版本、典型单空/多空/简答页面、授权环境与成功/失败日志，再建立专用适配。继续复用四题型开关和公共模块。不要扩展 Legacy 手动框选。
8. 每次发布后在本文件末尾追加新版本，注明最终修改文件、关键条件、测试数字及未测项；保留本节和全部旧记录。不要用“已全部修复”掩盖缺少设备验证。

---

## 0.8.2 / versionCode 11 · 2026-10-01 · 主界面重构与日志记录控制

### 本版目标

重做 APP 主界面布局、层级、配色、图标与交互，保留 0.8.1 的答题与权限逻辑。日志增加“开始记录 / 停止记录”和实时状态。通用答题后续开发仍暂停；某 APP 专用模式仅保留待开发入口。

### 功能变化与实现方式

- `MainActivity` 使用原生 Android View 重组为底部“首页 / 设置 / 日志”三页，页内容各自可滚动，底部导航和顶部品牌栏固定。复用已有 `ic_app`、`ic_auto`、`ic_settings`、`ic_article` vector 图标，不引入第三方 UI/图标/动效依赖。
- 首页以渐变主卡突出“开启/停止悬浮助手”，运行状态随 `CaptureService.active` 更新。无障碍和日志状态用独立信息行显示；无障碍授权仍走 0.8.1 的主动引导。某 APP 专用答题保留为明确标注“待开发”的次要卡片，点击只显示计划说明。
- 设置页分为“自动答题”“DeepSeek 连接”“识别方式”。总开关、四个题型开关继续调用原 `Settings` 存储与 `AUTO_SETTINGS` 刷新；关闭执行仍生成/显示答案。Key 仍用 Keystore 加密，保存、连接测试、三种策略切换及悬浮窗运行时的 `STRATEGY` 刷新都保留。低频识别策略默认折叠。
- 日志页显示记录状态和一个随状态切换文案的控制按钮；查看、导出最新、清除功能保留。清除操作加确认对话框。操作反馈在顶部短暂淡入显示，页面切换使用约 150ms 淡入和轻微位移；避免后台识题状态下长期执行 UI 动画。
- 布局在实际窗口宽度内计算内容宽度，大屏最大约 640dp，窄窗口或横屏跟随容器缩放。正文在滚动区域内，底部导航固定；系统 Insets 负责状态栏和导航栏边距；输入 Key 时窗口 `adjustResize`。`values-night/styles.xml` 与运行时配色保持系统暗色模式和弹窗主题一致。
- `QaLog` 新增 `qa_log_recording` 布尔设置（同一 `settings` SharedPreferences，默认 true，保持旧版默认记录）。关闭时先排队写入 `LOG_RECORDING stopped`，然后停止接受新 `QaLog.event`，已有文件可读可导。重新开启创建新日志并写 `LOG_RECORDING started`。记录状态在进程重启后保留。
- 日志任务使用事件提交时的文件引用，确保异步队列中停止前已接收的事件不会写进重新开启后的新文件。清除操作以 `clearing` 栅栏暂时阻止新记录，等待旧队列写完再删除，结束后仅在记录开启时创建新文件并写 `LOGS_CLEARED`；停止状态下清除不会自动恢复记录。原 256KiB 换卷、8 文件和 2MiB 上限继续生效。

### 修改文件清单

| 文件 | 变化 |
| --- | --- |
| `app/build.gradle` | 版本更新为 0.8.2 / versionCode 11 |
| `app/src/main/java/cn/screenqa/lite/MainActivity.java` | 三页主界面、导航、卡片、状态反馈、动效与响应式宽度；保留原设置和启动权限流程 |
| `app/src/main/java/cn/screenqa/lite/QaLog.java` | 可持久化记录开关、异步文件会话、清除时防止旧日志重现 |
| `app/src/main/AndroidManifest.xml` | 主界面键盘弹出时调整可见区域 |
| `app/src/main/res/values/styles.xml` | 浅色主题色和系统栏配色 |
| `app/src/main/res/values-night/styles.xml` | 新增深色弹窗和系统栏主题 |
| `README.md`、`验证记录.md` | 使用步骤及验证边界更新 |
| `DEVLOG.md` | 追加本版记录，0.8.1 与背景历史完整保留 |

### 保留的核心逻辑

`CaptureService`、`ScreenQaAccessibilityService`、`ApiRequest`、`QuestionTracker`、`QuestionDetection`、`TextAnswerExecutor`、`TextQuestionIdentity` 和答题相关配置逻辑未改。主页启动继续保存 Key、检查无障碍/悬浮窗/通知/录屏权限并启动前台服务；设置页仍可独立启用各题型。打开历史日志与导出最新仍从应用私有 `files/Q&A` 读取。手动框选继续作为悬浮窗内的 Legacy fallback。

### 构建与验证

- 执行项目既有离线构建 `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest`，最终成功。52 项 JVM 测试全部通过，0 failure / 0 error；Lint 0 错误、15 警告，其中主界面两条为原生 `Switch` 替换建议。考虑新增依赖的成本，仍使用 Android 原生 Switch。
- APK v2 签名验证和 4 字节 zipalign 检查通过；证书 SHA-256 为 `b5cbd72bb2fb3dee36f28fc0216d94c52f1462233f985f69c898458278611c73`，与 0.8.1 一致，可覆盖安装。构建使用 JDK 17 / SDK 35 / Gradle 8.9，未添加依赖。
- 代码层面检查了旧版设置、授权、连接测试、日志查看/导出、占位入口的操作路径。没有 Android 模拟器系统镜像，未连接真机，也未通过真实 DeepSeek 请求检验页面；构建与 JVM 回归不能证明设备上的视觉与触控布局。

### 已知问题与后续建议

- 系统 ROM、字体放大、横屏与分屏下的最终视觉表现需要在设备上确认；优先看底部导航、Key 输入框、长中文提示和系统弹窗。支持内容滚动与动态宽度，但未做设备截图或无障碍自动化测试。
- 日志关闭后无法追溯关闭期间的诊断事件，这是按钮预期语义。若需排查答题问题，应先在日志页开启记录，复现后导出最新文件。停止前已进入写队列的少量事件仍会写完；新事件不再接收。
- 日志清除仍会删除已有本机文件，现需二次确认。原 8 文件 / 总约 2MiB 留存策略不变。
- 0.8.1 填空续答仍需用户手机复测，简答题自动填写仍默认关闭且未真机验证。主界面改版不代表这两项已完成设备端验收。
- 专用 APP 入口仍是占位；待确认包名、版本与典型页面后再设计适配。下一版本继续按本日志末尾追加的规则记录变更，保留此前全部内容。

---

## 0.8.3 / versionCode 12 · 2026-10-01 · 产品化信息架构与低风险启动优化

### 功能变化、信息架构和关键逻辑

- 底部导航改为“首页 / 设置 / 关于”。首页只保留悬浮助手启动/停止、服务状态、当前答案和通往权限说明的入口。最新答案来自 `CaptureService.currentAnswer`；服务仅在答案内容变化时通知当前可见的 `MainActivity`，页面暂停后移除弱引用观察者，不做轮询。首页为避免长文本撑开首屏只显示前 600 字，完整答案仍在原悬浮窗中。
- 设置按“答题与识别 / 运行与权限 / 更多”分组，次级页面分别为自动答题、识别、AI、悬浮窗、Root 占位、日志与诊断、高级设置、权限说明、某 APP 专用占位。设置、关于和次级页按首次打开创建，底部导航、返回按钮和系统返回键可回退。保留原总开关、四题型开关、识别策略、Key 保存和连接测试的存储及服务通知行为。
- 权限说明展示无障碍、悬浮窗、通知状态及用途，可调用既有无障碍引导或打开系统悬浮窗权限页；屏幕共享注明按次由系统授权。权限状态在页面返回时刷新。Root 和指定 APP 专用答题仍仅为说明占位，未实现或启动任何增强功能。
- 关于展示版本，并为开源信息、隐私政策、用户协议、第三方许可建立明确“待提供”入口；没有虚构正式政策 URL 或许可清单。
- Q&A 日志移入“日志与诊断”，保留持久化的开始/停止记录、查看、导出和确认清除。手动框选从悬浮窗主面板移到“高级设置”；运行中的服务收到 `MANUAL_SELECT` 后调用原 `select()`。未修改选区识别、OCR、DeepSeek、自动点击/输入/滑屏的实现。自动定位错误提示对应改为指向高级设置。
- 复用原生 View、现有 vector 图标、短暂淡入状态过渡与系统 Ripple。页面宽度仍按可用窗口计算并在大屏限制为 640dp；没有添加 UI、图标或动效依赖。

### 性能分析与实施

- 基线 0.8.2 的 `MainActivity.onCreate` 在首屏前创建首页/设置/日志三棵页面树，并在设置构建时调用 `Settings.key()` 解密 Keystore 密钥，同时同步执行 `QaLog.start()`。0.8.3 首屏仅创建首页；设置、关于和各次级页首次进入才创建，因此 Key 解密推迟到 AI 页，日志详情及文件操作推迟到日志页。
- `QaLog.startAsync()` 在首页布局启动后延迟 250ms 入既有日志 IO 队列；目录、偏好设置和版本查询不再由 `MainActivity` 在首帧前的主线程执行。`CaptureService` 启动时原有同步 `QaLog.start()` 不变，确保答题运行前有日志会话。
- `MainActivity` 的测试连接/导出执行器改为使用时创建，Activity 销毁时仅在已创建的情况下关闭。首页不再为日志状态调用 `QaLog.isRecording()`，也不读取无障碍系统设置；这些状态仅在对应页需要时查询。服务答案通知按内容变化发送，避免首页无意义重复刷新。删除 lint 确认未使用的 `ic_crop.xml`。
- APK 基线 0.8.2 为 **29,125,513 B**，最终 0.8.3 为 **29,125,335 B**，减少 **178 B**（约 0.0006%），属于无实际体积意义的变化；没有新增或移除依赖。仍保留 ML Kit 中文 OCR 与 JVM 测试依赖。首帧耗时、可交互时间、空闲 CPU/内存和主线程卡顿没有可用模拟器测量，不能报告毫秒或改善百分比；上述启动收益是静态执行路径分析，不是设备性能实测。

### 修改文件

| 文件 | 变化 |
| --- | --- |
| `app/build.gradle` | 0.8.3 / versionCode 12 |
| `app/src/main/java/cn/screenqa/lite/MainActivity.java` | 首页收敛、分类设置、About、权限说明、日志与高级入口、按需建页、延迟执行器和状态刷新 |
| `app/src/main/java/cn/screenqa/lite/CaptureService.java` | 只读答案状态通知、原手动选区入口迁移所需 action、相应 UI 文案；答题执行流程未改 |
| `app/src/main/java/cn/screenqa/lite/QaLog.java` | 使用既有 IO 队列异步启动首页日志初始化 |
| `app/src/main/res/drawable/ic_crop.xml` | 删除未引用的 vector 资源 |
| `README.md`、`验证记录.md` | 页面位置、验证数据和边界更新 |
| `DEVLOG.md` | 追加本版记录，保留历史 |

### 构建、检查与已知边界

- 使用既有 JDK 17、SDK 35、Gradle 8.9 离线构建运行 `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest`，全部成功。52 项 JVM 测试通过，0 failure；Lint 0 error / 14 warnings。APK 为 `cn.screenqa.lite` 0.8.3 / versionCode 12，arm64-v8a 和 x86_64；zipalign 4 字节校验通过，v2 签名有效，证书 SHA-256 `b5cbd72bb2fb3dee36f28fc0216d94c52f1462233f985f69c898458278611c73`，可覆盖同证书旧包。
- 代码路径检查了首页启动、设置分类、Key 保存/测试、权限跳转及返回、日志查看/导出/清除、手动兼容入口和系统返回。环境无 Android 模拟器镜像，按用户要求没有连接真机；未测 UI 截图、冷启动毫秒数、可交互时间、空闲 CPU/内存和真实答题端到端行为。上述检查不能替代设备上的触控、ROM 权限页、深色/横屏/大字体实测。
- 为稳定性没有改动 `ScreenQaAccessibilityService`、`ApiRequest`、OCR 调度、题目身份、自动点击/填写和下一题/滑屏算法；未尝试移除 ML Kit、引入大型框架或做未经量化的底层重构。原 0.8.1 填空连续切题仍待手机复测，简答题自动填写仍默认关闭且未实测。手动框选保留兼容入口但不继续适配新功能。
- 关于页的正式开源信息、隐私政策、用户协议和第三方许可清单需在对外发布前补齐；Root 和某 APP 专用功能仍未开发。本轮在此停止，不主动实现 Root。

---

## 0.8.4-dev / versionCode 13 · 2026-10-01 · Developer Build Token 与临时积分测试

> **后续接手者先读 `docs/DEVELOPER_BUILD.md`，再读本日志。当前 APK 是开发者测试版，不是普通用户正式版；临时积分比例不得视为商业规则。**

### 功能变化与关键实现

- 所有 DeepSeek 请求均通过 `ApiRequest.send()`；本版在读取完整 HTTP 200 JSON 响应后、解析答案前提取 `usage.prompt_tokens`、`usage.completion_tokens`、`usage.total_tokens` 原值。自动定位解答、手动兼容模式以及“测试连接”均覆盖。若回答因 `finish_reason=length` 被拒绝，只要响应有有效 usage，仍计入实际已消耗 Token。HTTP 错误、网络失败或非 JSON 响应无法取得有效 usage，不猜测用量。
- `TokenUsageTracker.parse()` 只接受三个均为非负整数的数值字段；不把文本长度、输入+输出之和或其他字段当作 `total_tokens`。缺少/无效 usage 的 HTTP 200 响应单独计数并写诊断事件，不计 Token 和 Credits。统计入账异常被隔离，不改变原答案解析、返回结果或错误处理。
- 开发版统计保存在应用私有 SharedPreferences `developer_token_usage_v1`：最近一次输入/输出/总 Token、当日总 Token、历史总 Token、请求次数、历史平均每次 Token、对应最近一次/当日/历史的临时积分，以及缺少 usage 的成功响应次数。记录用同步 `commit()` 在原请求后台线程完成，静态锁保护并发请求。今日以设备本地日期划分；跨日展示归零，历史值保留。仅存数字，不存题干、答案或 Key。卸载 App 会清除统计。
- `CreditCalculator` 是单独的 TEMP/TEST 换算模块，唯一比例配置 `TEMP_TEST_TOKENS_PER_CREDIT=100`，即本版 `credits=total_tokens/100`。Tracker 调用换算器，积分以百万分之一积分为内部整数单位随每次请求累计，显示时才转换为文本。改变未来比例不会重算旧请求已经累计的积分。这个积分是本机测试数据，非可购买、兑付或转移余额。
- “设置 → Token 与测试积分”仅在 Developer Build 出现，显示单次、今日、历史及平均用量和临时积分，提供手动刷新。主界面顶部与 About 显示“开发者版本 / Developer Build”。About 可打开随 debug APK 打包的 `assets/DEVELOPER_BUILD.md`。版本名为 `0.8.4-dev`，避免与普通 release 版本混淆。
- Gradle `BuildConfig.DEVELOPER_BUILD` 在 debug 为 true、release 为 false。Release 变体不显示统计入口、不会执行开发版 usage 入账，且不打包开发说明 asset；它目前只是可编译的对照变体，并非已经完成法律文本、商业规则和正式发布验收的产品。

### 修改文件

| 文件 | 作用 |
| --- | --- |
| `app/build.gradle` | 版本 0.8.4 / versionCode 13，debug 后缀 `-dev`，Developer Build 编译开关与仅 debug 打包的开发说明 asset |
| `app/src/main/java/cn/screenqa/lite/ApiRequest.java` | 从 HTTP 200 响应提取 usage，调用 Tracker；统计失败不影响既有 AI 请求结果 |
| `app/src/main/java/cn/screenqa/lite/TokenUsageTracker.java` | 严格解析三项 Token、持久化单次/今日/历史数据与缺失计数、调用临时积分换算 |
| `app/src/main/java/cn/screenqa/lite/CreditCalculator.java` | 集中配置和计算 TEMP/TEST 换算比例 |
| `app/src/main/java/cn/screenqa/lite/CaptureService.java` | 两处请求创建时传入应用 Context；OCR、识题和自动执行流程不变 |
| `app/src/main/java/cn/screenqa/lite/MainActivity.java` | 开发版标识、统计页面、About 开发说明入口；连接测试创建请求时传 Context |
| `app/src/main/res/values/strings.xml` | 统计页面格式化文本 |
| `app/src/test/java/cn/screenqa/lite/TokenUsageTrackerTest.java` | 用量字段解析、缺失拒绝、精确临时换算、平均值测试 |
| `docs/DEVELOPER_BUILD.md` | 长期开发说明，debug APK asset 唯一原文 |
| `AGENTS.md`、`README.md`、`验证记录.md` | 醒目标记 Developer Build 并指向开发说明，更新使用与验证边界 |
| `DEVLOG.md` | 追加本版内容，全部历史版本保留 |

### 验证与已知边界

- 离线执行 `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest` 成功；56 项 JVM 测试通过、0 failure；Lint 0 错误 / 14 警告。`assembleRelease` 也编译成功，但未签名或发布。生成的 debug `BuildConfig.DEVELOPER_BUILD=true`、release 为 false；debug APK 包含 `assets/DEVELOPER_BUILD.md`，release APK 不包含。
- 0.8.3 APK 为 29,125,335 B；本版 debug APK 为 29,141,974 B，增加 16,639 B（约 0.057%）。未增加外部依赖。最终 APK 签名、对齐、版本及 ABI 另见本版验证记录。
- 当前环境没有 Android 模拟器镜像，按约定未连接真机；没有发送真实 DeepSeek 请求。因此实际模型 Token 数量、不同题型成本与设备端页面交互仍待开发者实际请求验证。单元测试只验证解析和计算，不宣称已测真实 API 响应。统计开始于安装/升级本版后首次有效 usage 响应；旧版本历史请求无法追溯。
- 为稳定性未修改提示词、HTTP 参数、OCR、Accessibility、自动点击/输入和下一题/滑屏逻辑。正式商业化前必须重定价、决定可信服务端计量及异常处理、移除/隐藏真实 Token 展示、补齐隐私政策/协议/许可并另行验收；**禁止直接沿用本版临时比例**。

---

## 0.9.0 双预览版 / versionCode 14 · 2026-10-01 · 本地积分、账户占位与双包交付

> 当前同时交付用户初版 `cn.screenqa.lite` 与开发者本地版 `cn.screenqa.lite.dev`。两者都是 debug 签名预览包；云端积分、账号登录和充值支付**尚未接入**。后续先读 `docs/DEVELOPER_BUILD.md`，再读本日志。

### 功能变化与关键逻辑

- 将 TEMP/TEST 比例集中调整为 `CreditCalculator.TEMP_TEST_TOKENS_PER_CREDIT=10`，即 **1 积分等于 10 Token**。这是本轮本地测试换算，不是正式商业化定价。积分以百万分之一积分的 long 整数存储，避免小数累计误差；开发统计继续展示真实输入、输出、总 Token 与对应测试积分。
- Gradle 增加 `user`、`developer` 两个 product flavor。用户版版本名 `0.9.0-user-preview`、包名 `cn.screenqa.lite`、应用显示“看题助手 Lite”；开发版 `0.9.0-dev-local`、包名 `cn.screenqa.lite.dev`、显示“看题助手 Dev”及 Developer Build 标识。无障碍服务显示名称也区分两包。各自的 Android 应用沙箱、Key、日志和积分完全独立，旧版同包名的开发统计不迁移到新开发包。
- 新增 `CreditRepository` 接口，定义读取余额与按请求 ID 扣减；`CreditService` 是答题业务入口和本地仓储工厂。用户版 `EncryptedLocalCreditRepository` 使用 Android Keystore AES-GCM，将状态写入应用私有 `no_backup/.wallet/balance.bin`；开发版 `PlainLocalCreditRepository` 写入其独立沙箱内 `no_backup/.developer_wallet/credits.txt`，不加密。公共 `LocalCreditRepository` 用 `AtomicFile` 原子写入；`CreditState` 保存余额及最近 64 个请求 ID 防止常见重复扣费。读取失败不重置余额，积分状态不可用时停止使用。
- 为本地预览配置用户初始 100 积分、开发者初始 1000 积分，集中在 `PreviewCreditConfig`。这是可运行测试额度，不是赠送或收费政策。开发版“我的 → 开发者积分设置”可以设置 0 到 10 亿的整数积分；用户版不提供调额入口。
- `ApiRequest.send()` 在发出网络请求前通过 `CreditService.requireAvailable()` 检查余额；成功读取 DeepSeek HTTP 200 JSON 后按原始 `usage.total_tokens` 精确扣减，并在扣减成功后才交付答案。连接测试请求同样计费。缺少有效 usage 时拒绝交付答案，不估算也不扣费；网络/HTTP 失败且无 usage 时不扣费。Developer Build 的 `TokenUsageTracker` 保留独立成本统计。存储/扣减异常与零余额通过 `CreditException` 返回；`CaptureService` 将自动或手动识题暂停，在悬浮窗显示“积分不可用 · 已暂停答题”，避免后台反复请求。
- 余额检查发生在请求前，实际费用只能在 DeepSeek 响应后确定。如果一次请求的消耗超过剩余额度，本地仓储记录负余额作为待结算额，该次答案仍可交付，之后的新请求被拦截。正式云端版必须使用服务端预授权、对账和可信幂等，不能直接沿用这一预览结算行为。
- 底部导航改为“首页 / 设置 / 我的”。首页增加积分余额入口；设置增加积分与账号入口。“我的”采用身份卡、突出余额的渐变积分卡、充值 CTA、登录/注册占位与 About 入口；按钮明确提示未开放，不发起支付或保存账号凭证。`AccountGateway` / `PreviewAccountGateway` 提供未认证的预览账户状态，方便后续替换为真实账号系统。About 从底部导航移入“我的”，开发者统计仍在设置页。
- 云端积分目前**没有实现**。未来可用账号绑定的云端 `CreditRepository` 替换本地仓储，并替换 `AccountGateway`；不得以本机加密文件充当服务端可信余额。用户初版虽加密且放在私有隐藏路径，Root/重装/回滚等仍可能绕过本地规则。

### 修改文件

| 文件 | 变化 |
| --- | --- |
| `app/build.gradle` | versionCode 14，双 flavor、独立包名与标签，开发说明仅入开发版 asset |
| `app/src/main/AndroidManifest.xml` | 使用 flavor 提供的应用及无障碍服务名称 |
| `app/src/main/java/cn/screenqa/lite/CreditCalculator.java` | 唯一测试比例调整为 10 Token/积分；整数积分转内部单位 |
| `PreviewCreditConfig.java` | 两版初始本地测试额度集中配置 |
| `CreditRepository.java`、`CreditService.java` | 可替换仓储接口、请求前余额检查、按真实 Token 扣费与开发版调额 |
| `CreditState.java`、`LocalCreditRepository.java` | 本地余额状态、重复请求 ID 去重、原子文件读写 |
| `EncryptedLocalCreditRepository.java`、`PlainLocalCreditRepository.java` | 用户版 Keystore 加密私有文件；开发版独立明文文件 |
| `AccountGateway.java`、`PreviewAccountGateway.java` | 账号服务接口与明确未登录的本地预览实现 |
| `ApiRequest.java` | 请求前积分门禁、响应后实际用量扣减；原提示词/HTTP 参数/答案格式处理保留 |
| `CaptureService.java` | 积分异常时暂停并在悬浮窗给出状态；通知与面板使用当前 flavor 名称 |
| `MainActivity.java`、`ic_account.xml`、`ic_wallet.xml`、`strings.xml` | “我的”页、余额与充值/账号占位、开发者调额、导航及状态文案 |
| `TokenUsageTrackerTest.java`、`CreditStateTest.java`、`VariantConfigTest.java` | 新比例、扣费幂等/欠额拦截、包名隔离测试 |
| `docs/DEVELOPER_BUILD.md`、`AGENTS.md`、`README.md`、`验证记录.md`、`DEVLOG.md` | 两版边界、存储位置、云端待办、验证结果和追加日志 |
| 上一项目工作目录 `build.ps1` | 本机离线构建脚本改为分别运行两版 assemble/lint/JVM 测试；不在源码包中 |

### 验证结果与未完成事项

- 离线执行 `assembleUserDebug`、`assembleDeveloperDebug`、各自 `lint` 和 `testDebugUnitTest` 成功。每版 59 项 JVM 测试，均 0 failure；两版 Lint 0 错误/14 警告。APK v2 签名、zipalign 4 字节、版本名、包名和 ABI 均通过核验；共同开发证书 SHA-256 `b5cbd72bb2fb3dee36f28fc0216d94c52f1462233f985f69c898458278611c73`。用户 APK 29,146,122 B，开发者 APK 29,148,141 B。用户 APK 不含开发说明 asset，开发者 APK 含 `assets/DEVELOPER_BUILD.md`。
- JVM 测试覆盖 1:10 换算、实际 Token 扣费、重复 ID 不二次扣费、余额耗尽阻止下次请求及 flavor 包名。当前环境没有模拟器镜像，按约定未连接真机、未调用真实 DeepSeek；Android Keystore 文件落盘、悬浮窗实际提示、视觉布局和真实计费仍需设备端验收。编译通过不等于支付/云端安全验收。
- 充值、登录、账号云同步均为 UI 与接口占位；没有服务端、支付或充值到账。正式商业化前需要账号身份、服务端余额与预授权、可审计交易记录、退款/失败请求规则、防客户端篡改与重放、隐私政策/用户协议、第三方许可、迁移策略和真实设备实测。用户预览版不能作为正式 Release。Root 功能未开发。

---

## 0.9.0 基线后的构建体系规范化 · 2026-10-01

本次保持 0.9.0 / versionCode 14，不修改或移动 `v0.9.0` 初始 Git 标签。唯一工程根目录为 `source`，继续共享 `app/src/main` 核心代码；未建立长期 developer/user 分支，未增加或升级应用依赖。

### 变化、实现与修复

- `app/build.gradle`：保留 audience 维度的 user/developer flavor 和独立 applicationId；增加 flavor 级 `DIAGNOSTICS_ENABLED`，与 `DEVELOPER_BUILD` 一致。userDebug 也不具有开发权限。禁用 developerRelease，保留 developerDebug、userDebug、userRelease；userRelease 显式关闭调试和 debug 签名，启用 R8/资源收缩。
- 开发说明通过 `DeveloperNotesTask` 和 Android Components 的 generated assets API 接入，只复制 `docs/DEVELOPER_BUILD.md`。修复首次检查发现的 Lint 生成模型未声明 asset 任务依赖问题，改由 Variant API 自动关联生成任务及所有消费者。
- `MainActivity.java`：用户版隐藏日志/诊断及 Root 开发占位，并在详情路由和 switch 分派再次检查权限；继续隐藏 Token 统计和调额，拦截历史日志导出返回入口，用户文案不展示真实 Token。保留常规权限、识别、手动框选和使用者自己的 API Key 连接检查。
- `QaLog.java`：用户版在底层禁用启动、重新开启记录、事件写入、读取、列举、导出和清理诊断日志；不创建日志执行器，不写应用诊断 Logcat。曾保存的记录开关不能绕过编译标识。开发版日志行为保留。
- `ApiRequest.java`：用户版不展示 HTTP 状态码和缺少 usage 的内部细节；仍按真实 usage 进行原有积分扣减。未改变 API 参数、OCR、Accessibility、题目识别、自动点击/填写、下一题和滑屏算法。
- `verifyNoEmbeddedSecrets` 构建前检查源码/资源、构建配置及开发说明，拒绝敏感配置文件、私钥和常见硬编码凭证；只打印文件路径。API Key 继续仅由使用者运行时输入、以 Android Keystore 加密保存，不从构建配置注入。
- 更新 `docs/DEVELOPER_BUILD.md` 和 README；更正开发日志可能包含题目/答案片段的说明。新增 `UserDiagnosticsTest.java`，扩展 `VariantConfigTest.java`，验证 flavor 权限及用户版底层不能访问日志。

### 构建和验证证据

- 本机在忽略的 `.local` 内准备 Temurin JDK 17.0.20.1、Android SDK platform 35、Build Tools 34.0.0 和 Gradle 8.9；AGP 8.7.3 及全部应用依赖版本未变。SDK 自动准备的 platform-tools 仅为本地构建工具。代理、缓存和 debug.keystore 均位于忽略目录。
- 三个 Variant 的 assemble、对应 Lint 和 JVM 测试均通过。developerDebug：62 项测试，60 通过、2 个仅适用于用户版的测试跳过；userDebug / userRelease 各 62 项全部通过。均为 0 failure / 0 error；各 Lint 0 错误、14 警告。
- 实际 BuildConfig 核验：developerDebug 开发/诊断标识 true/true；userDebug 与 userRelease 均 false/false。APK 包名分别为 cn.screenqa.lite.dev / cn.screenqa.lite；minSdk 26、targetSdk 35，ABI 均为 arm64-v8a、x86_64。
- 两个 Debug APK v2 签名通过，共同新本地证书 SHA-256：0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912。三个 APK 均通过 4 字节 zipalign。userRelease 未签名且无 debuggable 标识；不使用 debug 签名。
- 开发者 APK 包含与源文档哈希一致的 DEVELOPER_BUILD.md；两份用户 APK 均不含此 asset。检查 APK DEX/资源/assets 未发现常见 API Key 或私钥模式、私有配置文件；userRelease 中未保留 developer_token_usage_v1、.developer_wallet、DEEPSEEK_USAGE、开发者积分设置和日志与诊断等开发实现标记。
- 两个负向样例验证通过：临时 .env 配置和包含纯合成密钥模式的文本样例都被 verifyNoEmbeddedSecrets 拒绝；仅本次创建的样例已清理。没有向源码或 Git 写入真实凭证。任务清单确认没有 assembleDeveloperRelease。

### 交付与边界

APK 输出为 app/build/outputs/apk/developer/debug/app-developer-debug.apk、app/build/outputs/apk/user/debug/app-user-debug.apk、app/build/outputs/apk/user/release/app-user-release-unsigned.apk；哈希和大小见根目录追加的验证记录。

原始 ZIP、APK、根工作区历史日志和验证文件未改变。当前新 Debug 证书不能覆盖其他证书签名的旧包；userRelease 必须使用正式发布证书签名后才可安装。未连接或寻找真机，未执行设备端端到端测试，未调用真实 DeepSeek API。凭证模式扫描不构成对任意编码秘密的证明；账号、支付和可信云端积分仍未实现，正式商业发布前仍须独立验收。

## 2026-10-01 · Root 增强开发测试 · 0.9.1-dev-root-test

### Git 基线与范围

开始时 main 存在上一轮已验证但尚未提交的 Build Variant 规范修改。先单独提交为 `66a565a`（build: isolate developer and user variants），以此稳定 main 创建并切换 `feature/root-enhancements`；以下功能与测试均在该分支完成。main 未合并 Root 修改，原始 `v0.9.0` 仍指向 `b8c800a`，没有创建或修改版本 tag。

共享工程仍为 source/app/src/main，无 developer/user 源码副本或长期分支。DEV flavor 单独调整版本到 0.9.1-dev-root-test / code 15，包名 cn.screenqa.lite.dev；用户 flavor 保持 0.9.0-user-preview / code 14 / cn.screenqa.lite。仅运行 DEV assemble、Lint、JVM 测试，不运行用户构建、不改用户产物、依赖或业务识题算法。

### 实现与重要修改

- 新增 `RootShell.java`：唯一 su 入口，ProcessBuilder 启动 su -c /system/bin/sh。先仅发送 uid=0 握手，再复核动作 guard，最后才发送固定命令；避免用户授权弹窗返回后执行旧题坐标。stdout/stderr 由受限缓冲的后台线程排空，避免阻塞，不向日志输出原始命令结果。分类处理缺失、拒绝、超时、命令失败和取消；中断保留线程标志，退出时终止仍存活进程并关闭流。
- 新增 `RootCommands.java`：只生成两类可信命令，组件严格校验、坐标必须在当前屏幕尺寸内。Accessibility 使用当前 Android 用户 ID 与 ComponentName(context, existing service) 得到实际组件，兼容 DEV 包名与 Java namespace 不一致。读取 enabled_accessibility_services，将 null 当空；按冒号分隔精确判断完整/缩写组件，不重复添加；追加本应用并开启 accessibility_enabled，复核本应用及原有每个服务。失败不写回旧快照，避免回滚覆盖别人的后续修改。
- 新增 `RootManager.java`：进程内权限状态、后台串行 UI 操作和有限能力入口；不持久化 Root 授权。每次真实操作重新通过 su 握手，撤销或失败清除缓存授权。权限申请限时 15 秒、系统设置 5 秒、答案触摸 1.2 秒，均不阻塞 UI；不使用任意 AI/UI shell 文本，没有新增第三方依赖。
- 新增 `AnswerClickOutcome.java`：区分成功、命令未发送的失败、动作过期取消和“可能已点击”的失败。未发送失败允许原无障碍执行器接管；过期动作取消不复用旧目标；发送后的失败/超时停止补点和自动切题，标记该题等待用户确认，避免双击。
- `Settings.java`：Root 总开关默认 false；自动授权和答案点击分别可关闭，所有有效开关先检查 DEVELOPER_BUILD 和总开关。用户 flavor 即使残留同名偏好也不能启用 Root。
- `MainActivity.java`：复用现有 Root 页面和组件样式，添加总开关、两项子能力、申请权限/启用服务按钮、权限状态和限制说明。原手动授权函数保留，Root 不可用或系统未连接服务时回到原引导；启用后最多等待 2 秒连接再继续屏幕共享流程。Root 不绕过悬浮窗或 MediaProjection 用户授权。
- `ScreenQaAccessibilityService.java`：只改答案最终执行层。复用已有候选题目、AnswerTargetResolver、节点坐标与原点击/gesture fallback。Root 下发前再次读取节点树，核验包名、题目 fingerprint、屏幕尺寸及答案行文字/位置一致；重新核验当前题型开关、暂停、服务连接和执行代次。其他文本输入/下一题/滑屏执行逻辑未改。
- `CaptureService.java`：画面答案路径沿用已有目标中心、屏幕坐标、VisualSignature、悬浮窗/答案窗遮挡和 3 秒时效检查。Root 点击前在主线程短暂保留新帧并再次验证；无新帧最多按 32ms 重试 5 次，后台等待上限 350ms，取不到可靠新帧就取消。避免普通 frame 消费抢走 Root guard 所需的新画面。取消/关开关/暂停/旋转后失效；安全失败时以新帧调用原执行层，绝不复用旧像素补点。Root 关闭时不会进入新增分支，普通识题、AI、定位及无障碍执行路径保持原样。
- `app/build.gradle` 只改 DEV 版本信息；开发 asset 与 flavor 隔离规则不变。更新开发说明、README 与验证记录，保留既有历史。

### 可执行验证与证据

运行 `:app:assembleDeveloperDebug :app:lintDeveloperDebug :app:testDeveloperDebugUnitTest`，最终 BUILD SUCCESSFUL。85 项 JVM 测试：83 通过，2 项用户专用诊断测试按既有条件跳过，0 failure / 0 error。新增 RootShellTest 13 项、RootCommandsTest 10 项全部执行通过；包含真实宿主 Git Bash 脚本集成，但 su 身份和 settings 均为假实现，不涉及设备授权。

测试覆盖：su 缺失/拒绝、授权等待超时、线程中断、授权前和授权后 guard 取消、握手后才发送动作、成功/非零退出/发送后超时、结果不确定禁止 fallback；空/null 列表、保留多项其他服务、完整/缩写去重、相似名字不误判、设置写入失败、全局启用失败、列表内容不作为 shell 执行、恶意组件/用户拒绝；横纵屏尺寸边界和复用现有答案定位中心。原选择/判断、填空/简答、下一题、积分、Token、flavor 测试继续通过。

Lint 0 错误、15 警告；原有 14 项与现有 UI/旧 API 有关，新增 1 项为 Root 动态状态 setText 的可翻译资源建议，本轮没有进行无关 UI 重构。源码检查确认仅 RootShell 存在 su/ProcessBuilder 入口，CRLF-aware git diff --check 通过。

实际 DEV APK：`app/build/outputs/apk/developer/debug/app-developer-debug.apk`，28,933,362 字节，SHA-256 `5203a3c00c7080fe2e3ea77200d2f1a1f77afb87b1b035695d3b415ac5066214`。aapt 与 metadata 确认为 developerDebug / cn.screenqa.lite.dev / 0.9.1-dev-root-test / code 15 / minSdk 26 / targetSdk 35。apksigner v2 校验、zipalign 4 字节检查通过；继续使用上轮本地 debug 证书，SHA-256 `0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912`。DEV asset 与当前 docs/DEVELOPER_BUILD.md 完全一致；构建 secret guard 通过，最终 DEX/资源/assets 和条目检查未发现常见 API Key/私钥模式或私有配置文件。

两份既有用户 APK SHA-256 保持不变：userDebug `d82cc0187ee2eacba598a84e4a332fd2679aff63bde10044dc43540b0f88abee`；userRelease `348067278e3397a1ceb32eb8025f2bb528288cc999da4011d98962c28203e4fe`。用户编译配置保持共享源码兼容，未以用户构建测试替代本轮限定的 DEV 测试；未来用户发行仍需重新运行对应 Variant 的完整验证。

### 已知问题、真机验收与后续

没有连接或寻找 Android 真机，环境无可用模拟器；没有运行设备端端到端测试或真实 AI API。真实 su/Magisk 授权交互、厂商 ROM 对 settings 写入的限制及 input 点击接受情况仍需真机验收。su/input 成功只证明命令成功，并不证明目标应用选中答案；沿用原页面变化监测，结果不确定时需要手动确认。Root 不新增文本输入/下一题或外接显示屏支持，仍依赖现有 AccessibilityService 与主显示屏链路。

系统 settings 读写没有原子比较交换接口，短暂合并窗口中其他应用/系统同时修改服务列表存在竞争；正常操作已保留读到的全部服务并复核，测试时避免同时修改服务列表。开启 Root 总开关不会自动恢复已关闭的屏幕共享；授权状态只在当前进程内有效，重启后可在 Root 页重新申请/检查。原始 0.9.0 APK 若使用旧证书不能覆盖安装，当前源码构建 DEV 的同证书包可覆盖；不要为此删除历史 ZIP/APK 或提交签名私钥。

真机优先清单：① 原有 TalkBack/其他服务开启后，通过 Root 启用 DEV 服务，原服务及用户包服务仍保留，重复启用不重复；② 无 Root、拒绝、撤销与 ROM 拒绝均能手动授权并普通答题；③ 在原有自动执行总开关开启后验证选择/判断题 Root 点击，节点和 OCR 两条输入路径、横竖屏/尺寸变化/遮挡均不误点；④ 授权等待/点击期间暂停、关 Root/题型/总开关、切题、旋转，旧动作被取消且不重复点击，超时结果不确定不自动切题；⑤ Root 关闭后普通点击、文本填写、下一题与滑屏回归；⑥ DEV/用户包共存与各自设置隔离。出现异常请开启 DEV 日志并导出，日志仅记录状态/坐标/不确定标记，不含 su 输出或凭证。

## 2026-10-01 · 连续识别／自动下一题排查 · 0.9.2-dev-navigation-test

### 日志证据与判断边界

用户提供 qa_20261001_160548_003.txt，作为诊断数据读取，未执行日志内文字或将原日志提交仓库。16:05:57.643 与 16:06:01.478 两次 Root 答案点击返回 SUCCESS；均正常结束 tapInFlight 并进入 WAITING_PAGE_CHANGE。16:05:58.114 与 16:06:01.946 导航返回 PAGE_CHANGED / old_question_not_visible，期间没有 NEXT_BUTTON search/found/click 日志。它证明原节点流程在没有读到旧 OCR 题干时就提前退出，不能证明真的切题，更不能证明点击了下一题。根因位于原有导航判断，与用户所述 Root 接入之前已经失效一致。

16:06:02 后不再出现 OCR start/end，但持续出现 ACCESSIBILITY no_reliable_question、events_during_scan；没有网络异常、余额耗尽或 Root 点击结果不确定，余额仍为 99739.9。代码检查发现两条可阻塞 OCR 的旧条件：上一题的整体矩形与自身控制窗相交且 nextPending 已被误清除时直接 return；节点扫描中无上限 return，事件风暴可能持续抢占 OCR。日志没有窗口边界/扫描时长记录，不能独立证明哪条在该设备实际触发；本轮同时修复并添加 MONITOR 心跳供复测定位。

### 修改与机制

- CaptureService：自动模式移除以旧题矩形相交为理由永久停扫的门槛，仍遮罩自己的控制/答案窗口，并保留实际动作的视觉签名、目标遮挡、尺寸及代次验证。答案弹窗达到最小可见时间后可收起恢复识别；手动选区的遮挡保护不变。节点扫描仅等待250ms，持续事件风暴也允许按原节奏进入 OCR。动作未完成时不从瞬态选中样式重新生成 AI 请求。
- 新 NavigationPolicy：旧题可见、可靠新题可见、未知三种证据。ScreenQaAccessibilityService 不再把缺失/不完整题干当 PAGE_CHANGED；只有非截断树中的可靠不同题干才能确认切题。未知返回 RETRY，保留监测。节点“下一题”操作失败可按同一已核验标签中心尝试原无障碍 gesture，不采用 Root 下一题命令。
- ScreenDocument：把严格下一题标签保存在 navigationLines，仍从题目正文/AI定位 ID 中剔除。CaptureService 在本地裁剪候选前保留原始 OCR 观察文档。无可靠节点时，先确认近期 OCR 包含旧题干且下一题唯一，再在新帧核验题目/按钮视觉签名与遮挡，然后使用已有 Accessibility tap。无新帧最多等待800ms，过期、旋转、暂停、改设置或遮挡不点击，提示人工且继续扫描。不构建新的识题/AI/答案定位链路。
- 修复多行题干比较错误：旧代码将无行号的题干与包含行号/分隔符的 fingerprint 比较，可能重复解答同题；现在用实际归一化正文，稳定 fingerprint 只用于画面身份/重试判断。AI 定位仍返回已答题干时抑制重复点击；候选未知不再提前清除等待下一题状态。
- 下一题首击后等待1.4秒，最多两次点击。节点重试要求同题与原节点画面签名一致；OCR 重试只沿用 OCR 且要求同题、原正文 fingerprint 与新帧一致，不跨执行方式盲补。重复父/子标签相交视为同一节点按钮，真正不同位置的重复标签保持歧义拒绝；箭头后缀兼容略作扩展，提交/交卷等终止操作仍拒绝。
- 每5秒写 DEV MONITOR 状态，记录 OCR busy、距最近处理帧时长、next/root/navigation pending、节点扫描与暂停等；日志不新增凭证或题目内容。监测日志不是设备端成功证据。
- DEV 图标由 app/src/developer/res/drawable/ic_app.xml 覆盖：紫色底、黄色 DEV 标识，用户 main 资源/manifest 不改，没有核心源码副本。开发 flavor 版本为0.9.2-dev-navigation-test / code16，包名仍cn.screenqa.lite.dev；用户配置与产物保持0.9.0。

### Token 分类和定价辅助

TokenUsageTracker 保留 developer_token_usage_v1 所有旧累计字段；新增 classified_v2 分类账和三项自设单价。新增纯 Java TokenLedger 处理聚合、显示、预算与 CSV。ApiRequest 只在观测层包裹原 run/solve/detect，保留请求体、依赖、真实 usage 扣费和答案返回流程，统计异常不会让请求失败。观察连接测试/手动解题/AI定位解题/本地定位后解题、题型、OCR/节点来源、实际API模型、耗时、结果、当前稳定识别周期内的请求次数；每次观察生成请求UUID，定位后以归一化题干SHA-256记录题标识，失败未定位则为空。不存题目/答案/API Key。

用量：prompt/completion/API total、合法缓存hit/miss、可选reasoning子项；不重新计算 authoritative total，缓存拆分缺失或不符合输入总量时标未知，不影响原扣费。保存成功/无题/条件不完整/取消/超时/HTTP或网络错误/格式异常/输出截断/缺少usage/积分不足未发送/积分失败等结果；即使格式或积分处理失败，只要响应已包含真实usage也计入成本观测。取消后的可用响应按取消记，费用仍可观察。请求尝试数与有效答案请求数不等于唯一题数或动作成功数。

逐请求最近500条、每日最近31天、类别累计永久保留；新明细从本版开始，旧总量不猜测归类。CSV 用系统文件选择器导出UTF-8 BOM、23列，包括逐请求与分类累计，空字段表示未知，字段引用并防电子表格公式注入；按题SHA和模型可进一步汇总，但相同题干/手动完整文本与不同识别周期的次数不能当真实题号。自设每百万Token的缓存hit输入、miss输入和输出单价，单价必须非负、最多8位小数，均以同一货币单位输入；仅完整缓存拆分请求计入统一单价预算，未知部分明确排除，混合模型应从CSV分别计价。没有硬编码真实API价格，不改临时积分规则，也不把预算当官方账单。API字段来源为 docs/DEVELOPER_BUILD.md 链接的 DeepSeek 官方 Chat Completions / 缓存文档。

### 验证与交付

在 feature/root-enhancements 完成修改；main仍66a565a，保留v0.9.0，不创建正式tag、不合并。只运行 assembleDeveloperDebug、lintDeveloperDebug、testDeveloperDebugUnitTest，最终BUILD SUCCESSFUL。101项JVM测试：99通过，2项用户专用诊断测试按既有条件跳过，0 failure/error。新增NavigationPolicyTest 6项、MonitoringRegressionTest 1项、TokenLedgerTest 9项，覆盖日志里的缺失节点不假切题、不同题正向证据、多行正文与导航标签分离、重复/提交拒绝、扫描风暴限时、重试限制；分类失败用量、未知缓存/usage、重复ID、恢复、500条/31天与累计保留、预算、小数单价、CSV公式防护、API缓存字段、题SHA与重试元数据。它们是宿主回归，不是Android目标APP端到端测试。

Lint 0错误、16警告（比上一版新增1项统计文字拼接的资源化建议；其余为已记录UI/旧API建议）。CRLF-aware git diff --check通过。DEV APK developerDebug / cn.screenqa.lite.dev / 0.9.2-dev-navigation-test / code16 / minSdk26 / targetSdk35；28,965,395字节；SHA-256 edf0d32cecee7fc3c449e642f63ba9e5e553828f1e2544867a8ec9149b043a76。v2签名与4字节zipalign通过，继续使用本地证书0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912，可覆盖同证书上一轮DEV安装。实际APK的ic_app包含紫/黄颜色和DEV矢量笔画，开发说明asset与源码一致；secret guard及APK常见凭证/私有文件检查通过。

现有userDebug SHA-256 d82cc0187ee2eacba598a84e4a332fd2679aff63bde10044dc43540b0f88abee、userRelease SHA-256 348067278e3397a1ceb32eb8025f2bb528288cc999da4011d98962c28203e4fe保持不变。源码/日志/测试/DEV资源进入Git，APK、构建缓存、签名、本地配置和用户提供原日志不提交。

### 已知限制与真机复测

未连接设备、无可用模拟器、未调用真实AI；不能宣称目标APP连续流程已经实测修复。新OCR导航仅覆盖文字标签唯一且当前题干/画面可核验的按钮；纯图标、遮挡、未选中答案所以按钮禁用、极慢页面或特殊布局会停止自动导航，要求手动切题并继续监测。命令或gesture被接受不等于选中/换页成功；后续仍靠可靠新题证据确认。2次导航上限有意保留，避免连续误跳。

真机优先：同一目标APP分别Root开/关连续做20题以上，记录是否仍有OCR断流；选择题/判断题及节点缺失页面检查真实下一题日志，点击不生效、遮挡、页面慢更新、暂停/旋转/改设置时不误点或连跳；人工切题能自动恢复；DEV紫黄图标明显；Token页面连接测试与不同题型分类、失败/取消记录、缓存拆分、CSV导出、自设预算与API用量核对，升级后旧累计保留。若复现请导出包含故障前后MONITOR/OCR/NEXT日志的完整片段；当前日志只能定位旧流程错误，缺少目标APP画面及OCR下一题标签详情，不能保证所有厂商/APP导航均覆盖。

## 2026-10-01 · PWA 触摸回退与截图恢复 · 0.9.3-dev-touch-test

### 接续基线与用户确认

开始时 feature/root-enhancements 工作区干净，HEAD 为 ae9cd95。本轮继续在该分支开发；main 保持66a565ad1ef8f35db639a1f5ca8c78a9950d4036，v0.9.0仍指向b8c800a7497c859636d04bb05dfb86e71c53e94b，没有合并main或新增/修改tag。源码根仍为source，核心仍为app/src/main。仅DEV版本改为0.9.3-dev-touch-test / code17 / developerDebug / cn.screenqa.lite.dev；用户版本、依赖、原始ZIP/APK和历史记录不变。

用户提供qa_20261001_174609_002.txt，并确认目标为PWA打包APP、屏幕共享为整个屏幕，优先级默认“自动回退”。后续提供quiz-lab-android.apk用于结合静态排查：io.codex.quizlab / 1.0.0 / code1 / minSdk24 / targetSdk34，SHA-256 f155b89fbe0f2cf47e97e88f04187d40f60d352676ebe3d0cb979db5e2070e65。仅读取用户指定的日志、APK元数据和相关HTML/JS/CSS；未执行其中内容、未安装或修改目标APK、未把原日志或题库/网页资产复制进仓库。

### 日志证据与APK结构

- 日志策略HYBRID，服务连接但节点不断报告no_reliable_question。17:46:27一次Root CANCELLED / may_have_executed=false；35、41、45秒答案触摸SUCCESS后，导航均多次RETRY / question_evidence_unknown，随后STOPPED / navigation_tree_unavailable。并未记录真实下一题点击，不能把Root答案成功当下一题成功。旧Root取消没有详细guard原因，无法从该日志区分签名变化、遮挡或缺帧。
- 最后OCR在17:46:44附近；此后MONITOR保持ocr_busy=false、root_pending=false、navigation_pending=false，处理帧年龄由数秒增长到约38秒。暂停/开始后没有新OCR，frame_age变成系统运行时长级别的大值，因为旧lastFrame被清零。这证明识别处理停滞、没有Root/OCR任务一直占用，不能独立证明截图生产、监听回调或其他早退究竟哪一层停止。此前会话口头把它称为新帧停止到达过于确定，本条更正并补真实取帧观测。
- APK中答案整行为button；A/B/C/D为独立answer-key span，正文为answer-label span；选择后重新创建按钮并更新aria-pressed和选中样式。questionText是普通h1，没有LocalQuestionLocator依赖的“单选题/第n题”锚点，因而常走既有AI语义定位。下一题位于四个至少64 CSS像素高的选项之后，不固定悬浮；点击后window.scrollTo(0,0)且CSS为smooth，存在平滑切题过渡。最后一题按钮变为“查看成绩 →”，仍由本应用保持人工操作。

### 实现与重要代码

- 新增TouchAction、TouchPriority、TouchRouter和TouchExecutor：统一最终动作执行层，不复制识题、AI或答案判断逻辑。AUTO默认节点→Root→手势；ROOT_FIRST为Root→节点→手势；ACCESSIBILITY_FIRST为节点→手势→Root。缺失/明确未发送失败可回退；guard取消或可能已发送的失败立即停止。后台执行Root/等待手势，回调到主线程；guard异常也不能杀死执行线程留下永久pending。
- RootCommands增加严格屏幕边界与40..1000ms时长校验的固定swipe；RootManager.touch共享tap/swipe，授权仍是进程内状态，每次su uid=0握手后再次核验guard。触摸超时1.8秒。RootShell仍为唯一su入口，没有任意AI/UI命令或依赖变更。原root_answer_tap偏好保留，页面名称扩展为“Root虚拟触摸”，覆盖答案、下一题、滑屏。Root自动启用无障碍仍用原精确追加和保留其他服务机制。
- ScreenQaAccessibilityService的节点答案、下一题和滚动接入同一TouchRouter。OCR匹配节点仍读取新树并匹配文字/坐标/可点击父节点。手势使用onCompleted/onCancelled回调，1.6秒无回调按可能已执行处理，禁止盲目换后端重复动作。服务断开或guard异常返回取消，不在主线程崩溃；主线程中的图像guard直接检查，不等待自己排队的任务。滚动的不确定结果显式返回UNCERTAIN，不能再进入OCR补滑。
- CaptureService画面答案共享原AnswerTargetResolver和VisualSignature，不硬编码答案位置。Root-only时移除无障碍必须连接的入口条件；MainActivity和悬浮窗总开关也允许已有Root授权直接进入。文字填写仍依赖原无障碍执行器。画面路径在每个后端前检查新帧；无新帧时只复用450ms内刚成功验证的同一动作证据，重查开关、尺寸、题型、代次、暂停和自身窗口。签名仍严格相等，没有用模糊匹配放松题目确认。
- AnswerRetry保存一次AI答案的短暂执行上下文，只有确定没有发送动作的失败、新OCR完整题干严格相同、会话/优先级/题型/权限有效，才通过原maybeAutoSelect重新定位执行一次。最多6秒、总共两次尝试；不复用旧坐标、不再次请求AI、不增加这次本地重试的Token用量。结果不确定绝不重试，成功后仍等待原页面变化链路。
- OCR导航不再把未知节点树当作必须停止。先用最新OCR最小完整题干区间核验同题（不把已答计数/选中选项样式纳入题干proof），找到唯一下一题则复用当前按钮中心执行；没有按钮时，根据当前contentBounds生成向上滑动，整条路径避开panel/popup。最多2次滑动、2次下一题触摸。自执行滚动后题干不可见时，仅在5秒上下文中用至少2条标签加实质正文严格相同的选项保持连续性；裸字母/泛化对错、重复标签、可见不同新题干均拒绝。下一题之后允许原AI定位确认无类型锚点的PWA新题，未建立第二套语义识别。ScreenDocument保留terminalLines以拒绝提交/交卷/查看成绩，目标APK的最后一题保持手动。
- 悬浮窗新增DEV专用“执行顺序”单选弹窗；偏好与识题来源strategy分开，默认AUTO。打开弹窗撤销待动作并阻止扫描自身弹窗，关闭后恢复；选择顺序不补执行被撤销的旧题动作。移除/创建窗口异常均有处理。用户flavor不显示此开发入口且有效Root开关仍编译关闭；共享源码兼容，不构建用户APK。
- 截图方面：新增纯Java FrameProof及3项回归，等su/手势期间持续排空截图队列、更新当前动作的证据而不重新识题；新画面不匹配立即撤销缓存证据，避免保留帧导致生产者被队列阻塞。ImageReader从2槽改为3槽，允许OCR复制持有1帧时acquireLatestImage仍有2槽用于丢弃旧帧；700ms主动读取作为监听补充。使用独立lastFrameArrival，不再以lastFrame节流时间假充到帧时间；MONITOR增加processed_age_ms、touch_pending和reader_repairs。等待新帧的答案/视觉回退都有超时清理。无实际取帧3.5秒、没有OCR/触摸占用时，为已有VirtualDisplay setSurface到新ImageReader，每分钟最多3次、间隔10秒；不创建第二个VirtualDisplay、不重用MediaProjection授权。修复时使旧请求/动作过期，但保留已答/结果不确定的题干防重状态；失败提示重新共享整个屏幕。
- 节点优先策略的二次扫描也受同一个250ms等待预算限制，不能每个新事件重新获得无限等待。DEV紫黄图标、原Token分类账、积分和模型/API配置不变；无新增凭证或遥测。

### 可执行验证与APK

运行assembleDeveloperDebug、lintDeveloperDebug、testDeveloperDebugUnitTest，最终BUILD SUCCESSFUL。134项JVM测试：132通过、2项原有用户专用诊断测试跳过，0 failure/error。新增33项：FrameProofTest3、TouchRouterTest12、TouchGeometryTest4、FrameHealthTest3、PwaNavigationTest8、AnswerRetryTest3。覆盖三种顺序、无节点Root-only、Root拒绝/撤销前失败回退、Root下发后超时不补点、手势取消不补点、变更开关/guard异常、屏幕边界/时长、整段遮挡、恢复限流、PWA分离字母与正文/无类型锚点/计数变化/滚动连续性/同选项不同题干/最后成绩拒绝，以及缓存答案重试限次/过期/换题/换优先级。原积分、Token分类、Root列表保留、答案定位与导航单测继续通过。这些是宿主纯逻辑及假shell回归，没有Android目标APP端到端测试。

Lint 0错误、17警告（原有UI/旧API建议，加1项新状态文字资源化建议）。CRLF-aware git diff --check通过。实际BuildConfig/metadata/aapt确认为developerDebug / cn.screenqa.lite.dev / 0.9.3-dev-touch-test / code17 / minSdk26 / targetSdk35。最终APK路径：D:/Codex/Program/Screenqa/source/app/build/outputs/apk/developer/debug/app-developer-debug.apk；28,999,458字节；SHA-256 5c55317328c9c26d03368ad1ceb0ac626e177c2cb654bdfe2cfbee5c52f31c88。v2签名和4字节zipalign通过，证书仍0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912，可覆盖同证书上一轮DEV。APK内DEVELOPER_BUILD.md与源码字节一致，构建secret guard及APK DEX/资源/assets/文件名检查未发现常见密钥或私有签名配置。

现有用户APK未运行构建，SHA-256仍为：userDebug d82cc0187ee2eacba598a84e4a332fd2679aff63bde10044dc43540b0f88abee；userRelease 348067278e3397a1ceb32eb8025f2bb528288cc999da4011d98962c28203e4fe。本地构建日志/audit JSON在忽略的.local中；源码、测试、说明及本日志/验证记录进入Git，APK/缓存/本地签名/用户原日志不提交。

### 已知限制与真机重点

没有安装APK、连接或寻找设备、运行模拟器或调用真实AI；不能宣称真实失效率已经降低到特定比例或连续做题已实测修复。Root input成功/gesture完成只说明动作交付，不证明选项已选中或真正换页；节点返回成功也可能被目标APP忽略，可用Root优先对照。精确像素签名、非常长题、被遮挡/裁切、纯图标下一题、重复/短选项及无法核验的滚动上下文仍可能转手动。相同选项且新题干完全不可见时无法从有限屏幕内容证明全局题目唯一，人工操作前应暂停，不把滚动连续性当真实题号。截图恢复有次数与冷却限制，系统共享被撤销/系统侧冻结仍可能需要停止后重新授权整个屏幕。Root与无障碍都没有时普通APP没有直接跨应用注入权限；本轮未新增ADB/Shizuku授权通道。用户variant仅保持源码兼容，正式用户发布仍需其自己的完整构建与设备验收。

真机优先：① 答题实验室共享整个屏幕，自动回退连续30题以上，观察选项真实高亮、滚动找到下一题、真正换页，无连跳/重复AI；② Root优先对照A/B/C/D不同位置与长题，关闭/断开无障碍服务但Root已授权时答案/滑屏/下一题仍可执行，填空仍明确需要无障碍；③ 无Root或撤销授权时节点/手势回退，Root关闭回归；④ 卡住后暂停/继续与自动rebind能否恢复，导出故障前后MONITOR/FRAME/TOUCH/ANSWER retry/NEXT；⑤ 拖窗遮挡、选择优先级、暂停、旋转、撤销Root期间无旧动作补点或误点；⑥ 最后一题不自动点查看成绩，Token历史和分类仍保留，本地缓存答案重试不新增AI请求。若仍失效请提供完整新日志及失败时屏幕截图，区分已交付触摸、实际选中、页面变化和取帧恢复。

## 2026-10-01 · 允许 App 被截屏与录屏

用户明确要求关闭 App 自身的截图/录屏保护并永久保存到 Git。本次仅移除 app/src/main/java/cn/screenqa/lite/MainActivity.java 中 onCreate 的 FLAG_SECURE 设置；该 Activity 为用户与开发者 flavor 共用，后续构建两者均允许系统截屏与录屏。全 app/src 检索没有发现其他 FLAG_SECURE、setRecentsScreenshotEnabled、setScreenCaptureDisabled 或 setContentSensitivity 限制。没有增加开关、改版号或修改其他业务逻辑。

验证：使用项目本地 JDK 17、离线 Gradle 执行 assembleDeveloperDebug 与 lintDeveloperDebug，BUILD SUCCESSFUL；Lint 0 错误、17 项已有警告。首次使用 Android Studio 自带 JDK 的 Lint 失败，切换项目已有 JDK 17 后通过。git diff --check 通过。本次为窗口标志删除，不新增镜像实现的单测，不重跑与本修改无关的业务单测；未构建或交付两个 flavor 的新版本。

在 ScreenQA_Test / Android 15 / emulator-5554 覆盖安装当前 DEV APK，安装 Success、启动 Status: ok。ADB screencap 的首页截图已经人工视觉检查，可见完整 App 内容，不再黑屏；screenrecord --time-limit 3 正常完成并生成 88,903 字节 MP4（未逐帧检查视频）。本地证据在 D:/Codex/Program/Screenqa/android-test-setup/screenqa-capture-enabled.png 和 screenqa-capture-enabled.mp4，不提交生成媒体、APK、缓存或签名文件。仅 DEV APK 本轮在模拟器验证，用户 flavor 的效果来自相同 Activity 源码，未作设备实测。

## 0.9.4-dev-quizlab-test / versionCode 18 · 2026-10-01 · 答题实验室真实自动答题回归

### 目标、根因与修复

用户要求在可见模拟器自行测试其提供的 quiz-lab-android.apk 并解决自动化问题，已明确允许捕获测试题目及文字/坐标发送到设备上已配置的 DeepSeek API。没有读取 API Key、重置用户设置或修改目标 APK。仅更新 DEV，包名 cn.screenqa.lite.dev 保持。

- 基线实测首题可选中 A，静态页面却不产生新的投影帧，导航等待画面超时。CaptureService 增加限频的按需换 ImageReader surface 请求新帧，复用已有 VirtualDisplay，不创建第二个显示、不使题目代次过期；动作排队时主动请求，OCR 或正在交付触摸时不关闭其图像资源。旧异常恢复保留。
- 目标 WebView 没有题型/题号锚点，通用定位器无法提取候选；OCR 的分离字母和正文不稳定。新增 QuizLabLocator，仅在 io.codex.quizlab、标题/进度/唯一编号/四个唯一 A-D 选项齐全时使用，只取编号之后、选项之前的实际题干，排除重复的大父节点、提示及已答计数。ScreenQaAccessibilityService 复用原候选/目标解析/执行 guard，不存储固定坐标或题目答案。
- WebView 事件和框架节点缓存导致旧题仍被当成当前题。API 33+ 每次树读取先 clearCache，节点快照超过一秒触发扫描；刷新期间保留不足 2.5 秒的已完成快照，避免在节点/OCR 身份间来回切换、重置稳定计数和重复 AI 请求。外部事件仍立即作废快照；真正点击和导航仍重新读取并核对包名、完整题干/选项、动作代次及遮挡。
- 末题无下一题时原节点链路会尝试两次滑屏。改为复用 ScreenDocument 的 terminalLines 检测，返回 TERMINAL，直接停止并提示手动查看成绩/提交。不会自动点击结果或提交，也不进入 OCR 导航兜底。
- 测试工具 uiautomator dump 会中断助手 AccessibilityService；诊断后连续测试仅使用 screencap 和 ScreenQA logcat，避免测试工具干扰。临时 OCR 文字诊断已删除。

修改文件：CaptureService.java、ScreenQaAccessibilityService.java、新增 QuizLabLocator.java/QuizLabLocatorTest.java、NavigationPolicyTest.java、app/build.gradle、docs/DEVELOPER_BUILD.md、本 DEVLOG。

### 实测证据与检查

ScreenQA_Test / Android 15 API 35 / x86_64 / emulator-5554 可见窗口，目标 io.codex.quizlab 1.0.0，十题固定顺序。修复后完整一轮（stable-run.log）真实 DeepSeek 十次请求、十次答案点击、九次下一题，全程未手动选项或翻页。助手停在末题，测试人员暂停后主动点击查看成绩，页面显示共十题、答对十题、正确率 100%；截图 stable-score.png。节点 accepted 日志仅证明动作交付，实际完成及正确率另外由目标进度和成绩页佐证。

最终 code18 APK 再次完整运行（final-run.log）：12:16:52.585 NEXT result=TERMINAL reason=terminal_visible scroll_count=0，停止前未滑屏或自动提交；final-run.png 显示 10/10、已答十题。上述本地证据保存在 D:/Codex/Program/Screenqa/android-test-setup，不提交运行原始媒体、APK、缓存或签名。

assembleDeveloperDebug、lintDeveloperDebug、testDeveloperDebugUnitTest 全部 BUILD SUCCESSFUL。138 项 JVM：136 通过、2 项原有跳过、0 failure/error；新增三项实际 DOM 适配回归与一项末题成绩识别回归。既有 Root 假 shell 测试在受限沙箱有宿主权限失败，按正常宿主权限重跑通过；不是 Android Root 实测。Lint 0 error / 17 warning。CRLF-aware diff --check 通过；aapt 核验 cn.screenqa.lite.dev / code18 / 0.9.4-dev-quizlab-test，apksigner verify 通过。APK SHA-256：dc16886502892c293bc8863e4ff83b5d2401eea8419479487f9dee3b4d301283。

### 限制与后续

只验证这个模拟器中的同一组十题选择题，不能据此宣称全部48题/其他App/长题/旋转/Root-only/真机或长时稳定性已通过。API 33 以下没有 clearCache 的同等缓存修复验证。按需创建截图 reader 的耗电与长期资源使用仍需后续测量。截图/录屏解禁继续有效，用户 flavor 未构建交付；具体 App 适配位于现有自动识题链路，专用入口仍是占位。日志明确区分失败实验、修复后完整回归与最终版本末题验收。

## 0.9.6 / versionCode 20 · 2026-10-02 · 自动识题与答案展示优先

### 功能与关键逻辑

用户要求降低自动选答及下一题的入口权重，核心改为自动识题并给出答案，永久移除积分，保留 Token 用量，并提交 Git；本轮无需模拟器测试。用户与 DEV 两个 flavor 共用变更，分别为 0.9.6-user-preview、0.9.6-dev-answer-preview，包名不变。保留任务开始时已有的未提交滚动核验修复，与本次变更一并提交，不覆盖前一轮工作。

- MainActivity：首页围绕自动识题、当前答案；识别和 AI 入口优先。自动选答/填写移动到“设置 → 更多 → 辅助自动执行”；总开关默认关闭且升级保留原偏好。新增独立 auto_next 偏好默认 false，答案执行成功后只有显式开启才进入寻找下一题/滑屏流程。DEV 执行顺序从悬浮窗移到辅助设置，修改时通过 AUTO_SETTINGS 使旧待执行动作失效。“我的”只保留用途与 About，删除余额、充值、账号占位及调额入口。
- CaptureService：移除悬浮窗自动执行开关与执行顺序入口。showState 不再因旧 fullAnswer 提前返回，定位/分析时更新 overlayState、清理旧答案/详情并 renderBubble，加载环和识题图标能更新。原答案延期显示仍有会话/题目标识核验，不能在过期题目回调中覆盖当前状态。识题节点读取和自动执行解耦：助手运行时开启已授权节点跟踪，混合策略关闭自动执行也优先本地定位，减少 OCR 与 AI 定位回退；不会为此强制申请新的授权。答案、填空、Root、下一题原执行安全核验继续保留。
- ApiRequest / ScreenDocument：AI 定位采用紧凑 modelJson，只传行号、文字与归一化 y 中心，完整矩形留在本机，仍按屏幕中心选择完整题目；本地定位只提交一道题。输出预算为选择/判断 128、填空 384、简答 768，AI 定位 768，thinking 继续关闭。真实 Token 节省比例和识别耗时没有调用 API 实测。
- 删除 CreditCalculator、CreditRepository、CreditService、CreditState、LocalCreditRepository、两个钱包仓储、PreviewCreditConfig、wallet 图标与 CreditStateTest；移除积分换算、余额门禁、扣减、充值、显示、诊断与异常处理。现有旧安装的数据文件不再被代码访问，历史 Git 与 DEVLOG 作为历史证据保留，不能把历史旧规则当成当前功能。
- TokenUsageTracker / strings：两个 flavor 均持久化实际输入/输出/API total、最近有效、今日和累计请求。沿用 developer_token_usage_v1 原 Token 字段，不重置开发版历史用量；用户版从本版开始入账。缺少/无效 usage 记录未知，仍可展示有效答案，不估算。DEV 保留分类账、缓存细项、CSV 与自设 API 单价预算，用户仅展示基础总量；已删除换算字段及日志输出。
- 保留前一轮 CaptureService / NavigationPolicy / QuestionDetection 滚动修复：动作后等待新帧、按取帧开始计 OCR 新鲜度、清理旧 OCR/边框、限制滚动连续性、精确处理內/内与 QUESTION 栏目标题。没有增加固定坐标或提交/成绩点击。
- 更新 AGENTS、README、docs/DEVELOPER_BUILD（仅 DEV 打包）与验证记录；文档不再介绍旧积分功能，长期工作规则禁止重新引入余额/兑换/扣费系统。DEVLOG 保留完整旧版本历史。

### 验证与证据

项目本地 JDK 17、SDK 35、离线 Gradle，最终执行 assembleDeveloperDebug、assembleUserDebug、lintDeveloperDebug、lintUserDebug、testDeveloperDebugUnitTest、testUserDebugUnitTest 全部 BUILD SUCCESSFUL。DEV JVM 140 项：138 通过、2 原有跳过、0 failure/error；USER JVM 140 项全部通过。两版 Lint 均 0 error / 16 warning（兼容 Switch、内部 inset、国际化、RTL、自定义 View 可访问性等）。紧凑定位请求新增回归验证文字与行号不变、归一化中心保留、有效 ID 返回后本地矩形不变；输出题型预算与实际 usage/历史平均统计回归通过；滚动核验及 QUESTION 标题相关回归也通过。

受限沙箱首次构建遇到默认 C:/.android 目录不可写，改用工程 .local/android-user-home；已有 RootCommandsTest/RootShellTest 的宿主 Git Bash 集成用例在沙箱失败，使用正常宿主权限离线重跑通过，没有跳过这些失败用例。最终日志为忽略的 .local/refocus-build-final.log，JVM XML 与 Lint 报告在 app/build。git diff --check（CRLF-aware）通过；app/src、当前 README 与打包说明扫描无 Credit/credit/积分/wallet 相关引用。verifyNoEmbeddedSecrets 通过。

aapt 核验两个 applicationId、versionCode 20 与各自版本名；两个 APK apksigner verify 均 exit 0。DEV 含开发说明 asset，USER 不含。APK 及签名文件不提交。

- DEV APK：app/build/outputs/apk/developer/debug/app-developer-debug.apk，SHA-256 07b05c7d718b2f3aa2ed319b5afc8b2396cf789b2363346c5dd12e750479a441。
- USER APK：app/build/outputs/apk/user/debug/app-user-debug.apk，SHA-256 e1a1e600b9d5e321a20bda740fa13e702f50b81812a35d2ddd61322617552219。

### 边界与后续

按用户要求没有模拟器测试、安装 APK、连接/寻找 Android 真机或调用真实 API。加载 UI 的真实视觉表现、连续识题、长时 OCR、复杂图形/长题/多题同屏、真实 Token 节省与跨 App 节点差异仍需设备验收，不能用本次编译/JVM 证明真实成功率或提速比例。选择/判断预算适合精简 JSON 与字母答案；遇到截断明确失败，不执行不完整输出。Root 用户版依旧关闭，专用 APP 入口仍为占位；本轮不扩大通用自动执行能力。

## 0.9.7 / versionCode 21 · 2026-10-02 · 用户版同步 Root 与日志

### 行为与实现

用户要求把 Root 权限及记录日志同步到用户版。两个预览包更新为 code21，版本名为 0.9.7-user-preview / 0.9.7-dev-answer-preview，包名不变。核心自动识题、辅助选答低权重、auto_next 默认关闭与积分删除继续保持。

- app/build.gradle：新增 ROOT_SUPPORTED 两版均 true；user 的 DIAGNOSTICS_ENABLED 改为 true，DEV 维持 true。DEVELOPER_BUILD 用户仍 false，仅控制开发者身份、详细 Token 分类及开发说明等专属内容。userRelease 同样包含 Root/诊断能力，不依赖 DEBUG。
- Settings、RootManager、MainActivity：Root 总开关、权限检查/申请、自动启用本应用无障碍、Root 虚拟触摸、设置页面路由与执行顺序均使用 ROOT_SUPPORTED，移除用户版底层 DEVELOPER_BUILD 限制。入口在“设置 → 更多 → Root”，执行顺序在“辅助自动执行”。Root 总开关默认关闭，沿用已保存偏好；设备授权仍为进程内缓存，不持久化授权。保持设备 Root 权限管理器授权、拒绝回退、保留其他无障碍服务、动作核验与不确定不补点机制。文字填写仍需无障碍，不绕过屏幕共享。
- QaLog、MainActivity：用户版开放开始/停止记录、查看日志列表、读取、系统文件选择器导出最新日志和清除。新增 defaultRecording，用户默认 false，DEV 默认 true，显式保存过的 qa_log_recording 优先。未启用用户记录时不创建日志文件或写 Logcat 事件；打开日志页面不会隐式开始记录。日志在各包私有 Q&A 目录，不自动上传，沿用每文件 256 KB、最多 8 个、约 2 MB 总量及异步 IO。界面说明日志可能含题目、答案片段和运行状态；不传 API Key 或凭证给 event。
- 改正 Root 页面“顺序可在悬浮窗调整”的旧提示为辅助设置；去掉 DEV 测试功能标签。
- 将旧 UserDiagnosticsTest（断言用户永远禁用日志）替换为 DiagnosticsConfigTest，核验用户记录默认需显式启用、DEV 默认保留、Release 仍有 Root/日志且无开发者身份；VariantConfigTest 同步验证能力与身份分别配置。没有用跳过旧断言掩盖功能同步。
- 更新 README、AGENTS 与开发说明，确保以后不能再因用户身份隐藏 Root/日志；保留全部历史 DEVLOG，历史“用户禁止 Root/日志”只适用于旧版。

### 验证及证据

本地 JDK 17、SDK35、正常宿主权限、离线 Gradle：assembleDeveloperDebug、assembleUserDebug、assembleUserRelease、三者对应 lint 和 JVM 单测全部 BUILD SUCCESSFUL（.local/user-root-log-build.log）。三种配置各 140 项测试全部通过，无失败/错误/跳过；各 Lint 0 error / 16 项已有警告。RootCommands/RootShell 的宿主模拟 shell 及触摸回归同样通过，不是实际 Android Root 实测。verifyNoEmbeddedSecrets 与 CRLF-aware git diff --check 通过。

aapt 核验用户 cn.screenqa.lite、DEV cn.screenqa.lite.dev，code21 与各自版本名。两份 Debug APK apksigner verify 均 exit0，DEV 包含开发说明 asset、用户包不包含。用户 Release 经 R8/资源收缩后仍含 Root 授权与日志记录/偏好字符串，配置测试确认能力 true；Release 保持 unsigned，不作为可安装包交付。

- USER Debug APK：app/build/outputs/apk/user/debug/app-user-debug.apk；SHA-256 e7c93f6d152a28fd7b3d27494d3d4c313ceab8d88d01913faa227a317eee5616。
- DEV Debug APK：app/build/outputs/apk/developer/debug/app-developer-debug.apk；SHA-256 cd044b6e06f42c953917dbb0344c229fbe416a29341b01c284ebd3f9a877a4c9。

### 验证边界

延续本次工作无需模拟器测试的要求，未运行模拟器、安装 APK、连接/寻找真机、申请真实 su 或调用 API。真实设备授权弹窗、厂商无障碍设置、日志文件轮换与 SAF 导出、Root 触摸的端到端行为仍待用户设备验收；本次编译/JVM及APK静态检查不代表这些已经实测通过。正式 Release 仍需正式签名。没有增加支付、余额、积分或新的自动执行能力。

## 0.9.8 / versionCode 22 · 2026-10-02 · 界面重做：黑金主题、液态玻璃 dock、模型与接口选择

本轮改动由 DSH 完成（不是 Codex 的改动），逐文件清单与设计取舍另见 DSH-LOG.md；本条目只记录按项目原格式的版本级行为与验证证据。

### 行为与实现

用户要求把界面整体重做：交互逻辑要优秀、要有非线性动画；底部 dock 悬浮并采用液态玻璃；默认配色走黑金；设置里预留几套配色主题；模型与接口向用户开放选择。两个预览包更新为 code22，版本名 0.9.8-user-preview / 0.9.8-dev-answer-preview；包名、minSdk 26、targetSdk 35、权限、Root 与日志能力、自动识题与辅助选答逻辑均未改动。

- 新增 ThemePalette 作为全应用唯一配色来源，提供六套主题：black_gold（默认黑金）、obsidian_teal、midnight_violet、crimson_night、jade_green、ivory_light（唯一浅色）。原来写死在 MainActivity.themeColors() 与 CaptureService.applyTheme() 的浅/深绿被全部替换；主题以 theme_id 持久化，切换后重建页面，并由偏好监听器下发到悬浮窗。
- 新增 Motion 动效入口：SPRING/SPRING_OUT/SPRING_SOFT 为阻尼弹簧（1-exp(-kt)·cos(wt)），另有 EMPHASIZED/DECELERATE/ACCELERATE/OVERSHOOT/ANTICIPATE 五条 PathInterpolator；时长 QUICK 190 / BASE 300 / SLOW 470 ms。页面入场、按钮按压、dock 抬升、药丸指示器与开关都走它，替换默认线性插值。
- 新增 GlassBackdrop、LiquidGlassView、GlassCard、GlassDock 组成液态玻璃：底图按 0.2 降采样、22px 模糊、两遍可分离 box blur；面板绘制顺序为近似阴影 → 裁剪圆角 → 模糊底图（围绕中心放大 1.07 倍做折射）→ 色调 → 高光 → 两团漂移焦散 → 渐变描边。底图只在切页、展开详情、主题重建后延迟 90ms 抓一次快照，不做逐帧重采样；dock 为悬浮圆角面板加药丸指示器，列表滚动超过 6dp 时下移 12dp 并缩到 0.95，停止后回到原位。
- 新增 AuroraBackground（三团预渲染径向光斑按 34 秒周期漂移）、GoldSwitch（自绘开关，轨道/拇指按弹簧进度混合 accent 与 onAccent，开态带光晕）、Ui（dp/sp）与 ic_dock_home / ic_dock_settings / ic_dock_profile 三个矢量图标。
- MainActivity 整体重写（原 853 行全部替换）。根布局为 FrameLayout{AuroraBackground, pagesLayer, GlassDock}：pagesLayer 同时充当玻璃采样源；dock 三项对应首页 / 设置 / 我的。详情页由 11 个扩到 13 个，在“我的”下新增“外观”（六套主题、液态玻璃开关、减弱动效开关），原 id 0..10 的入口与 onSaveInstanceState 的 selectedTab/selectedDetail 语义保持不变。开关改用 GoldSwitch，模型预览与接口选择用 chip，自定义模型/接口用输入框。
- Settings 新增 theme_id、reduce_motion、glass_dock、model_id、endpoint_url、thinking_enabled 六项偏好。模型偏好键必须用 model_id：Settings.save() 结尾会 remove("base") 与 remove("model")，沿用 "model" 会被下一次保存抹掉。API Key 的 AndroidKeyStore + AES/GCM 存储、isOfficialLegacyBase 拦截等原有逻辑未动。
- ApiRequest 新增 requestBody(model, system, user, maxTokens, thinking) 重载与实例方法 message(Exception)，请求地址改为读取 settings.endpoint()；Settings.MODEL 与 Settings.ENDPOINT 静态默认和旧的静态重载保留，FastPipelineTest 对 deepseek-flash、thinking.type=disabled 的断言继续通过。自定义接口必须是完整 https 地址（ModelCatalog.validEndpoint 校验），不回落到旧 base 偏好。
- CaptureService 的悬浮窗面板、答案弹窗、图标底色改为从当前主题取色，并注册 SharedPreferences 监听器，theme_id 变化时在主线程重绘；截图、Root、无障碍与自动答题流程未改。
- README.md、docs/DEVELOPER_BUILD.md、验证记录.md 同步版本号与界面说明。

### 验证及证据

本地 JDK 17、SDK35、离线 Gradle：assembleDeveloperDebug、assembleUserDebug、assembleUserRelease 及三者对应 lint 与 JVM 单测全部 BUILD SUCCESSFUL（.local/ui-rework-final3.log，156 个任务）。三种配置各 140 项测试全部通过，无失败/错误/跳过；各 Lint 0 error、16 项已有警告；verifyNoEmbeddedSecrets 通过。

首次完整验证（.local/ui-rework-final.log）曾在 testDeveloperDebugUnitTest 出现 10 项 RootCommandsTest 失败，全部为 java.nio.file.AccessDeniedException: C:\Users\Administrator\AppData\Local\Temp\junit<随机>，即宿主临时目录不可写；把 TEMP/TMP 指向工程内 .local/tmp 后重跑全部通过，属于宿主环境问题，与本轮改动无关（0.9.6、0.9.7 也出现过同类现象）。

aapt 核验用户 cn.screenqa.lite / 0.9.8-user-preview、DEV cn.screenqa.lite.dev / 0.9.8-dev-answer-preview，均 versionCode 22、minSdk 26、targetSdk 35。两份 Debug APK apksigner verify 均 exit0，证书 SHA-256 仍为 0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912，zipalign 4 字节检查通过；DEV 包含 assets/DEVELOPER_BUILD.md 且其 SHA-256 与 docs/DEVELOPER_BUILD.md 一致（00804b6d0ccab114bcd22b560ecef849fa2799bfa4b8a1f3c4e5790e27a975b2），用户包不含该 asset。用户 Release 经 R8 与资源收缩构建成功，保持 unsigned，不作为可安装包交付。

- USER Debug APK：app/build/outputs/apk/user/debug/app-user-debug.apk；29,022,042 字节；SHA-256 77eb33e0288fb22069a4bad9e3631eef66f73d4aa790eabaaa788ea3f9760ca6。交付副本 outputs/screenqa-0.9.8-user-preview.apk。
- DEV Debug APK：app/build/outputs/apk/developer/debug/app-developer-debug.apk；29,027,796 字节；SHA-256 5d81bee1860966ea9cb547ddd93c9a1c65621f64ffe07310879787fe5911f092。交付副本 outputs/screenqa-0.9.8-dev-answer-preview.apk。

### 验证边界

按用户要求未运行模拟器、未安装 APK、未连接真机、未调用真实 API：编译与 JVM 回归不能证明真机上的观感、帧率与交互手感。液态玻璃的模糊采样、极光背景与逐帧焦散在低端设备上的实际开销未实测；玻璃底图是快照，滚动时面板背后的内容会变化，画面会略显滞后。自定义模型与接口是否被服务端接受、thinking 开关对答题质量的实际影响未实测。积分/余额体系仍不存在；Root、日志、无障碍与自动答题能力与 0.9.7 相同。正式 Release 仍需正式签名。本轮改动未提交 git，是否提交、是否打 tag 由用户设备验收后决定。

## 0.9.9 / versionCode 23 · 2026-10-02 · 底部 Dock 改用库实现的实时液态玻璃

本轮改动由 DSH 完成（不是 Codex 的改动），逐文件清单与设计取舍另见 DSH-LOG.md；本条目只记录按项目原格式的版本级行为与验证证据。

### 行为与实现

用户要求给应用内底部 Dock 加“实时”液态玻璃：浮动胶囊、实时采样背后页面、轻微模糊 + 边缘折射 + 高光，图标与文字保持清晰，保留原有导航逻辑、选中状态，选中指示器平滑移动、按压带弹性；先用 68dp 高度与 20dp 左右留白；适配浅色/深色、系统导航栏 Insets、键盘弹出与大字体；低版本或不支持效果的设备降级，不模糊整页或 Dock 内文字。用户同时指定：先只出开发者端，用户测试无问题后再继续。

- 选型（用户给的分支）：界面是纯 View 体系（71 个 Java 文件、0 个 Kotlin、无 Compose），因此用 XML/View 方案 `com.github.QWEA0:liquidglass:v2.0.11`；仓库为 JitPack，写入 settings.gradle 的 dependencyResolutionManagement（该块是 FAIL_ON_PROJECT_REPOS，仓库只能加在这里）。Compose 方案（Kyant0/AndroidLiquidGlass）不适用。
- 新增 Dock 抽象接口：常量 PANEL_HEIGHT_DP=68、ROOM_DP=12、SIDE_DP=20，`heightFor(Context)` 在 fontScale≥1.3 时追加 10dp 容纳大字体；暴露 setPalette/setGlassEnabled/setReduceMotion/setItems/setListener/setSelected/setLifted/setBackdrop/setBackdropSource，让 MainActivity 不再依赖具体实现。
- 新增 LiquidDock（库封装）：胶囊圆角 34dp、bevelWidth 10dp、refractionHeight 14dp、refractionFalloff 1.6、dispersionStrength 0.12、blurAmount 0.10、saturation 1.15、pressScale 0.95、elasticity 0.15、accessibilityMode AUTO。关键一处是库的 `setEnableDynamicBackground` 默认 false（底图只抓一次，玻璃看起来是冻结的），本版指向页面层并显式打开逐帧刷新；采样源设成页面层而不是根布局，避免把 dock 自己卷进采样。库的几何属性以像素为单位，全部用 Ui.dp 换算。
- 兼容与降级：库自带 JNI 只覆盖 arm64-v8a / armeabi-v7a（`libnativegauss.so`），而本工程 ABI 是 arm64-v8a + x86_64，因此按 Build.SUPPORTED_ABIS 做闸门；闸门不通过或 `new LiquidDock(this)` 抛出任何 Throwable（含 NoClassDefFoundError、UnsatisfiedLinkError）时回退到原有 GlassDock 自绘面板，并写一条 `dock liquid-fallback` 日志，导航逻辑、选中态与图标完全不变。
- GlassDock 改为实现 Dock 接口，高度/边距/圆角改用接口常量，药丸指示器锚点改到 items 的左边并加布局监听重新定位，按压缩放到 0.96 与库对齐。
- MainActivity：dock 字段类型改为 Dock 并由 `createDock()` 决定实现；dock 宽度改 MATCH_PARENT（胶囊由库按 20dp 边距自己撑满）；根布局 Insets 监听的底部改为 `max(systemBars.bottom, ime.bottom)`，键盘弹出时 dock 浮在键盘之上（targetSdk 35 强制 edge-to-edge，软键盘不再自动顶起窗口）；`aurora` 从根布局子视图移入页面层，并给页面层设置主题背景色——采样源必须不透明，否则透明区域等于没有内容（这也正是上一版玻璃效果差的直接原因）。
- settings.gradle 增加 JitPack 仓库；app/build.gradle 增加 `implementation 'com.github.QWEA0:liquidglass:v2.0.11'`，versionCode 22→23、versionName 0.9.8→0.9.9（两个 flavor）；README.md、docs/DEVELOPER_BUILD.md、验证记录.md 同步版本、依赖与外观说明，构建命令去掉 `--offline` 并说明首次构建需要联网解析 JitPack。
- 未改动：包名、minSdk 26、targetSdk 35、权限、Root 与日志能力、自动识题与辅助选答逻辑、Screenshare/无障碍流程、Token 统计与 API 请求格式。

### 验证及证据

按用户要求只编译开发者端（“先不要编译用户端”），用户端 assemble/lint/测试本轮未运行。`.local/glass-dock-final.log`：`:app:assembleDeveloperDebug`、`:app:lintDeveloperDebug`、`:app:testDeveloperDebugUnitTest` 全部 BUILD SUCCESSFUL in 15s，52 个 actionable tasks（16 executed、36 up-to-date）。testDeveloperDebugUnitTest 140 项全部通过，无失败/错误/跳过；developerDebug Lint 0 error / 24 warning，无新增 error（本轮新代码引入的 UseCompatLoadingForDrawables 已用 @SuppressLint 禁用，理由是工程从不链接 appcompat）。verifyNoEmbeddedSecrets 通过。

首次编译失败并已修：MainActivity.java:167 与 :239 把接口类型 Dock 直接当 View 使用（root.addView(dock, lp)、dock.getLocationInWindow(...)），改为显式转成 View 后通过。

aapt 核验开发者包 cn.screenqa.lite.dev / 0.9.9-dev-answer-preview，versionCode 23、minSdk 26、targetSdk 35，native-code arm64-v8a 与 x86_64。apksigner verify exit 0（v2 方案 true），证书 SHA-256 仍为 0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912；zipalign 4 字节检查通过。APK 内 ARM64 侧 JNI 为 libnativegauss.so（278,488 字节）与既有 libmlkit_google_ocr_pipeline.so，x86_64 侧只有 MLKit 的 so，没有 libnativegauss.so，与 ABI 闸门一致。

交付前按文档改动重新打包一次（`.local/glass-dock-release.log`，BUILD SUCCESSFUL in 6s，52 个 actionable tasks，mergeDeveloperDebugAssets 与 packageDeveloperDebug 实际执行），确认 APK 内 assets/DEVELOPER_BUILD.md 与仓库 docs/DEVELOPER_BUILD.md 逐字节一致，两者 SHA-256 同为 1c9af904593fa39412e20188e515d8f45928cd041b3a5f3be29e11485ea8c4f6。

- DEV Debug APK：app/build/outputs/apk/developer/debug/app-developer-debug.apk；29,691,904 字节（比 0.9.8 开发包 +664,108 字节，主要来自库的 libnativegauss.so）；SHA-256 acc13b0c1f0ddba80ed6b2695ae1721def27cb57a3529472baea99963b0632fa。交付副本 outputs/screenqa-0.9.9-dev-answer-preview.apk，汇总见 outputs/SHA256-0.9.9.txt。

### 验证边界

按用户要求未编译用户端、未运行模拟器、未安装 APK、未连接真机、未调用真实 API。用户要求的“用带文字或图片的滚动页面检查背景实时更新、导航点击、可读性、滑动流畅度”只能在真机上验证，本轮未执行；库自带 JNI 只覆盖 arm64-v8a 与 armeabi-v7a，x86_64 设备（含模拟器）会走自绘降级，因此模拟器也无法验证库路径。逐帧采样与极光背景同时运行时的帧率与耗电未实测；浅色主题（ivory_light）下未选中项的文字/图标颜色由库按亮度自动决定，若真机可读性不足需要改为显式指定（库只暴露 selectedTintColor 一个颜色入口）。库的 2.0 透镜特性要求 API 33+，API 26–32 走经典 C++/NEON 管线，效果强度会有差异。本轮改动未提交 git，等用户测试开发者端反馈后再继续。

## 0.9.9 修订 / versionCode 23 · 2026-10-02 · 毛玻璃 Dock、主题切换修复、默认悬浮窗绿

本轮改动由 DSH 完成（不是 Codex 的改动），接在上一段 0.9.9 之后，仍属 0.9.9（versionCode 保持 23，该包从未提交 git、也未对外发布）。逐文件说明见 DSH-LOG.md。

### 行为与实现

用户反馈三条：底部液态玻璃 dock 应当一定程度半透明、接近毛玻璃效果；主题颜色切换无效需要修复；默认配色改成与悬浮窗一致的配色。

- 主题切换无效的根因：MainActivity.rebuildForTheme() 只用内存里的 palette 重建界面，没有先 settings.theme() 读回刚写入的 theme_id，所以整屏颜色一直是旧值；另外重建会把用户当前所在的子页重置回页签首页。现在重建前先 `palette=settings.theme()` 并 applyWindowColors()，重建后按 selectedDetail 原位恢复（openDetail(id,false)，不播入场动画）。
- 新增第七套主题 overlay_green（悬浮窗绿）并作为 DEFAULT：取色直接对齐悬浮窗的深绿面板、薄荷强调色与浅色文字（surface 0xFF142420、control 0xFF1D3930、border 0xFF3A6250、foreground 0xFFF4FFF8、accent 0xFF6EE7A8，对应原来的悬浮窗夜色调），玻璃与极光偏绿。为避免“默认换了但老安装看不出来”，Settings.migrateThemeDefault() 做一次性迁移：从未存过 theme_id 的安装、以及仍停在旧默认 black_gold 的安装都跟随新默认，用户显式选过的其它主题保持不变；MainActivity.onCreate() 与 CaptureService.onCreate() 都会调用一次（悬浮窗先启动也能对齐）。
- 毛玻璃观感：手绘管线的面板色调透明度由 `glass ? 0.68 : 0.96` 降到 `glass ? 0.44 : 0.88`，顶部高光由 0.85 降到 0.55；采样底图放大倍率 1.07 保留并在下缘取偏移，因此面板能透出背后滚动的文字与卡片，同时靠边缘折射、描边与阴影维持轮廓与可读性。库渲染路径的半透明程度由库的材质决定：暗色主题改用 GlassMaterial.CLEAR（库自带压暗层，透出页面内容但仍保证可读），浅色主题保留可读性优先的 REGULAR；玻璃着色强度从 0.20/0.16 降到 0.14/0.10，让背后的页面更多地透出来。
- 手绘降级路径也做实时采样：滚动时以 170ms（滚动中）/ 90ms（停止后）节流重新抓页面层底图；库路径本身逐帧采样，不再重复抓图，用新的 Dock.liveSampling() 区分两条路径。dock 的让位从内容容器 padding 改为 ScrollView 底部内边距 + clipToPadding=false，卡片因此可以滚到胶囊下方——玻璃背后必须有会动的内容，否则毛玻璃看不出来。
- 未改动：包名、minSdk 26、targetSdk 35、权限、Root 与日志能力、识题与选答逻辑、库版本（仍是 liquidglass v2.0.11）。

### 验证及证据

`.local/glass-frost-final.log`：`:app:assembleDeveloperDebug`、`:app:testDeveloperDebugUnitTest`、`:app:lintDeveloperDebug` 全部 BUILD SUCCESSFUL in 18s；testDeveloperDebugUnitTest 140 项全部通过（fail=0 / error=0 / skip=0），developerDebug Lint 0 error / 24 warning（与上一轮同数，无新增 error）。中间两次单独的 assemble（`.local/glass-frost.log`，9s）与 test+lint（`.local/glass-frost-verify.log`，17s）同样通过。按用户要求仍只编译开发者端。

APK 核验：app/build/outputs/apk/developer/debug/app-developer-debug.apk；29,692,521 字节；SHA-256 a21a5c4db0e28b5bcdf6d49ca8158e000bf9747915b907117836f04e59e6cec6；aapt 为 cn.screenqa.lite.dev / 0.9.9-dev-answer-preview / versionCode 23 / minSdk 26 / targetSdk 35 / native-code arm64-v8a 与 x86_64；apksigner verify exit 0（v2 方案 true），证书 SHA-256 仍为 0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912；zipalign 4 字节通过；APK 内 assets/DEVELOPER_BUILD.md 与仓库 docs/DEVELOPER_BUILD.md 逐字节一致（同为 6a8f4ffb2573af9089bc11dda88a88cc8fe25b311691803c5dbfa8a8498cb716）。文档与材质改动完成后重新打包（`.local/glass-frost-release.log`，BUILD SUCCESSFUL in 6s，37 个 actionable tasks；随后 `.local/glass-frost-final.log`，18s），上面记录的 SHA-256 即为最后一个包。交付副本 outputs/screenqa-0.9.9-dev-answer-preview.apk 与 outputs/SHA256-0.9.9.txt 已按新包更新。

### 验证边界

毛玻璃的透过率是否“够像毛玻璃”、悬浮窗绿在真机上的观感、切换主题后悬浮助手是否即时跟随、x86_64 自绘降级路径的实际观感，都只能在真机上确认，本轮未执行（未运行模拟器、未安装包、未连接真机）。本轮改动未提交 git。

## 0.9.9 修订 / versionCode 23 · 2026-10-02 · Codex：半透明毛玻璃优先与有限绘制降耗

### 行为与实现

用户在本轮确认主题配色切换已经生效，要求忽略该问题并降低 CPU 优化优先级。因此本轮以玻璃观感为主，不修改主题点击、主题持久化、迁移与配色切换路由；保持 0.9.9 / versionCode 23，仅交付 developerDebug。已有 DSH 未提交修改保留，DSH-LOG.md、ApiRequest、Settings、CaptureService 与识题/无障碍/Root 逻辑未作本轮修改。

- LiquidDock.java：深浅色主题均采用 CLEAR；着色基色从偏白改为 surface 混合 20% accent，强度深色 0.14→0.06、浅色 0.10→0.08，减轻乳白实心胶囊感。保留轻模糊、边缘折射/高光与 AUTO 无障碍降级；选中项显式使用主题强调色，未选中图标/文字继续由库按背景亮度适配。
- GlassBackdrop.java、新增 GlassBlur.java / GlassBlurTest.java：原滑动窗口算法一边覆盖输入一边读取离窗像素，后半段因此读取已经模糊过的数据，导致方向性拖尾。改为独立输入/输出数组并复用像素缓冲；0.2 倍降采样图的两遍 box blur 半径从 22 减至 4，避免把背后文字/卡片轮廓抹成大片均匀色。MainActivity.java 在抓图时暂时排除自绘玻璃层，避免已有快照再次卷入底图形成反馈；LiquidGlassView.java 降低玻璃色调覆盖率 0.44→0.16，关闭玻璃时底色保持不透明；GlassDock.java 修正玻璃子 View 边距未计入采样原点的偏移。
- 有限 CPU 调整：已缓存 v2.0.11 classes.jar 字节码表明库 GPU/CPU 绘制分支在 enableDynamicBackground=true 时会于绘制结束再次 invalidate，形成常驻绘制；backdropScrollListener 则独立执行 invalidate。故关闭自循环和传感器高光，保留库的滚动监听与 GPU 原生采样，不切换到强制 CPU 的 customBackdropCapture。AuroraBackground.java 改用 100ms 定时回调（10Hz），并通知库 Dock 更新；短时 Dock 位移动画仍刷新底图。页面/布局变更也显式刷新，避免减弱动效下底图冻结。没有可见玻璃卡且 Dock 自行采样的页面跳过额外整页 CPU 抓图；卡片相位只更新当前可见的 View。恢复前台重启既有动画，重建时停止旧卡片驱动并用 UI 代次拦截过期采样回调，避免跨页面复用已释放底图。
- Dock.java 注释、README.md、docs/DEVELOPER_BUILD.md 与外观说明同步刷新策略；没有增加依赖、androidx 或 Kotlin 源码。

### 验证及证据

本地 JDK 17.0.20.1+1 / SDK35，TEMP、TMP 指向 source/.local/tmp；沙箱内 java.exe 启动返回 -1073741502，改为获准宿主权限运行指定工具链，没有绕过或跳过测试。两次 developerDebug assemble / test / lint 均 BUILD SUCCESSFUL：.local/codex-glass-fix.log 与最终 .local/codex-glass-final.log，最终 17s、52 个任务（19 executed / 33 up-to-date）。原有 140 项 JVM 单测全部通过，加上 2 项模糊回归共 142 项，failure=0 / error=0 / skipped=0；新回归覆盖脉冲对称性、输入不被覆盖、ARGB 独立通道、零半径与超过行长的半径，并与直接求均值参考实现比较。developerDebug Lint 0 error / 24 warning，与交接基线同数；verifyNoEmbeddedSecrets 通过。未运行 userDebug / userRelease 构建、Lint 或测试。

刷新开销的静态证据：修改前 Dock 在每次绘制后请求下一帧，极光 ValueAnimator 每显示帧 invalidate；修改后 Dock 无自循环，空闲极光每 100ms 至多请求一次刷新，滚动与短时交互按事件实时更新。此频率变化是代码路径证据，不能换算为真实 %CPU 降幅或 janky 比例；本轮按用户降优先级要求不扩展性能实验。

APK 核验：cn.screenqa.lite.dev / 0.9.9-dev-answer-preview / code23 / minSdk26 / targetSdk35 / arm64-v8a + x86_64；apksigner verify exit0、v2 true，证书 SHA-256 0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912；zipalign -c 4 exit0；内嵌 assets/DEVELOPER_BUILD.md 与源码说明一致。

- DEV APK：D:\Codex\Program\Screenqa\source\app\build\outputs\apk\developer\debug\app-developer-debug.apk；29,693,543 字节；SHA-256 e4e44790bfcb8886118f186fc567c22d23bdc18547145947bd4603fc223e0456。
- 交付副本：D:\Codex\Program\Screenqa\outputs\screenqa-0.9.9-dev-answer-preview.apk；汇总 D:\Codex\Program\Screenqa\outputs\SHA256-0.9.9.txt 更新为本包哈希。用户端产物未更新。

### 验证边界

未运行模拟器、未连接或寻找真机、未安装包、未调用真实 API。主题切换已由用户确认生效，本轮不宣称修复此问题。半透明观感、浅色/深色图标可读性、API26–32 经典管线与 API33+ 透镜管线的实际差异、空闲 10Hz 极光的手感和实际 CPU/janky 对比尚无真机证据；当前交付是待设备验收的改进包，不能宣称玻璃与 CPU 的设备验收已完成。AUTO 仍尊重系统要求的不透明降级，未强制绕过无障碍设置；自绘降级滚动采样维持 170/90ms 节流，极光不逐帧抓自绘底图，仍有少量滞后。

优先复测：用深色主题与象牙白主题，让带颜色和文字的卡片滚过胶囊，观察背景轮廓、模糊与边缘高光，同时检查三项导航及标签可读性；再检查减弱动效开/关、玻璃开/关和切页后的底图。CPU 比较后续再做：同一设备、同一首页滚动场景 15 秒，比较旧 a21a5c4d… 包与本包的 top %CPU、gfxinfo P90 与 janky 比例。本轮所有修改保持工作区状态，未 git commit / push。

## 1.0.0 / versionCode 24 · 2026-10-02 · 大学生小帮手正式发布准备

### 行为与实现

用户确认上一轮版本表现可用，要求正式改名为“大学生小帮手”、替换其提供图标、版本 1.0.0、准备可上传 GitHub 的 ZIP、起草用户协议和隐私政策，并在首次启动及应用顶部声明用途限制。用户随后确认先生成未签名 Release、附本地签名说明，运营者、邮箱与仓库地址暂未确定。此前只编 developer 的范围在本轮正式版准备请求下扩展到 userRelease / userDebug / developerDebug；仍不提交 Git、不运行模拟器、不连接或寻找真机。

- app/build.gradle：code23→24、versionName 1.0.0；用户包去掉 preview 后缀，开发者包为 1.0.0-dev，保留 applicationId cn.screenqa.lite / cn.screenqa.lite.dev 和 developerRelease 禁用。用户 Release 启用既有 R8/资源收缩，仍不设置签名配置。
- 图标：用户 PNG 原样复制至 drawable-nodpi/brand_mascot.png，SHA-256 91c007fdcdc6ecf3487f66cfb3337cc16f29a6da1ef6936d8f6fcf1e783c0360，1254×1254、透明背景；main/developer ic_app 改为相同原图。新增 adaptive launcher icon，前景 20% inset 避免角色被圆形/圆角裁切，提供 monochrome 主题图标。通知小图标改为独立单色书本图标，避免彩色角色在状态栏变成块状。Manifest、用户/开发者名称及无障碍标题、页面品牌文字、悬浮提示、剪贴板说明同步改名；CaptureService 与 TextAnswerExecutor 仅涉及名称和图标，识题、权限与辅助执行算法未改。
- MainActivity.java、新增 UsageDeclaration.java / UsageDeclarationTest.java：首次启动或声明修订号不匹配时，仅显示声明页面，提供协议/隐私草案全文、未预选确认复选框、同意进入与不同意退出。确认后保存本机文档修订号/时间，才初始化功能页面与应用自身常规日志；生命周期、回调和新 Intent 在声明页不继续权限/识题启动路径。用途声明禁止线上/线下考试中的违规答题、代考、协助作弊及所有非法用途，强调仅限个人学习研究。不是考试检测或技术封锁。顶层标题下面新增 framework TextView 无限 marquee，所有主界面子页可见；关于页可再次阅读声明和两份草案。
- docs/legal/USER_AGREEMENT.md、PRIVACY_POLICY.md：按现有功能起草，运营者与邮箱明确待填写；描述本机 OCR、发送文字至所选 AI 接口、API Key 加密、本地用量/日志、权限、撤销与删除，以及 ML Kit 诊断联网；不捏造完全离线、无数据收集或绝对免责承诺。Gradle prepareLegalAssets 将两份唯一来源文档打包至各 Variant 的 assets/legal，避免手工副本漂移。草案定稿后须递增 UsageDeclaration.REVISION 并重新构建。
- Release 首轮 R8 失败原因：liquidglass v2.0.11 引用 Android16 API36 的 RuntimeColorFilter / RuntimeXfermode，compileSdk35 无这些类。app/proguard-rules.pro 仅针对两项可选、版本受控类 dontwarn，不使用全局忽略、不禁用 R8、不引入 androidx 或 Kotlin 源码。图标目录变更曾遇到增量资源链接失败，清理 app 生成目录后干净构建通过；移除空的旧 v26 目录并加入 monochrome 后 Lint 恢复原 24 warning。
- README 重写为当前名称与发布状态，docs/DEVELOPER_BUILD.md 更新；新增 docs/RELEASE_PREPARATION.md、THIRD_PARTY_NOTICES.md 和 tools/sign-release.ps1。签名脚本交互读取密码、先 zipalign 再签名并核验，不保存密码或覆盖输入。ZIP 为 repository 源码快照与 release 附件准备，不创建/上传远程仓库。DSH-LOG.md 未改，原 LICENSE 保留；ApiRequest 与 Settings 未修改。

### 验证及证据

指定本地 JDK17 / SDK35 / Gradle8.9，TEMP/TMP 指向工程 .local/tmp；最终干净构建 .local/release-1.0.0-clean-final.log BUILD SUCCESSFUL in 36s，158 个任务（156 executed / 2 up-to-date）。三套 assemble / test / lint 通过，各 144 项 JVM 测试、failure=0 / error=0 / skipped=0（原 142 项 + 2 项声明修订回归）。最终三套 Lint .local/release-1.0.0-lint-final.log 均 0 error / 24 warning，BUILD SUCCESSFUL in 13s；verifyNoEmbeddedSecrets、CRLF-aware git diff --check、签名脚本 PowerShell 语法解析通过。声明回归覆盖首次/旧修订必须重新确认、仅当前修订有效，不构成 Android 点击或生命周期实测。

APK aapt 核验用户名称“大学生小帮手”、cn.screenqa.lite / 1.0.0 / code24，开发者名称“大学生小帮手 Dev”、cn.screenqa.lite.dev / 1.0.0-dev / code24；均 minSdk26、targetSdk35、arm64-v8a+x86_64。三份 APK zipalign -c 4 通过，assets/legal 两份全文与源文档一致；开发者含 DEVELOPER_BUILD.md，用户包均不含。两个 Debug APK v2 签名通过，证书 SHA-256 0ccbfe0a24a21e7c84d3b9ba471ff16fe743daf47068a7c8671cf42a74932912。Release 的 apksigner 返回 DOES NOT VERIFY / Missing META-INF/MANIFEST.MF，符合用户指定未签名状态，不能直接安装。

- userRelease：app/build/outputs/apk/user/release/app-user-release-unsigned.apk；27,179,212 B；SHA-256 28ca5b7eec3314573d9ea915f159baece8a96e456c70c9f6a5d9623392d9754e。ZIP 内 release/college-helper-1.0.0-unsigned.apk。
- userDebug：app/build/outputs/apk/user/debug/app-user-debug.apk；30,727,033 B；SHA-256 ea2f078f714f51d352990c4baca50e69666a2c57f86b05ad685d76ea95f72008。ZIP 内 release/college-helper-1.0.0-testing-debug.apk，仅供用户自行设备验收。
- developerDebug：app/build/outputs/apk/developer/debug/app-developer-debug.apk；30,731,784 B；SHA-256 93ef6b2fd6b3f2c592506a9abf7c15b555068e27c660472b0e28cbf089136353。本轮 ZIP 主交付源码与用户发布准备，不把 DEV 包当正式附件。

ZIP 路径：D:/Codex/Program/Screenqa/outputs/大学生小帮手-1.0.0-GitHub发布准备.zip；源码包括当前全部未提交 UI 修改及本轮修改，排除 .git、.local、缓存、构建目录、机器配置、签名材料和运行日志；内附文件校验清单及本地签名说明。ZIP 自身哈希记录在外侧 SHA256-1.0.0-release-kit.txt。

### 验证边界

未安装/运行设备、未进行真机或模拟器测试、未调用真实 AI、未上传 GitHub、未 git commit / push；源码继续在 feature/root-enhancements / HEAD b346c55 工作区。首次声明的点击、拒绝退出、旋转恢复、升级后提示、TalkBack/大字体、顶部 marquee 与原有识题/液态玻璃设备效果仍需用户验收。

运营者、邮箱、仓库及正式签名尚未确定，协议仍是明确标注的草案，当前交付不能当作已完成正式发布。正式签名脚本仅经过语法检查，未使用正式密钥实签；不要把调试证书当正式证书。ML Kit 启动 Provider 的初始化/诊断流量、第三方和跨境处理告知、法律文本审定以及素材权利都仍需正式发布者确认。用途声明不能消除法定责任，也不保证技术上阻断全部考试用途。后续补齐政策时须更新声明修订号、重新构建/签名、更新 SHA-256，再由发布者上传 GitHub。

## 1.0.0 / versionCode 24 · 2026-10-02 · 自定义 API 修复与 GitHub 上传审查

### 行为与实现

用户要求先检查 GitHub 上传缺口并收集所需信息，检查敏感信息，修复自定义 API 模型切换后保持版本号交付测试，完整打包留到下一次任务。版本仍为用户 1.0.0 / 开发者 1.0.0-dev、code24。

- 根因：MainActivity 选择自定义时把 model_id / endpoint_url 写成空字符串，Settings 的有效值读取随即回退默认值，sync 又依照有效值隐藏自定义输入框，因此自定义选择失效。请求层已经读取运行时模型，不需修改 ApiRequest。
- 新增 ApiSettingsSelection.java，单独维护本页模型/接口编辑模式。选择自定义不写空值、不清除已保存配置，保留草稿；选择预设继续立即生效，保存时忽略隐藏自定义字段。MainActivity 保存按钮改为“保存配置”，先验证非空模型及安全 HTTPS 地址，再保存 Key，成功后才写入模型与接口；选项勾选与输入框显隐依据编辑模式，不依赖默认值回退。无效输入明确报错，不以默认配置冒充保存成功。
- 新增 ApiSettingsSelectionTest.java 六项回归，覆盖默认有效值下进入自定义、重新读取已保存自定义配置、预设忽略旧草稿、空模型/空接口拒绝、HTTP/凭据/查询参数/片段 URL 拒绝。
- 新增 docs/GITHUB_READINESS.md，区分源码上传与正式 APK 分发条件，并列出仓库账号/名称/可见性、运营者/邮箱、签名方式、图标权利及设备验收待补。已通过表单向用户收集信息，未收到的项继续待填写。当前无 Git remote，未配置 CI 不妨碍源码上传。旧 ZIP 明确标记缺少本次修复，不重新生成完整 ZIP 或 Release APK。

### 验证及证据

使用既有 JDK17/SDK35、可写 .local/tmp TEMP/TMP，`.local/api-custom-1.0.0-validation.log` BUILD SUCCESSFUL in 23s（123 tasks，38 executed）；assembleUserDebug / assembleDeveloperDebug、对应 lint、三套 JVM 测试通过。developerDebug / userDebug / userRelease 各 150 项，failure=0 / error=0 / skipped=0。`.local/api-custom-1.0.0-release-lint.log` BUILD SUCCESSFUL in 28s；三套 Lint 均 0 error / 24 warning，保持基线。CRLF-aware git diff --check 通过。

两个 APK aapt 核验名称、包名与原版本，已有调试证书 v2 签名验证通过，zipalign -c 4 通过：

- outputs/college-helper-1.0.0-api-fix-testing.apk：cn.screenqa.lite / 1.0.0 / code24；30,917,250 B；SHA-256 306e11125cb794c9c6e3c584cbc18808928b9040bacb900a1e3f925fd44b24fb。
- outputs/college-helper-1.0.0-dev-api-fix-testing.apk：cn.screenqa.lite.dev / 1.0.0-dev / code24；30,924,643 B；SHA-256 c8e13fcf518c67283ff17582ac176628397419d973901b1b9859d5483d0647d6。

最终敏感信息扫描检查 130 个 Git 跟踪/未忽略文件、全部可达历史的 190 个 blob（9 次提交）、两份新测试 APK 和旧发布 ZIP 共 2,126 个条目（包含旧 ZIP 内 APK 解包）。规则包括常见 AI/Google/AWS/GitHub/GitLab/Slack 令牌、私钥头、长 Bearer 字串及显式长凭据赋值，匹配数 0；待上传文件清单没有 .env、机器 local.properties、签名配置或密钥文件。报告保存在不上传的 .local/github-secret-audit-final.json。追加本段及验证记录后对更新的文档再扫描，匹配数 0。API Key 运行时由用户填写，不向用户索要密钥；扫描未输出秘密原文。

本轮未改 ApiRequest.java / Settings.java / DSH-LOG.md，三者 SHA-256 与本轮开始一致（56ad8b1b… / 543908a3… / 408b46a0…）；它们相对 HEAD 的既有工作区修改仍保留。未改默认模型、默认接口和静态 requestBody 重载，未改识题、无障碍或 Root 算法。

### 验证边界与后续

没有运行模拟器、连接设备、安装 APK、调用真实 API、创建密钥、提交/推送 Git 或上传 GitHub。本轮回归验证状态和参数处理，实际接口支持的模型 ID、权限/声明界面和 API 往返需用户设备验收。规则扫描无法证明任意格式或加密秘密不存在，不涵盖外部远程仓库、设备私有数据和依赖供应链。下一任务依据用户填写的信息及测试反馈，直接从最新工作区开始完整打包，重新生成 Release/ZIP/清单/哈希，不沿用旧 ZIP；若协议定稿，递增声明修订号。

## 1.0.0 / versionCode 24 · 2026-10-02 · Cenbyte 发布信息与正式签名打包

### 行为与实现

用户提供公开仓库 https://github.com/Cenbyte/ScreenQA、运营者 Cenbyte、邮箱 Cenbyte.dev@outlook.com、工程外已有签名文件，要求代理本机签名并且不上传密钥；确认图标为本人原创、本轮测试 OK。本轮按前次安排开始完整打包，版本不变，不提交 Git、不上传 GitHub。

- docs/legal 两份文本补入运营者、邮箱和公开仓库，文档修订由 1 升至 2、随 1.0.0 发布生效，补齐私下权利请求渠道；保留第三方 SDK 初始化/诊断数据说明与实际行为尚未设备核验的边界，不宣称完全离线或完成全部合规审查。
- UsageDeclaration.REVISION=2，声明正文补入运营者及邮箱；MainActivity 仅去掉协议按钮上的“草案”标记。旧修订确认需要重新接受新文本。未修改识题、无障碍、Root 算法和 API 请求层；自定义模型修复原样保留。
- README、DEVELOPER_BUILD、RELEASE_PREPARATION、GITHUB_READINESS、THIRD_PARTY_NOTICES 同步发布信息和原创图标确认，新增 RELEASE_NOTES_1.0.0.md 发布文案。原 LICENSE 和 DSH-LOG.md 保留。仓库只读 ls-remote 成功，远程 main / HEAD 为 5b6323c1acf2a72a0ccde10ed3ce050580bd142b；没有创建本地 remote 或修改远程。
- 使用已有工具脚本签名：密钥仍留在工程外，先本机读取唯一 PrivateKeyEntry 别名 screenqa-release，再 zipalign、签名、核验。密码经屏蔽回显的交互输入，仅在进程内临时使用，不写入源码、脚本参数、文件或发布 ZIP。用户曾把密码发到聊天，已提醒不要复述，并建议本机更换 keystore 密码；不会声称密码从未出现在聊天记录。没有新建签名密钥。

### 验证及证据

.local/release-1.0.0-cenbyte-final.log：完整三套 assemble / lint / JVM tests BUILD SUCCESSFUL in 34s，157 tasks / 59 executed。三套各 150 项单测无失败、错误，Lint 均 0 error / 24 warning。正式 APK 内两份 assets/legal 与当前源文档逐字节一致；aapt 核验名称“大学生小帮手”、cn.screenqa.lite / 1.0.0 / code24 / minSdk26 / targetSdk35。

正式输出 outputs/college-helper-1.0.0.apk：27,224,087 B；SHA-256 606024542b53c6a658a6223c14e1ea6d2020203c565280705b8da01b802f096c。apksigner verify v2=true / v3=true / 1 signer，RSA4096；公开证书 SHA-256 915397ae3ac3e6ae30b6f42a1cd71ba50c8266e81085f397f9998842093dd75e；zipalign -c 4 通过。APK 必须携带公开签名证书，但不携带私钥。

本轮额外生成的 userDebug / developerDebug 仅放在 outputs，未混入正式 ZIP；对应 SHA-256 121518aa72055587da7d86b9e10160336e3eec60261464b4e8c214a94fb5b6a2 / 6dc51d7ae607a3bf5fba65d12a7df92314a25d6d7356c369b3bbb95d7d91cafb。

交付 college-helper-1.0.0-GitHub.zip：repository 最新源码快照（包括已有未提交 UI）、release 正式签名 APK 与 SHA256SUMS、START_HERE、RELEASE_NOTES、逐文件 SHA-256 清单。最终 ZIP 自身哈希位于 outputs/SHA256-1.0.0-final.txt，避免记录 ZIP 内导致循环哈希。打包时对所有条目回读验证哈希，禁止密钥、机器配置、.git/.local/缓存和运行日志进入。

交付前对当前上传文件、新 APK、完整 ZIP 进行凭据规则扫描，并额外核对本次已知密码和 keystore 原始字节，报告保存在不上传的 .local/final-signed-kit-secret-audit.json；只有匹配数为 0 且条目哈希全部通过时交付。此前所有可达历史 190 个 blob 已扫描，本轮 HEAD 未变，无新增提交。

### 验证边界

用户报告上一轮测试 OK；本轮代理未运行模拟器、连接设备、调用真实 API 或实测发布信息更新后的设备流程。正式签名通过不等于全部法律、SDK 流量或第三方许可核验通过。协议改动后首次/升级的再次确认仍请发布者验收。源码未提交，GitHub 未上传，旧发布准备 ZIP 和 api-fix-testing 文件均是历史附件，不用于本次正式发布。ApiRequest.java、Settings.java、DSH-LOG.md 的 SHA-256 与本轮开始相同，保留原有工作区改动。

## 1.0.0 / versionCode 24 · 2026-10-02 · 输入长按编辑与普通 Key 键盘

### 行为与实现

用户发现输入框长按不显示复制/粘贴，要求取消 API Key 处调用安全键盘的行为；限定只改此功能，保持版本号，最后重新打包，不重复上一轮全面校验。版本、声明修订、API 请求、识题、无障碍、Root 与其他 UI 行为不变。

- 新增 ClipboardEditText.java；MainActivity 的通用 field() 工厂及 Key 字段都使用此 EditText 子类，覆盖模型/接口、辅助填写参数、预算等现有输入。长按直接显示 framework PopupMenu，提供复制、剪切、粘贴、全选，避免仅依赖 OEM 浮动 ActionMode 菜单。存在选区时使用选区，未选中且非空时先全选；空输入仍可粘贴。菜单按选区/剪贴板状态启用，命令调用 framework onTextContextMenuItem，沿用系统文本编辑与输入过滤，不自建剪贴板存储。普通光标/触摸编辑保留。
- API Key 去掉 TYPE_TEXT_VARIATION_PASSWORD，改为普通文本并明文显示；增加 NO_SUGGESTIONS 与 IME_FLAG_NO_PERSONALIZED_LEARNING / NO_EXTRACT_UI 请求，继续禁用字段状态保存和自动填充。App 不再通过密码类型请求安全键盘；具体输入法是否遵循学习请求仍由输入法决定。Key 加密保存逻辑没有改动。
- RELEASE_NOTES_1.0.0.md 补充输入修复，并明确此次未重复以前的测试/扫描，不把以前 150 项结果当作本轮重新运行的证据。DSH-LOG.md 未改，未提交或上传 GitHub。

### 专项验证与打包

.local/input-editing-1.0.0-build.log：仅 assembleUserRelease / assembleUserDebug / assembleDeveloperDebug，BUILD SUCCESSFUL in 23s，120 tasks / 19 executed。Release 自带的 lintVital 与 preBuild 凭据 guard 按正常构建链运行，未另行执行全套 lint、JVM tests、历史/源码/ZIP 凭据扫描或设备测试。

专项静态检查通过：两处输入入口均使用 ClipboardEditText、没有遗留 new EditText(this)、Key 不再含密码类型、四个编辑命令齐全且调用系统编辑函数、键盘学习禁用请求存在。此检查仅证明配置与命令接线，不能证明设备上的长按菜单与输入法行为已经实测。

沿用已有发布密钥和 screenqa-release 别名签名一次，脚本正常完成签名后验证及对齐。outputs/college-helper-1.0.0-input-fix.apk：27,224,087 B；SHA-256 e84c40e313db35fa37d92c8b65981801eadec7e1c0ac8cebbf04e2cc5b442327。没有新建或复制签名密钥，密码没有写文件。

完整输出 college-helper-1.0.0-input-fix-GitHub.zip，包含最新源码快照与本次签名用户包；ZIP 内正式 APK 仍名为 release/college-helper-1.0.0.apk。外侧 SHA256-1.0.0-input-fix.txt 为本次 APK/ZIP 校验；调试副本 college-helper-1.0.0-dev-input-fix.apk / college-helper-1.0.0-user-input-fix-debug.apk 单独放在 outputs，不混入正式 ZIP。打包仅保留既有文件筛选及逐条目哈希完整性处理，没有重复全量安全审计。

### 设备复测边界

本轮不运行模拟器、不连接设备、不调用 API。请在设备分别长按空 Key 输入框、已有模型/接口文字及数字输入，复测粘贴、复制、剪切、全选和部分选区；确认 Key 调用普通输入法，粘贴/编辑后仍能保存。不要把普通文本 Key 的显示状态误认为密码遮罩仍存在。

## 1.0.0 / versionCode 24 · 2026-10-02 · 首页精简与版本卡片重影

### 行为与实现

用户要求去掉首页“当前答案”，修复截图中“我的”版本介绍卡片的类似散光现象；本轮只交付新版正式签名 APK，不打 ZIP。用户明确条件授权：其宣布测试成功之后才写入 Git 并生成完整 GitHub 发布准备 ZIP；本轮不提前提交或上传。

- MainActivity 移除首页当前答案标题、玻璃答案卡、附属策略/模型标签，以及对应 TextView 成员、重建清理和刷新分支。保留悬浮助手启动/停止、权限入口、服务答案展示/复制及识题逻辑；既有输入复制粘贴修复保持。
- 重影原因：已有采样流程仅跳过 LiquidGlassView 本身，GlassCard 内图标和文字仍被采入底图，模糊并放大后绘回同一卡片，叠在清晰前景下。LiquidGlassView 新增 isCapturingBackdrop getter，GlassCard.dispatchDraw 在采样期间跳过所有子视图；正常显示仍绘制玻璃与前景。沿用 MainActivity 同步采样标记及 finally 恢复，不隐藏布局、不改可访问性节点、不增加采样循环；保留玻璃材质和动效。
- 版本继续 1.0.0 / code24，声明修订 2、API、无障碍和 Root 逻辑不改；DSH-LOG.md 不改。

### 专项验证及交付

.local/home-glass-1.0.0-build.log：仅 assembleUserRelease，BUILD SUCCESSFUL in 23s，49 tasks / 10 executed；专项静态检查确认首页答案引用全部移除、卡片采样排除分支存在、采样 finally 恢复仍存在。未重复全面单测、lint 或敏感扫描；正常 Release 构建自带的 lintVital 与 preBuild guard 保持运行。

沿用原发布密钥和 screenqa-release 别名，签名脚本验证及对齐成功；输出 outputs/college-helper-1.0.0-home-glass-fix.apk，27,224,087 B，SHA-256 3039703e4cc02dbdf6b6e7d80b778b3f7e15e9b820756457f6fc155e4f55dea9。密码及密钥不写入交付文件。没有生成新 ZIP、没有提交 Git、没有上传 GitHub。

### 验证边界与下一步

没有模拟器、设备或真实 API 测试。采样逻辑检查不代表截图中的观感已在设备验证；请用户复测首页精简、版本卡片文字/图标是否仍有模糊副本、深浅主题及滑动时的表现。待用户明确宣布测试成功，再按其条件授权提交当前待发布工程，并生成最新完整 GitHub 发布准备 ZIP；不沿用此前 ZIP。

## 1.0.0 / versionCode 24 · 2026-10-02 · 正式来源署名、协议定稿与发布交付

### 行为与实现

用户确认首页精简、版本卡片重影修复测试无问题，启用其此前条件授权：提交当前完整待发布工程并生成 GitHub 发布准备 ZIP。随后要求补齐应用开源信息中的仓库、识别组件、skill、素材来源与作者，并去掉协议的草稿措辞和标题 # 符号；历史 skill 无法回忆，用户指定标为“不明来源”。版本仍为 1.0.0 / code24。

- 新增 docs/legal/OPEN_SOURCE_INFO.txt：列明 Cenbyte/ScreenQA 仓库和联系渠道、Google ML Kit 中文 OCR 16.0.1、Liquid-Glass-Android v2.0.11（维护者 QWEA0、MIT 作者 pandadog）、Kotlin、Google Material Design Icons、Cenbyte 原创角色、历史界面 DSH 与测试/构建工具；历史 skill 如实注明名称、作者和出处未确认。最后一行“由 Codex 辅助开发”。未把未核实来源伪称为已确认运行依赖。
- 根据上游官方来源校正 Google ML Kit 许可表述：SDK/模型适用 Google ML Kit 条款，不能因示例代码 Apache-2.0 而给整个 SDK 标同一许可。附 Liquid Glass 原始 MIT 与 Material Icons 原始 Apache-2.0 许可全文，三枚 Dock 图标增加来源注释，未改图形。
- MainActivity 关于页面开源入口展示仓库，打开完整署名文本，增加 MIT/Apache 许可查看入口和 Codex 页脚；移除仓库待提供占位。
- 用户协议及隐私政策使用正式表述，去掉标题 # 与草稿/起草措辞，保留真实 SDK 初始化及诊断联网说明。文档修订与 UsageDeclaration.REVISION 同步升为 3，已接受旧修订的用户需要再次确认。Gradle 自动将五份法律/署名/许可文本打包。
- 同步发布文案和准备说明。DSH-LOG.md、ApiRequest.java、Settings.java 未在本次署名/协议收尾中修改；保留此前已验收工作区修改。

### 专项验证与签名

.local/release-credits-policy-1.0.0.log：仅 assembleUserRelease，BUILD SUCCESSFUL in 26s，49 tasks / 23 executed；正常 preBuild guard / lintVital 随构建执行。未重复之前全面单测、全量 lint、历史或源码敏感审计，未运行模拟器、设备或真实 API。

专项检查确认两份发布协议不含标题 #、草稿措辞或待填写占位，署名包含不明 skill 和 Codex 尾行。签名 APK 的五份 assets/legal 与当前源文档逐字节一致。原发布证书 RSA4096，SHA-256 915397ae3ac3e6ae30b6f42a1cd71ba50c8266e81085f397f9998842093dd75e；apksigner 签名验证及 zipalign 对齐通过。没有生成新密钥，密码仅通过屏蔽回显输入在进程内临时使用，不写入交付文件。

正式输出 outputs/college-helper-1.0.0-release.apk：27,228,426 B；SHA-256 dfb15e3bf71a2a70768b6ae6c99029a9201f6182eebedd31b75a2a4eaf678576。

### Git 与交付边界

按用户测试通过后的授权，将当前完整工程提交至本地 feature/root-enhancements 分支；提交作者使用用户提供的 Cenbyte / Cenbyte.dev@outlook.com，仅以命令级配置指定，不改全局身份。原 LICENSE、DSH-LOG.md 及已有 UI 工作均保留。没有推送或创建 GitHub Release。

完整交付 college-helper-1.0.0-release-GitHub.zip：已提交源码快照、正式签名 APK、发布说明、提交标识和逐文件校验清单；打包筛选排除密钥、本机配置、缓存与 .git，并回读每个 ZIP 条目验证完整性。ZIP 自身与 APK 校验位于外侧 SHA256-1.0.0-release.txt，避免循环哈希；实际提交标识随 ZIP 记录。以前 APK/ZIP 是历史附件，不作为本次最终包。

用户已验收前轮首页与玻璃修复；本轮新增署名及协议由构建与文本打包检查验证，未声称设备实测或法律审查完成。公开上传由用户执行。
