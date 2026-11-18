# AGENT.md — TickCount 开发须知

安卓倒数日应用。单模块 Kotlin + Compose；第三方运行库仅 osmdroid（万疆地图）。照片以相册为真身（引用 + SHA-256 指纹），权限与联网均按需（通知 / 定位 / 麦克风 / 相册读取；地图瓦片）——文档不做「无权限 / 离线」宣传。

## 环境

- JDK 17：`D:\jdk\jdk-17.0.20`（`run.bat` 已内置；直接跑 gradle 需先 `$env:JAVA_HOME='D:\jdk\jdk-17.0.20'`）
- Android SDK：`C:\Users\13056748\AppData\Local\Android\Sdk`，adb 在 `platform-tools\`
- 验证设备：模拟器 emulator-5554，AVD `Medium_Phone_API_36`，易掉线——重启 `emulator -avd Medium_Phone_API_36 -no-snapshot-save`（冷启动 userdata 不丢，应用数据与相册都在；运行时权限会重置）。用户手机从未连接过
- Windows + PowerShell 5.1 控制台 GBK：中文乱码多为显示问题，但管道有真坑，见「大坑」

## 编译 / 测试 / 安装

> **轻量模式**：`testDebugUnitTest` + `compileDebugKotlin` 改完必跑；`installDebug` 可代劳装机。应用内操作（导航/点按/走查）由用户执行，助手不写 adb/UI 自动化代劳——给用户步骤清单，结果听回报。

```powershell
$env:JAVA_HOME='D:\jdk\jdk-17.0.20'
.\gradlew.bat testDebugUnitTest compileDebugKotlin   # 单测 + 编译，必跑
.\gradlew.bat installDebug                            # 装机
.\run.bat                                             # installDebug + 启动
.\gradlew.bat assembleRelease                         # 本地签名 APK（.tools/签名密钥文件/keystore.properties，无则 debug 签名）
```

- `更新日志.txt` / README / china_*.md 已声明为测试输入（`app/build.gradle.kts` 的 `tasks.withType<Test>`）：改后增量测试自动重跑，不假绿。

## 功能 → 文件

| 功能 | 主要文件 |
|---|---|
| 留莹（倒数日列表） | `ui/HomeScreen.kt`、`ui/EventCard.kt`、`domain/CountdownList.kt` |
| 倒数日模型与存储 | `data/CountdownEvent.kt`、`data/EventCodec.kt`、`data/EventStore.kt` |
| 详情（大字倒计时、照片叠放） | `ui/EventDetailScreen.kt`、`ui/components/PhotoStack.kt`、`ui/components/BackgroundCrop.kt`、`data/EventBackground.kt` |
| 新建 / 编辑 / 一键添加多个 | `ui/EventEditorScreen.kt`、`domain/BatchAdd.kt` |
| 作陪（心愿） | `ui/ThingsScreen.kt`、`data/Wish.kt` |
| 敬心（胶囊、拆信、信笺、声音信） | `ui/CapsuleScreen.kt`、`data/TimeCapsule.kt`（按年分文件）、`data/CapsuleVoice.kt`、`domain/VoiceWaveform.kt`、`ui/components/VoiceNote.kt` |
| 照片引用与体检 | `domain/MediaRef.kt`（`g:` 条目）、`data/GalleryMedia.kt`（身份阶梯）、`domain/GalleryRelink.kt`、`ui/GalleryHealth.kt`、`ui/components/GalleryPermission.kt`。旧副本（fp-/bg-）双读兼容；换机：系统克隆相册 + 备份导记录 + 体检重连 |
| 万疆（足迹、媒体、地图） | `ui/WanJiangScreen.kt`、`ui/WanJiangMap.kt`（osmdroid/高德瓦片）、`data/Footprint.kt`、`data/FootprintMedia.kt`；离线地名 `domain/ChinaPlaces.kt`（`res/raw/china_places.md`，生成器在 `.tools/行政区划/`，跑时带 `-Dfile.encoding=UTF-8`，源数据不入库） |
| 行舟（星图、背景音乐） | `ui/VoyageScreen.kt`（全 Canvas）、`domain/VoyageLayout.kt`；默认曲 `res/raw/voyage_default.mp3`，可选曲拷私有目录 |
| 日历 | `ui/CalendarScreen.kt`、`ui/calendar/MonthCalendar.kt`、`domain/CalendarMath.kt` |
| 纪念日与整数日 | `domain/Milestones.kt`，布线在 `CalendarScreen.kt` / `MainViewModel.kt` |
| 提醒（含胶囊到期） | `notify/Reminders.kt`、`notify/ReminderReceiver.kt`、`notify/BootReceiver.kt`、`ui/NotificationsScreen.kt` |
| 桌面小组件 | `ui/widget/` |
| 农历与每年重复 | `domain/Lunar.kt`、`domain/YearlyRepeat.kt`、`ui/LunarText.kt` |
| 外观（主题、玻璃、全局背景） | `ui/AppearanceScreen.kt`、`ui/theme/Style.kt`（GlassTokens 分层：大卡 `glassSurface` / 编辑静板 `interactive=false` / 小件 `glassPane`） |
| 烟涛（磁带墙，原名声带） | `ui/VoiceTapeScreen.kt`（墙+机座+录音棚）、`data/VoiceTape.kt`（模型+按年存储+`vt-` 录音仓）、`data/TapeImport.kt`（视频抽音轨/波形解码） |
| 照片查看器（三入口共享）与弹窗防护 | `ui/components/PhotoViewer.kt`（`ViewerMedia` 双源）、`data/PhotoExif.kt`、`ui/components/DialogWindowGuard.kt` |
| 数据管理（备份 / 加密） | `ui/DataScreen.kt`、`data/Backup.kt`、`data/BackupCrypto.kt`（.ics 已退役） |
| 抽屉 / 设置 / 标题栏 | `ui/AppDrawer.kt`、`ui/SettingsScreen.kt`、`ui/AppTitleBar.kt`、`ui/TickCountApp.kt` |
| 手绘动作图标 | `ui/components/ActionIcons.kt`（✓/×/🗑/↺/▶/‖/i…） |
| 应用内更新日志 / 项目介绍 | `ui/ChangelogScreen.kt`、`domain/Changelog.kt`、`domain/Readme.kt` |

文案：中 `values-zh/strings.xml`，英 `values/strings.xml`。测试：`app/src/test/`，与 domain/data/notify 对应。

## 数据流

- **UI 不碰存储**：Screen → `MainViewModel` 六张 StateFlow（events/wishes/capsules/footprints/tapes/settings）。改动三步：改 flow → `store.save()` → 旁路（`reschedule()` / `refreshAllWidgets()`）。
- **持久化五处**：事件+心愿+设置在 SharedPreferences；胶囊 / 足迹 / 磁带按年 JSON。**照片以相册为真身**：`g:` 引用，旧 `bg-`/`fp-` 副本双读且 `fp-` 启动自动迁移；语音信笺（`cv-`）、磁带录音（`vt-`）、背景图为私有副本。
- **备份六类全集，媒体像素不随走**；两类语音例外（`voices` / `tapeVoices`）Base64 随 JSON（恢复只写活记录、`isOurs` 校验）；背景图不随走。
- **提醒只有一个闹钟**，四处维护：持久化后、`reschedule()`、`BootReceiver`、触发后重排。
- **两文档即应用内容**：`更新日志.txt` → 日志页；README「功能 / 一键添加多个 / 倒计时的读法 / 桌面小组件」节 → 介绍页（按标题抽取，**勿改标题**）。

## 血泪清单（新功能逐条对）

1. **新数据类型六件套**：模型+store；备份导/入对称；**MERGE 按 id**；previewOf；删记录清关联文件；中英 strings。
2. **写路径三问**：闹钟重排？小组件刷？强停自愈？
3. 提醒选「下一个」**按时刻排序**，目标 id 写进 `EXTRA_ID`。
4. 编辑器媒体：拷进私有目录、**保存才写记录**；✕ 删除是缓刑（保存才行刑，取消归还）。
5. 备份来的文件名进 `File()` 前**必须 `isOurs` 校验**（前缀+无分隔符）。
6. 存储迁移三原则：旧容器存在才触发；按 id 幂等；解码失败保源。
7. 按年文件 save：只删「读得出来且已空」的年份文件。
8. 文本切分**防代理对**（emoji 2 个 code unit）。
9. 同位置复用 `rememberSaveable` **以内容为 key**。
10. 列表删除按 id；item key 前缀互异。
11. 测试盲区：单测只盖 domain 纯逻辑；store/备份/闹钟/UI 靠人审。
12. **视觉类新功能先最小可视样**确认审美再铺开。
13. **操作一律手绘图标**（ActionIcons 体系），不写文字按钮、不引 Material 图标。
14. **拉取远端先 `compileDebugKotlin`** 再动工。
15. 引用删除语义：`g:` 永不碰相册；迁移/重连「先改记录后动文件」；编辑器保存重放 `convertedCopies`。
16. 后台迁移依赖相册权限——挂 `onGalleryPermissionGranted`，别假设启动即有。
17. `Modifier.transformable` 认领单指 pan（灭 Pager 翻页）——手写仲裁（见 ZoomablePhoto）。
18. **系统缩略图 ≠ 原图**——全屏必须整流解码（`GalleryMedia.bitmap`），缩略图只给条/格。
19. Compose Dialog 窗口默认有底有压暗——要透出 Activity 需清 `windowBackground`+`setDimAmount(0f)`。
20. 页面分支互换会丢列表滚动位——`SaveableStateHolder` 每页一钥；**编辑/详情页不加**（表单按位置恢复会跨记录串台）。

## 发版流程（极简三步，本地零验证）

1. **bump**：`年.月.日.当天序号`；versionCode = 年月日紧缩+两位序号（2026.10.26.1 → 26102601）。
2. **拼日志**：顶部加同号小节（✨/🎨/🔄/🔧 条目 + `📦 安装：下载 TickCount-<版本>.apk 直接安装`）。**blob 通道**：从 HEAD blob 拼 → `%TEMP%` 断言（行 4 = 新版本号、标题唯一、零 U+FFFD、LF）→ `git hash-object -w` + `update-index --cacheinfo`，**不经工作区**。最新节 > 5 行且版本号 == versionName（CI 强制）。
3. **提交（只含版本号+日志+本发功能文件，不加塞）→ tag `v<版本>` → 推 main+tag**。

CI 唯一闸门：tag 触发单测 → assembleRelease → Release 挂 `TickCount-<版本>.apk`。

## 大坑

- **`更新日志.txt` 严禁 WPS/Word**（变 OLE 二进制，ChangelogTest 必挂）。被翻写时：干净文本放 `%TEMP%` → `hash-object -w` → `cacheinfo` 入索引 → `git rev-parse HEAD:更新日志.txt` 校验。
- **透明加密驱动翻写 `.txt`/`.csv`**（写入几秒内变密文；`.md` 免疫）。仓库数据文件一律 `.md`；`.txt` 只剩更新日志一个；**以索引 blob 为准，别信工作区**。
- **PowerShell+GBK 四坑**：① 管道按 GBK 解码，字节级走 `cmd /c "… > 文件"`；② `.ps1` 中文无 BOM 即烂，用码点拼或独立 UTF-8 文件；③ `ErrorActionPreference='Stop'` 遇 adb stderr 即断（`$home` 保留不可赋值）；④ **禁 `Get-Content | Set-Content` 改中文源码**（`—` 变 `鈥?`、引号被吞直接语法错误）——源码用编辑器工具改。
- **日志拼接以 blob 实际行尾为准**（库存 LF / 检出 CRLF）；拼后必做结构断言。
- **模拟器走查**：每步 `uiautomator dump` 再点，盲坐标必漂；`pm clear` 后文案双语兜底；`content delete` 不带 id 会清空整个集合；**硬崩回滚 userdata**（QEMU 缓存未落盘），涉及数据突变的彩排改完先重启确认。
- README 四个二级标题是介绍页抽取锚点，**勿改名**。
- CI 签名密钥在仓库 secrets，fork/换仓要重配。
