# AGENT.md — TickCount 开发须知

安卓倒数日应用。单模块 Kotlin + Compose；第三方运行库仅 osmdroid（万疆地图）。照片以
相册为真身（引用 + SHA-256 指纹），权限与联网均为按需（通知 / 定位 / 麦克风 / 相册读取；
地图瓦片）——文档不做「无权限 / 离线」宣传。

## 环境

- JDK 17：`D:\jdk\jdk-17.0.20`（`run.bat` 已内置；直接跑 gradle 需先 `$env:JAVA_HOME='D:\jdk\jdk-17.0.20'`）
- Android SDK：`C:\Users\13056748\AppData\Local\Android\Sdk`，adb 在其 `platform-tools\` 下
- 验证设备：模拟器 **emulator-5554**，AVD 名 `Medium_Phone_API_36`，易掉线——重启：
  `emulator -avd Medium_Phone_API_36 -no-snapshot-save`（冷启动，但 userdata 不丢，
  应用数据与相册都在；冷启动后运行时权限会重置，记得 `pm grant`）；用户手机从未连接过
- Windows + PowerShell 5.1，控制台 GBK：中文输出乱码是显示问题；但脚本与管道有真坑，见「大坑」末三条

## 编译 / 测试 / 安装

> **当前开发模式：轻量为主——`testDebugUnitTest` + `compileDebugKotlin` 必跑，`installDebug`
> 装机可以代劳；装好之后应用内的一切操作（导航、点按、走查、彩排）由用户本人执行，
> 助手不写 adb / UI 自动化脚本代劳——需要应用内验证时给用户一份简明步骤清单，
> 结果听用户回报。**

```powershell
$env:JAVA_HOME='D:\jdk\jdk-17.0.20'
.\gradlew.bat testDebugUnitTest compileDebugKotlin   # 单测 + 编译，改完代码必跑
.\gradlew.bat installDebug                            # 装到连接的设备/模拟器
.\run.bat                                             # 一键：installDebug + 启动 MainActivity
.\gradlew.bat assembleRelease                         # 本地签名 APK -> app/build/outputs/apk/release/
```

- `run.bat` = 编译 + 安装 + `adb shell am start` 启动，设备用 `adb devices` 确认。
- 本地 `assembleRelease` 直接出**可升级的同签名 APK**（签名读 `.tools/签名密钥文件/keystore.properties`，无它则退回 debug 签名）。
- `更新日志.txt` / README / china_*.md 已声明为测试任务输入（`app/build.gradle.kts` 的 `tasks.withType<Test>` 块）：改后普通增量 `testDebugUnitTest` 自动重跑受影响测试，不假绿，无需 `--rerun-tasks`。

## 功能 → 文件

| 功能 | 主要文件 |
|---|---|
| 留莹（倒数日列表） | `ui/HomeScreen.kt`、`ui/EventCard.kt`、`domain/CountdownList.kt` |
| 倒数日模型与存储 | `data/CountdownEvent.kt`、`data/EventCodec.kt`、`data/EventStore.kt` |
| 详情（大字倒计时、照片叠放、背景图） | `ui/EventDetailScreen.kt`、`ui/components/PhotoStack.kt`、`ui/components/BackgroundCrop.kt`、`data/EventBackground.kt` |
| 新建 / 编辑 / 一键添加多个 | `ui/EventEditorScreen.kt`、`domain/BatchAdd.kt` |
| 作陪（心愿） | `ui/ThingsScreen.kt`、`data/Wish.kt` |
| 敬心（时光胶囊、拆信动画、信笺分页、声音信） | `ui/CapsuleScreen.kt`、`data/TimeCapsule.kt`（按年分文件、自动迁移）、`data/CapsuleVoice.kt`（录音与私有文件）、`domain/VoiceWaveform.kt`（波形归一）、`ui/components/VoiceNote.kt`（播放行） |
| 照片引用（相册为真身）与体检 | `domain/MediaRef.kt`（`g:` 引用条目：大小/尺寸/拍摄时间/MIME/媒体 id/SHA-256）、`data/GalleryMedia.kt`（选取建档 + 身份阶梯解析 + `relocatedId`）、`domain/GalleryRelink.kt`（重连映射纯规则）、`ui/GalleryHealth.kt`（体检对话框：发现即回写新 id，入口在数据页）、`ui/components/GalleryPermission.kt`；旧私有副本（fp-/bg-）双读兼容，删除引用不动相册。换机走系统克隆：相册随克隆迁移，记录用备份导入，体检自动重连 |
| 万疆（足迹、媒体、位置、地图） | `ui/WanJiangScreen.kt`、`ui/WanJiangMap.kt`（osmdroid、高德瓦片、年份轨迹、图上选点）、`data/Footprint.kt`、`data/FootprintMedia.kt`；离线地名在 `domain/ChinaPlaces.kt`（资源 `res/raw/china_places.md`，由 `.tools/行政区划/RegionGen.java` 从 kakuilan/china_area_mysql 的 **UTF-8** dump 生成，跑生成器要带 `-Dfile.encoding=UTF-8`，源数据不入仓库） |
| 行舟（星图、彗星航行、背景音乐——内嵌默认曲 `res/raw/voyage_default.mp3`，可另选并拷入私有目录循环播放，无需存储权限） | `ui/VoyageScreen.kt`（全 Canvas，沉浸全屏）、`domain/VoyageLayout.kt` |
| 日历（月历、事件标记、当天列表） | `ui/CalendarScreen.kt`、`ui/calendar/MonthCalendar.kt`、`ui/calendar/MonthYearPickerDialog.kt`、`domain/CalendarMath.kt` |
| 纪念日与整数日（100/365/520/999/1314） | `domain/Milestones.kt`，布线在 `CalendarScreen.kt` 与 `MainViewModel.kt` |
| 提醒（含胶囊到期） | `notify/Reminders.kt`（`EXTRA_KIND` 区分单闹钟）、`notify/ReminderReceiver.kt`、`notify/BootReceiver.kt`；设置页 `ui/NotificationsScreen.kt` |
| 桌面小组件（单个 / 清单） | `ui/widget/` 整目录 |
| 农历与每年重复 | `domain/Lunar.kt`、`domain/YearlyRepeat.kt`、`ui/LunarText.kt` |
| 外观（主题、色相、玻璃体系、全局背景） | `ui/AppearanceScreen.kt`、`ui/theme/Style.kt`（GlassTokens 分层：大卡全玻璃 `glassSurface`（跟手高光+按压回弹）/编辑静板 `interactive=false`/小件薄纱 `glassPane`）、`ui/theme/` 其余 |
| 声带（磁带墙：录音 / 导入 / 播放） | `ui/VoiceTapeScreen.kt`（墙+机座+录音棚）、`data/VoiceTape.kt`（模型+编解码+按年存储）、`data/TapeAudio.kt` 所在即 `data/VoiceTape.kt`（录音仓 `vt-`）、`data/TapeImport.kt`（视频抽音轨/音频直取/波形解码）、`ui/components/ActionIcons.kt`（PlayIcon/PauseIcon/InfoIcon） |
| 照片查看器（三入口共享）与弹窗防护 | `ui/components/PhotoViewer.kt`（缩放/双击/缩略条/EXIF 玻璃面板；`ViewerMedia` 照片与足迹双源）、`data/PhotoExif.kt`（EXIF 按需 IO 读取）、`ui/components/DialogWindowGuard.kt`（横屏幽灵窗防护，全屏弹窗挂载） |
| 数据管理（备份 / 加密） | `ui/DataScreen.kt`、`data/Backup.kt`、`data/BackupCrypto.kt`（.ics 导出已退役，2026-11-05） |
| 抽屉 / 设置 / 标题栏 | `ui/AppDrawer.kt`、`ui/SettingsScreen.kt`、`ui/AppTitleBar.kt`、根画面 `ui/TickCountApp.kt` |
| 编辑器动作栏图标 | `ui/components/ActionIcons.kt` |
| 更新日志 / 项目介绍（应用内） | `ui/ChangelogScreen.kt`、`ui/ProjectIntroScreen.kt`、`domain/Changelog.kt`、`domain/Readme.kt` |

文案：中文 `app/src/main/res/values-zh/strings.xml`，英文 `values/strings.xml`。
测试：`app/src/test/`，与 domain/data/notify 一一对应。

## 结构一览（数据怎么流）

- **UI 层不碰存储**：所有 Screen 经 `MainViewModel`，状态是五张 StateFlow
  （events / wishes / capsules / footprints / settings）。改动一律三步：
  改 flow → `store.save()` → 受影响的旁路（提醒 `reschedule()`、小组件 `refreshAllWidgets()`）。
- **持久化五处**：事件+心愿+设置在 `SharedPreferences`（EventStore / WishStore /
  SettingsStore）；胶囊与足迹按年 JSON 文件（`TimeCapsule.kt` / `Footprint.kt`）；
  声带磁带同款按年 JSON（`data/VoiceTape.kt` 的 `VoiceTapeStore`）。
  **照片以相册为真身**：新选取存 `g:` 引用（指纹），显示直读相册；旧私有副本
  （`bg-`、`fp-`）双读兼容，且 `fp-` 会在启动时自动迁移为引用并删副本（权限到位后补跑，
  迁移映射在 `saveFootprint` 重放防踩）。语音信笺（`cv-`）、声带录音（`vt-`）与背景图
  仍是私有副本。
  备份（`Backup.kt`）是六类记录的全集，**媒体像素不随走**——相册照片靠「系统换机迁移
  相册 + 体检按指纹重连」；**两类语音例外**：语音信笺（`voices`）与声带录音（`tapeVoices`）
  没有相册真身，均以 Base64 随备份 JSON 走（恢复时只给存活记录写回、`isOurs` 校验）；
  背景图副本不随走（设计如此）。
- **提醒只有一个闹钟**（`notify/Reminders.kt`），由四处维护：数据持久化后、
  `reschedule()`、开机 `BootReceiver`、闹钟自身触发后重排。
- **仓库根的两个文档同时是应用内容**：`更新日志.txt` → 更新日志页，
  `README.md` / `README.en.md` 的「功能 / 一键添加多个 / 倒计时的读法 / 桌面小组件」节
  → 项目介绍页（`domain/Readme.kt` 按标题抽取，标题改名即静默丢节）。

## 新增功能注意事项（血泪清单，2026-10-26 审查出 11 个 bug 的教训）

1. **新数据类型六件套缺一不可**：模型 + store；备份导出/导入对称；**MERGE 按 id 合并**
   （不许整体替换——applyBackup 曾对心愿/胶囊/足迹整体覆盖）；导入预览统计（previewOf）；
   删除记录时清理其关联文件（removeCapsule 曾漏删照片）；中英两份 strings。
2. **每条写路径末尾三问**：闹钟要重排吗（`reschedule()`）？小组件要刷吗？强停后重开
   能自愈吗（应用启动会 reschedule，新数据源必须被它覆盖到）？
3. **提醒选「下一个」必须按时刻排序**（不是日期+存储序，同日不同时曾年年漏提醒），
   排期时把目标 id 写进 intent（`EXTRA_ID`），否则触发时投递的是存储顺序第一条。
4. **编辑器与媒体**：选了就拷进私有目录，**保存才写进记录**；删除引用 ≠ 删文件要分开
   （当前实现是当场删文件，取消编辑即丢照片——已知未修的重构点）。
5. **任何来自备份的文件名进 `File()` 前必须过 `isOurs` 式校验**（限定前缀 + 不含
   `/` `\`），否则一个恶意备份就能删沙箱内任意文件。
6. **存储迁移三原则**：以「旧容器还存在」为触发（不是新目录为空）；按 id 幂等合并
   （中断可续、已删不复活）；解码失败保源不删。
7. **按年文件 save**：只删「读得出来且年份已空」的文件——读不出的一律保留。
8. **文本切分/计数防代理对**：emoji 是 2 个 UTF-16 code unit，按 length/substring
   硬切会在边界劈成乱码（信笺分页曾中招）。
9. **同位置复用的 `rememberSaveable` 以内容为 key**（如 `rememberSaveable(list.toTypedArray())`），
   否则上一个条目的状态串到下一个（万疆查看器曾串台）。
10. **列表删除按 id，不按下标**；Compose 各 item 的 key 前缀互异。
11. **测试盲区自警**：单测只覆盖 domain/ 纯逻辑；store 迁移、备份 apply、闹钟接线、
    Compose UI 全靠人审。改这些文件时，把上面 1–10 逐条对一遍。
12. `更新日志.txt` / README / china_*.md 已在 `app/build.gradle.kts` 声明为测试输入，
    改后增量测试自动重跑、不会假绿；`--rerun-tasks` 纪律已废除（2026-11-04）。
13. **图形 / 视觉类新功能先用最小可视样确认审美，再铺开实现**——星图真实天文渲染
    一次做完，结果整体返工（2026-10-27）。文字与规则层面的功能没有这个风险。
14. **操作一律图标化，不写文字按钮**：保存 ✓（CheckIcon）、关闭 ×（CloseIcon）、
    删除 🗑（TrashIcon）、重录/撤销 ↺（ReopenIcon）——全部出自 `ui/components/ActionIcons.kt`
    的手绘 Canvas 体系，新动作先看这套够不够用，不够就补画一枚同风格的，不引入
    Material 图标也不写「完成 / 删除」字样。
15. **拉取远端后先编译再动工**——远端提交可能没编译过就推了（漏 import 实录，
    2026-11-02）。先 `compileDebugKotlin` 清一遍，别把别人的坑叠进自己的改动。
16. **引用式照片的删除语义**：解除关联 ≠ 删除——`g:` 条目永远不碰相册与文件；
    迁移/重连一律「先改记录、后动文件」，中断最坏留孤儿文件，绝不留指向已删
    文件的记录。编辑器保存要重放迁移映射（`convertedCopies`），防止旧快照复活
    已删副本的名字。
17. **后台迁移/重连依赖相册读权限**——无权限时指纹搜索全空但静默返回 null。
    依赖它的启动任务要挂在「权限授予」事件上（见 `onGalleryPermissionGranted`），
    不能假设首次启动就有效。
18. **`Modifier.transformable` 会认领单指 pan**——与 `HorizontalPager` 同用时，
    过 touch slop 的单指拖动被它消费，翻页从此失灵。修法是手写仲裁：双指归缩放、
    已放大时单指归平移、静止单指放行（见 `PhotoViewer.kt` 的 ZoomablePhoto）。
19. **系统缩略图 ≠ 原图**——`loadThumbnail`/`thumbnail()` 返回的是相册格子的
    压缩小图，全屏查看用它必然发糊（2026-11-09 用户一眼识破）。整页显示必须
    整流解码（`GalleryMedia.bitmap` / `FootprintMedia.bitmap`），缩略条才用微缩图。
20. **Compose Dialog 的窗口默认有底、有压暗**——内容想透出 Activity（背景图
    穿透），必须 `(LocalView.current.parent as DialogWindowProvider).window`
    清 `windowBackground` + `setDimAmount(0f)`，纱再薄也救不了不透明的窗底。

## 发版流程

**极简三步，本地不跑任何验证（2026-11-04 用户钦定）：**

1. **bump 版本**（`app/build.gradle.kts`）：日历方案 `年.月.日.当天序号`；`versionCode` =
   年月日紧缩 + 两位序号（如 `2026.10.26.1` → `26102601`）。
2. **拼更新日志**：`更新日志.txt` 顶部加同号小节（`版本号` 下接一行 `----`，条目行用
   ✨/🎨/🔄/🔧，末行 `📦 安装：下载 TickCount-<版本>.apk 直接安装`）。受加密驱动所迫，
   从 HEAD blob 拼、写到 `%TEMP%`、断言结构（行 1 标题 / 行 2 `====` / 行 4 新版本号、
   标题整行唯一、零 U+FFFD、LF），再 `git hash-object -w` + `update-index --cacheinfo`
   入索引——这是唯一保留的机械步骤。**写够行数**：ChangelogTest 要求最新节条目 > 5 行
   （`主要变化` + ≥4 条 + 安装行）、版本号 == versionName，由 CI 强制。
3. **提交 → 打标签 `v<版本号>` → 推 `main` + 标签。完事。**

- **CI 是唯一闸门**（`.github/workflows/android.yml`）：tag 上自动 单测 → `assembleRelease`
  → 发 Release 挂 `TickCount-<版本>.apk`（说明取自更新日志对应小节）。本地不再跑测试、
  不 `--rerun-tasks`、不 `installDebug` 验证——功能在开发时已测过，重复闸门浪费用户
  时间（2026-11-04 裁决；同日 `--rerun-tasks` 纪律废除，测试输入已在
  `app/build.gradle.kts` 声明，增量测试对日志/README 变更自动敏感）。
- **发版提交不加塞**：只含 版本号 + 日志 + 本发功能文件。任何顺手改动（哪怕工程优化）
  先问过用户、单独提交。

## 大坑（务必避开）

- **`更新日志.txt` 严禁用 WPS / Word 打开或保存**——办公套件会把它另存成自有格式
  （OLE .doc / 加密容器），git 提交进去是二进制，CI 的 ChangelogTest 必挂
  （2026-10-26 连环事故，上一会话的「乱码」事故疑似同因）。查看用记事本 / VSCode。
- 若它又被翻写（`git status` 显示莫名 modified、或 `git add` 后 diff 是 Bin）：不要从
  工作区 add。把干净文本放到 `%TEMP%`（不受影响），然后：
  `git hash-object -w <临时文件>` 得 sha → `git update-index --cacheinfo 100644,<sha>,更新日志.txt`，
  提交前 `git rev-parse HEAD:更新日志.txt` 校验。
- **本机的透明加密驱动会翻写 `.txt` / `.csv` 文件**（写入后几秒内变密文，git 提交进
  去就是二进制；`.md` 免疫）。仓库内新生成的数据文件一律用 `.md` 后缀；`.txt` 只保留
  `更新日志.txt` 一个，动它前先验字节。**它也可能在提交后翻写工作区副本**——以索引
  blob 为准（`git cat-file -p :更新日志.txt`），别信工作区。
- **PowerShell + GBK 三坑**（2026-11-02 全数踩过）：
  ① 外部程序输出过管道会被按 GBK 解码——`git show | Out-File` 中文全花；字节级取数
  用 `cmd /c "git … > 文件"` 或 `git cat-file -p <sha>`。
  ② `.ps1` 里写中文字面量（无 BOM 被按 ANSI 解析）会烂——中文字符串用
  `[char]0x4E07` 码点拼，或把中文放进独立 UTF-8 数据文件由脚本读。
  ③ `$ErrorActionPreference='Stop'` 时 adb 的 stderr 进度行会终止脚本——脚本容错化
  （Dump 带重试与大小校验）。另：`$home` 是保留变量不可赋值。
- **日志拼接以 blob 实际行尾为准**（仓库存 LF、工作区检出 CRLF，autocrlf）：猜错行尾
  会让插入点漂移、整个文件结构错乱（2026-11-02 实录，标题重复 + 孤字行）。拼接后必须
  做结构断言：标题整行唯一、行 1 = 标题 / 行 2 = `====` / 行 4 = 新版本号、旧节完整、
  零 U+FFFD。
- **模拟器 UI 走查纪律**：每步 `uiautomator dump` 验证再点，盲坐标必漂（应用会恢复
  上次页面）；权限弹窗、系统选择器、软键盘都会顶偏布局；**`pm clear` 后应用跟随系统
  语言，查找文案要双语兜底**。`adb shell content delete` 不带 id 会**清空整个集合**
  （实录：模拟器相册被清空）——只按 id 单行删。
- **模拟器硬崩会回滚 userdata 写入**（2026-11-03 三次实录）：Download 里的新文件、
  `pm clear`、应用内写入，崩溃后可能整体消失/复原（QEMU 磁盘缓存未落盘）。涉及数据
  突变的彩排，改完先重启模拟器确认变化仍在，再下结论；拉到本机的副本才是可信证据。
- `README.md` / `README.en.md` 的「功能 / 一键添加多个 / （倒计时的读法）/ 桌面小组件」
  二级标题是应用内「项目介绍」的抽取锚点（`domain/Readme.kt`），**勿改名**。
- CI 的签名密钥在仓库 secrets（`KEYSTORE_BASE64` 等），fork/换仓库要重配。
