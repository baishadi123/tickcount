# AGENT.md — TickCount 开发须知

安卓倒数日应用。单模块 Kotlin + Compose，纯离线，无第三方运行库。

## 环境

- JDK 17：`D:\jdk\jdk-17.0.20`（`run.bat` 已内置；直接跑 gradle 需先 `$env:JAVA_HOME='D:\jdk\jdk-17.0.20'`）
- Android SDK：`C:\Users\13056748\AppData\Local\Android\Sdk`，adb 在其 `platform-tools\` 下
- 验证设备：模拟器 **emulator-5554**（装 debug 版）；用户的手机从未连接过
- Windows + PowerShell，控制台是 GBK，中文输出乱码是显示问题不是文件问题

## 编译 / 测试 / 安装

> **当前开发模式：轻量级——只改代码，验证止于 `testDebugUnitTest` + `compileDebugKotlin`。
> 不启动虚拟机、不安装新版本（`run.bat` / `installDebug` 暂不用）。**

```powershell
$env:JAVA_HOME='D:\jdk\jdk-17.0.20'
.\gradlew.bat testDebugUnitTest compileDebugKotlin   # 单测 + 编译，改完代码必跑
.\gradlew.bat installDebug                            # 装到连接的设备/模拟器
.\run.bat                                             # 一键：installDebug + 启动 MainActivity
.\gradlew.bat assembleRelease                         # 本地签名 APK -> app/build/outputs/apk/release/
```

- `run.bat` = 编译 + 安装 + `adb shell am start` 启动，设备用 `adb devices` 确认。
- 本地 `assembleRelease` 直接出**可升级的同签名 APK**（签名读 `.tools/签名密钥文件/keystore.properties`，无它则退回 debug 签名）。
- 改了 `更新日志.txt` 后跑测试要加 `--rerun-tasks`：它不是 gradle 的任务输入，否则可能 UP-TO-DATE 吃旧结果（假绿）。

## 功能 → 文件

| 功能 | 主要文件 |
|---|---|
| 留莹（倒数日列表） | `ui/HomeScreen.kt`、`ui/EventCard.kt`、`domain/CountdownList.kt` |
| 倒数日模型与存储 | `data/CountdownEvent.kt`、`data/EventCodec.kt`、`data/EventStore.kt` |
| 详情（大字倒计时、照片叠放、背景图） | `ui/EventDetailScreen.kt`、`ui/components/PhotoStack.kt`、`ui/components/BackgroundCrop.kt`、`data/EventBackground.kt` |
| 新建 / 编辑 / 一键添加多个 | `ui/EventEditorScreen.kt`、`domain/BatchAdd.kt` |
| 作陪（心愿） | `ui/ThingsScreen.kt`、`data/Wish.kt` |
| 敬心（时光胶囊、拆信动画、信笺分页） | `ui/CapsuleScreen.kt`、`data/TimeCapsule.kt`（按年分文件、自动迁移） |
| 万疆（足迹、媒体、位置） | `ui/WanJiangScreen.kt`、`data/Footprint.kt`、`data/FootprintMedia.kt` |
| 行舟（星图、彗星航行） | `ui/VoyageScreen.kt`（全 Canvas，沉浸全屏）、`domain/VoyageLayout.kt` |
| 日历（月历、事件标记、当天列表） | `ui/CalendarScreen.kt`、`ui/calendar/MonthCalendar.kt`、`ui/calendar/MonthYearPickerDialog.kt`、`domain/CalendarMath.kt` |
| 纪念日与整数日（100/365/520/999/1314） | `domain/Milestones.kt`，布线在 `CalendarScreen.kt` 与 `MainViewModel.kt` |
| 提醒（含胶囊到期） | `notify/Reminders.kt`（`EXTRA_KIND` 区分单闹钟）、`notify/ReminderReceiver.kt`、`notify/BootReceiver.kt`；设置页 `ui/NotificationsScreen.kt` |
| 桌面小组件（单个 / 清单） | `ui/widget/` 整目录 |
| 农历与每年重复 | `domain/Lunar.kt`、`domain/YearlyRepeat.kt`、`ui/LunarText.kt` |
| 外观（主题、色相） | `ui/AppearanceScreen.kt`、`ui/theme/` |
| 数据管理（备份 / 加密 / .ics） | `ui/DataScreen.kt`、`data/Backup.kt`、`data/BackupCrypto.kt`、`domain/Ics.kt` |
| 抽屉 / 设置 / 标题栏 | `ui/AppDrawer.kt`、`ui/SettingsScreen.kt`、`ui/AppTitleBar.kt`、根画面 `ui/TickCountApp.kt` |
| 编辑器动作栏图标 | `ui/components/ActionIcons.kt` |
| 更新日志 / 项目介绍（应用内） | `ui/ChangelogScreen.kt`、`ui/ProjectIntroScreen.kt`、`domain/Changelog.kt`、`domain/Readme.kt` |

文案：中文 `app/src/main/res/values-zh/strings.xml`，英文 `values/strings.xml`。
测试：`app/src/test/`，与 domain/data/notify 一一对应。

## 结构一览（数据怎么流）

- **UI 层不碰存储**：所有 Screen 经 `MainViewModel`，状态是五张 StateFlow
  （events / wishes / capsules / footprints / settings）。改动一律三步：
  改 flow → `store.save()` → 受影响的旁路（提醒 `reschedule()`、小组件 `refreshAllWidgets()`）。
- **持久化四处**：事件+心愿+设置在 `SharedPreferences`（EventStore / WishStore /
  SettingsStore）；胶囊与足迹按年 JSON 文件（`TimeCapsule.kt` / `Footprint.kt`）；
  媒体像素在私有目录（`EventBackgrounds` 前缀 `bg-`、`FootprintMedia` 前缀 `fp-`），
  记录里只存文件名；备份（`Backup.kt`）是五类记录的全集，**媒体像素不随走**。
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
12. 改 `更新日志.txt` 或 README 后跑测试加 `--rerun-tasks`（它们不是 gradle 任务输入，
    会假绿）。

## 发版流程

1. **bump 版本**（`app/build.gradle.kts`）：日历方案 `年.月.日.当天序号`；`versionCode` =
   年月日紧缩 + 两位序号（如 `2026.10.26.1` → `26102601`）。
2. **写更新日志**：`更新日志.txt` 顶部加同号小节（`版本号` 下接一行 `----`，条目行用
   ✨/🎨/🔄/🔧，末行 `📦 安装：下载 TickCount-<版本>.apk 直接安装`）。
   **`ChangelogTest` 强制：最新节版本号 == versionName，且条目 > 5 行。**
3. **本地验证**：`.\gradlew.bat testDebugUnitTest --rerun-tasks`（必须真跑，见上）。
4. **提交** → 打标签 `v<版本号>` → 推 `main` + 标签。
5. CI（`.github/workflows/android.yml`）自动：单测 → `assembleRelease`（密钥来自仓库
   secrets，本地无需操作）→ 发 GitHub Release，APK 名 `TickCount-<版本>.apk`，
   Release 说明取自更新日志对应小节（`writeReleaseNotes` 任务，缺节会直接报错）。
6. 发布后到 Releases 页确认 APK 已挂。

## 大坑（务必避开）

- **`更新日志.txt` 严禁用 WPS / Word 打开或保存**——办公套件会把它另存成自有格式
  （OLE .doc / 加密容器），git 提交进去是二进制，CI 的 ChangelogTest 必挂
  （2026-10-26 连环事故，上一会话的「乱码」事故疑似同因）。查看用记事本 / VSCode。
- 若它又被翻写（`git status` 显示莫名 modified、或 `git add` 后 diff 是 Bin）：不要从
  工作区 add。把干净文本放到 `%TEMP%`（不受影响），然后：
  `git hash-object -w <临时文件>` 得 sha → `git update-index --cacheinfo 100644,<sha>,更新日志.txt`，
  提交前 `git rev-parse HEAD:更新日志.txt` 校验。
- `README.md` / `README.en.md` 的「功能 / 一键添加多个 / （倒计时的读法）/ 桌面小组件」
  二级标题是应用内「项目介绍」的抽取锚点（`domain/Readme.kt`），**勿改名**。
- CI 的签名密钥在仓库 secrets（`KEYSTORE_BASE64` 等），fork/换仓库要重配。
