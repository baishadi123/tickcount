# TickCount

[![Build](https://github.com/ZZPBY/tickcount/actions/workflows/android.yml/badge.svg)](https://github.com/ZZPBY/tickcount/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个极简、纯离线的安卓倒数日日历。在日历上点一天、起个名字，就能看到离那一天还有多久，
或者已经过去了多久。

[English](README.en.md)

**约 2 MB · 纯离线 · 无广告 · 无账号 · 开源**

<p align="center">
  <img src="docs/screenshot-home.png" width="200" alt="主页：每条倒数日还剩多少天，已过去的收在下面" />
  <img src="docs/screenshot-calendar.png" width="200" alt="日历与那天的倒数日" />
  <img src="docs/screenshot-widgets.png" width="240" alt="两种桌面小组件" />
</p>

## 功能

- **六个页面** —— 抽屉里：留莹（倒数日）、作陪（心愿）、敬心（时光胶囊）、万疆（足迹）、
  日历、行舟（星图）；提醒、外观、小组件、数据、日志、介绍收进左下角齿轮的「设置」，
  「语言」在最下一行。
- **留莹** —— 一条一张卡片：左边超大天数，右边名称与日期。已过去的收在「已过去」下，
  今明有日子时列表顶上一行提醒。置顶恒在最前；按日期或添加时间排序，选择会记住。
- **作陪** —— 想一起做的事，做完点勾收进「已完成」，进编辑页可撤销。
- **敬心** —— 写下想说的话、选照片，封存到某一天，到期才能拆信；信笺自动分页，
  到期当天到点提醒。
- **万疆** —— 到过的地方：地名、日期、照片 / 动图 / 视频，可带上此刻的位置；按地方分组，
  点开全屏翻看。右上角可切到**地图**：全部足迹化作地图上的钉，点开即见此地每次到访；
  按年份点亮走过的路线，瓦片看过即离线可用，也可在图上直接选点补位置。
- **行舟** —— 全部日子排成夜空星座，彗星沿星座航行，抵达已到来的日子便点亮它；
  甩动带惯性、拖动拨彗星、点按暂停。
- **纪念日与整数日** —— 日历任意一天可设为纪念日，从此每年都在；在一起第
  100 / 365 / 520 / 999 / 1314 天自动标出。
- **日历** —— 手写月历：倒数日最多三个彩点，胶囊、心愿、纪念日、整数日各有记号；
  点开某天，当天的全部事件列在下方。
- **编辑** —— 精确到分钟；标签不限个数、色相条调色；长按卡片直接置顶 / 编辑 / 删除；
  支持一键添加多个。
- **详情** —— 年月日时分秒完整拆分，不满一天改为大字时分秒倒数；可关联最多 16 张相册照片
  与详情页背景图。
- **搜索与筛选** —— 按名称（含标签）/ 日期 / 时间；可只看「7 天内」或按标签，
  标题下说明今年还有几个。
- **快速翻月** —— 点月份标题选年月，±1 / ±10 年翻页，一步回本月。
- **桌面小组件** —— 「单个」时分秒实时跳动，「清单」列出接下来的几条；面板浅色 / 深色 /
  跟随系统，清单字号可调，都不需要权限。
- **提醒** —— 提醒时间与全局、单条提前量，胶囊到期同样提醒；非精确闹钟，重启自动恢复。
- **农历** —— 1900–2100 按农历选日子，卡片与详情注明对应农历；每年重复可选公历或农历。
- **外观与双语** —— 六套主题 + 自定义色相；中英跟随系统或手动选；跟随系统字体缩放长高。
- **备份与恢复** —— 全部数据（照片与视频像素除外）导出 / 导入，可选 AES-256-GCM 加密，
  覆盖或合并；另有 .ics 导出给系统日历。
- **除地图瓦片外不联网、无统计。** 单模块，运行库仅 AndroidX、Compose 与 osmdroid（地图），
  `minSdk` 27；权限只在用到时才申请（提醒：通知 + 开机自启；万疆定位：精确位置；
  地图瓦片经网络获取，其余一切离线可用）。

## 一键添加多个

新建时打开「一键添加多个」，填**间隔天数**（1～3650）与**个数**（1～100）。上面选的日期
本身也添加一条，之后每条多间隔那么多天——10 月 14 日新建、间隔 2、个数 3，会得到 14、16、
18、20 日共 4 条。时间整串跟随；同一批在「按添加时间」排序时挨在一起，之后各自编辑或删除，
互不影响。

## 倒计时的读法

目标是**所选日期当地时间的 00:00**；指定了时刻就按那个时刻算。时刻一过，同一套算法反向
运行，标签从「还有」变成「已过去」。从最高位起，还没开始计数的字段用短横线占位：

| 剩余时间 | 显示 |
|---|---|
| 4 个月 11 天 6 时 30 分 15 秒 | `----年04月11日 06时30分15秒` |
| 2 天 30 秒 | `----年--月02日 00时00分30秒` |
| 3 年 4 个月 11 天 6 时 30 分 15 秒 | `0003年04月11日 06时30分15秒` |

年、月、天按日历算，时、分、秒按流逝时间算，夏令时切换的那天仍算一整天。

## 桌面小组件

长按桌面空白处 → 添加小组件，两个可选：

**倒数日 · 单个** —— 挑一条倒数日放上桌面。默认 4×2，拖大拖小自动换排版，天数会拆成
「1年1个月4天」；填了时间的按该时刻算，和详情页一致。点它直接打开这条倒数日，
秒针由系统自己走。

**倒数日 · 清单** —— 自己列出接下来的几条，从近到远，点哪行开哪条；默认放得下三行，
拖高最多四行，没有要数的日子时显示提示。

面板（浅色 / 深色 / 跟随系统）与清单字号在侧边栏「桌面小组件」里选。

## 下载

APK 发布在 [Releases](../../releases/latest)，也可以从 **Actions** 标签页里
任意一次成功的构建中下载产物。每个版本改了什么见 [`更新日志.txt`](更新日志.txt)。

## 编译

需要 JDK 17、Android SDK Platform 37 与 Build Tools 36.0.0，并在 `local.properties`
里写好 SDK 路径（或导出 `ANDROID_HOME`）：

```bash
./gradlew testDebugUnitTest   # 单元测试
./gradlew assembleRelease     # APK -> app/build/outputs/apk/release/
```

Windows 下使用 `gradlew.bat`。

## 技术栈

| | |
|---|---|
| 语言 | Kotlin 2.4.20 |
| UI | Jetpack Compose + Material 3（Compose BOM 2026.09.00） |
| 构建 | AGP 9.4.1、Gradle 9.6.0，版本集中在 `gradle/libs.versions.toml` |
| SDK | compileSdk 37、targetSdk 36、minSdk 27 |
| 存储 | 事件与心愿在 `SharedPreferences` 的小 JSON 文档；胶囊与足迹各按年份存于应用私有目录 |
| 依赖 | 运行时为 AndroidX、Compose 与 osmdroid（万疆地图，图块默认取自高德的栅格服务——无官方授权，个人使用无虞） |

`java.time`（API 26）与 `windowLightNavigationBar`（API 27）都够早，所以 `minSdk` 27
既不需要 desugaring，也不需要任何兼容垫片。

## 目录结构

```
app/src/main/java/io/github/zzpby/tickcount/
├── MainActivity.kt              边到边宿主 + 应用主题
├── data/                        模型与持久化
│   ├── CountdownEvent.kt        倒数日模型；EventCodec/EventStore 负责读写
│   ├── EventBackground.kt       详情页背景与相册照片的私有文件
│   ├── Wish.kt                  心愿
│   ├── TimeCapsule.kt           时光胶囊，按年分文件
│   ├── Footprint.kt             万疆足迹；FootprintMedia 管理图 / 动图 / 视频
│   ├── SettingsStore.kt         主题、排序、语言与小组件外观
│   └── Backup.kt                备份格式；BackupCrypto 负责密码加密
├── domain/                      纯逻辑，有单元测试
│   ├── Countdown.kt             年/月/天/时/分/秒 拆解与占位格式化
│   ├── BatchAdd.kt              一键添加多个
│   ├── CalendarMath.kt          月历网格
│   ├── CountdownList.kt         排序、分段与天数距离
│   ├── Search.kt                搜索范围与匹配
│   ├── Changelog.kt             更新日志解析；Readme.kt 抽取项目介绍
│   ├── Ics.kt                   .ics 导出
│   ├── Lunar.kt                 农历互转；YearlyRepeat.kt 每年重复
│   ├── VoyageLayout.kt          星图布点与航行路径；Milestones.kt 整数日
│   └── WidgetCountdown.kt       小组件的天数拆分与清单挑选
└── ui/
    ├── MainViewModel.kt         状态 + 全进程唯一的时钟
    ├── TickCountApp.kt          根画面：抽屉、屏幕切换、返回键
    ├── HomeScreen.kt            留莹；ThingsScreen 作陪；CapsuleScreen 敬心
    ├── WanJiangScreen.kt        万疆；VoyageScreen 行舟星图
    ├── CalendarScreen.kt        月历 + 当天全部事件
    ├── EventCard.kt             倒数日卡片；EventDetailScreen / EventEditorScreen 详情与编辑
    ├── AppearanceScreen.kt      外观；WidgetSettingsScreen 小组件；NotificationsScreen 提醒
    ├── DataScreen.kt            数据管理；ChangelogScreen 日志；ProjectIntroScreen 介绍
    ├── calendar/                手写月历 + 年月选择窗
    ├── components/              选择胶囊、Canvas 图标、色相条、Markdown 渲染等
    ├── notify/                  提醒：非精确闹钟排期、接收器、开机恢复
    ├── widget/                  两个桌面小组件
    └── theme/                   配色、字体与共享 token
```

倒计时、排序、搜索、小组件、日志解析、备份加解密、README 抽取、星图布点、整数日与提醒
排期等由 `app/src/test/` 下的单元测试覆盖，含闰日、跨年、夏令时切换等边界。

## 应用图标

矢量 XML 画不了汉字，启动器文字由
[`.tools/图标生成/IconGen.java`](.tools/图标生成/IconGen.java) 栅格化进自适应图标的
66dp 安全圆：

```bash
java .tools/图标生成/IconGen.java app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png
```

## 隐私

全部数据只存应用私有目录：倒数日与心愿在 `SharedPreferences`，胶囊与足迹按年份存文件。
声明的权限都按需申请——提醒的通知与开机自启（关掉即撤销）、万疆的精确位置
（点「带上此刻的位置」时才要，取一次当时的定位，坐标四舍五入到千分位，
只写进正在编辑的足迹）。地图模式是唯一联网的地方：向高德的图块服务请求瓦片并缓存在
应用私有缓存目录，除此之外应用没有任何联网行为，也没有任何上传；
备份导到哪、加不加密由你定，应用没有任何办法把它送出去。数据仅当 Android 自身云备份开启时
才会离开设备，由 `res/xml/backup_rules.xml` 决定。

## 许可

[MIT](LICENSE)。
