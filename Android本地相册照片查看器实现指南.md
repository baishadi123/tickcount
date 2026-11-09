# Android 本地相册 · 照片详情查看器实现指南

> 图源：系统相册（MediaStore） · 支持多图左右滑动 · Zoom / 下拉关闭 / EXIF 信息面板
>
> 配套代码：`PhotoViewer.kt` · Compose 实现 · minSdk 24+

---

## 目录

1. [第一步：图源与权限，这一步定架构](#1-第一步图源与权限这一步定架构)
2. [读取相册：MediaStore + 分页](#2-读取相册mediastore--分页)
3. [数据模型](#3-数据模型)
4. [查看器骨架](#4-查看器骨架)
5. [手势：四个必须处理的冲突](#5-手势四个必须处理的冲突)
6. [缩放时的边界处理](#6-缩放时的边界处理)
7. [下拉关闭](#7-下拉关闭)
8. [缩略图条：双向同步](#8-缩略图条双向同步)
9. [EXIF 与玻璃信息面板](#9-exif-与玻璃信息面板)
10. [性能清单](#10-性能清单)
11. [排查表](#11-排查表)
12. [依赖与目录](#12-依赖与目录)

---

## 1. 第一步：图源与权限，这一步定架构

"看整个相册"和"看用户选的几张"，是完全不同的两条路。先选，否则后面全要改。

| 方案 | 权限 | 能拿到什么 | 适用 |
|---|---|---|---|
| **Photo Picker** | **不需要任何权限** | 用户主动选择的 N 张 | 分享、上传、单次查看 |
| **MediaStore + 全量权限** | `READ_MEDIA_IMAGES`（13+）/ `READ_EXTERNAL_STORAGE`（12-） | 整个相册，可排序、可分组、可监听变化 | **相册浏览器** |
| 部分照片访问 | `READ_MEDIA_VISUAL_USER_SELECTED`（14+） | 用户勾选的那部分 | 折中方案 |

你要做的是"多图浏览"，需要能左右滑动翻遍相册，所以**必须走 MediaStore + 权限**。

### 1.1 权限声明

```xml
<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<!-- Android 14+ 部分访问 -->
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />

<!-- Android 12 及以下，用 maxSdkVersion 限定 -->
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- Android 14 起需要，用来点亮"选择更多照片" -->
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
```

### 1.2 运行时请求

```kotlin
private val permLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { result ->
    val granted = result.values.any { it }
    // ★ 关键：14+ 上"部分访问"也算 granted，但结果集是不完整的
    state = if (granted) State.Content else State.Denied
}

fun request() {
    val perms = buildList {
        if (Build.VERSION.SDK_INT >= 33) add(MediaStore.Images.Media.READ_MEDIA_IMAGES_PERMISSION)
        if (Build.VERSION.SDK_INT >= 34) add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        if (Build.VERSION.SDK_INT < 33) add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    permLauncher.launch(perms.toTypedArray())
}
```

`READ_MEDIA_IMAGES` 在 API 33 及以上用 `MediaStore.Images.Media.READ_MEDIA_IMAGES_PERMISSION`（就是那个字符串常量），别硬写字符串。

### 1.3 三个必须处理的边界

1. **Android 14+ 的部分访问**：用户可能只给了几张。这时查询结果只有那几张，界面上要提示"仅显示已授权的照片"，并给一个"选择更多"的入口（`ACTION_PICK_IMAGES` 或重新请求）。
2. **权限被永久拒绝**：`shouldShowRequestPermissionRationale` 返回 false 且没授权 → 只能引导去设置页。
3. **相册在外部变化**：用户在其他 App 删了照片。用 `ContentObserver` 或 `registerContentObserver` 监听，否则列表里会出现"点开是空白"的项。

---

## 2. 读取相册：MediaStore + 分页

### 2.1 查询

```kotlin
data class MediaBucket(val id: Long, val name: String, val cover: Uri)

private fun queryImages(limit: Int, offset: Int): List<PhotoDetail> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    // API 30+ 支持通过 Bundle 传 limit/offset，比 SQL 拼接安全
    val queryArgs = Bundle().apply {
        if (Build.VERSION.SDK_INT >= 30) {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Images.Media.DATE_TAKEN} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
    }

    return buildList {
        contentResolver.query(
            collection,
            projection,
            if (Build.VERSION.SDK_INT >= 30) queryArgs else null,
            null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                add(
                    PhotoDetail(
                        uri = ContentUris.withAppendedId(collection, id),
                        title = c.getString(nameCol) ?: "未命名",
                        takenAt = formatDate(c.getLong(dateCol)),
                        resolution = "${c.getInt(wCol)} × ${c.getInt(hCol)}",
                        fileSize = formatSize(c.getLong(sizeCol)),
                        bucket = c.getString(bucketCol),
                    )
                )
            }
        }
    }
}
```

### 2.2 为什么要分页

一万张照片一次性查出来，每项都要建 Uri 对象，内存和耗时都顶不住。两种做法：

| 做法 | 说明 | 适用 |
|---|---|---|
| **Paging 3** | 官方方案，`PagingSource` + `LazyColumn` / `HorizontalPager` | 推荐 |
| 手写 limit/offset | 简单，但要自己管加载状态 | 图库不大（< 5000） |

**绝对不要**把整个列表 `List<Uri>` 传给 `HorizontalPager` 再让它自己管——Pager 会预加载相邻页，等于把整个列表的图片都解码一遍。

### 2.3 分组（可选但值得做）

按 `BUCKET_DISPLAY_NAME` 分组，就是"相机 / 微信 / 截图 / 下载"这种文件夹视图。用户找图九成是按文件夹找的，比按时间轴找快得多。

---

## 3. 数据模型

```kotlin
data class PhotoDetail(
    val uri: Uri,              // 唯一键，也是加载入口
    val title: String,         // DISPLAY_NAME
    val takenAt: String,       // 已格式化，避免每次重组都 format
    val bucket: String? = null,// 相册分组
    val place: String? = null, // EXIF GPS，懒加载
    val camera: String? = null,
    val resolution: String? = null,
    val fileSize: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val shutter: String? = null,
)
```

**注意 `takenAt` 存字符串不存 Long。** 列表里几百个 item，每帧都 `SimpleDateFormat.format()` 会掉帧。在查询阶段格式化一次即可。

GPS、相机、ISO 这些需要读 EXIF 的字段，**不要在列表阶段读**——读 EXIF 要开流，几百张会让首屏卡死。做法是详情页打开时再按需读。

---

## 4. 查看器骨架

```
PhotoViewerScreen
├── HorizontalPager            ← 左右翻页，承载 ZoomablePhoto
│   └── ZoomablePhoto           ← 单指拖动 / 双指缩放 / 双击 / 下拉
├── TopBar                      ← 序号、返回，点击时随 UI 一起隐藏
├── GlassInfoPanel              ← 底部信息面板
└── ThumbnailStrip              ← 底部缩略图条（可选）
```

```kotlin
@Composable
fun PhotoViewerScreen(
    photos: List<PhotoDetail>,
    initialIndex: Int = 0,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var uiVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,     // ★ 核心：放大后必须禁用翻页
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomablePhoto(
                uri = photos[page].uri,
                onTap = { uiVisible = !uiVisible },
                onZoomChanged = { zoomed = it },
                onDismiss = onClose,
            )
        }
        // 顶栏 / 面板 / 缩略图条
    }
}
```

`userScrollEnabled = !zoomed` 这一行是整个组件能不能用的分水岭。去掉它，放大之后你会完全拖不动图——因为每次横向拖拽都被 Pager 当成翻页吃掉了。

---

## 5. 手势：四个必须处理的冲突

### 5.1 缩放和翻页抢事件

见上，`userScrollEnabled = !zoomed`。`zoomed` 由子页回传：

```kotlin
LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }
```

用 `1.01f` 而不是 `1f`，是因为双指缩放松手后 `scale` 常常停在 `1.0001` 这种值上，用 `== 1f` 判断会永远进不了"未放大"状态。

### 5.2 单指平移 vs 下拉关闭

两者都是单指拖拽，靠**方向**区分，靠**缩放状态**隔离：

- `scale > 1` → 单指拖动 = 平移查看细节
- `scale == 1` → 单指上下拖动 = 下拉关闭

```kotlin
val transformState = rememberTransformableState { zoomChange, panChange, _ ->
    val next = (scale * zoomChange).coerceIn(1f, 5f)
    scale = next
    // 只有放大时才允许平移
    offset = if (next > 1f) offset + panChange else Offset.Zero
}
```

`pointerInput(scale)` 里注册下拉手势时也必须加同样的条件，否则两个手势会在同一帧同时响应：

```kotlin
.pointerInput(scale) {
    if (scale <= 1.01f) {
        detectVerticalDragGestures(
            onDragEnd = { if (abs(dragY) > 220f) onDismiss(); dragY = 0f },
            onDragCancel = { dragY = 0f },
        ) { _, dy -> dragY += dy }
    }
}
```

### 5.3 双击缩放：别用 Animatable

```kotlin
detectTapGestures(
    onTap = { onTap() },
    onDoubleTap = {
        scope.launch {
            animate(scale, if (scale > 1.01f) 1f else 2.5f, tween(260)) { v, _ -> scale = v }
            if (scale <= 1.01f) offset = Offset.Zero
        }
    },
)
```

`animate()` 是挂起函数，直接逐帧写普通 state。如果用 `Animatable`，手势实时写值和动画写值会抢同一个对象——双击之后手指一碰，画面就跳。

### 5.4 单击隐藏 UI vs 双击缩放

`detectTapGestures` 同时给 `onTap` 和 `onDoubleTap` 时，**系统会自动延迟 onTap 约 300ms** 来等待第二次点击。这会导致单击隐藏 UI 有半秒延迟。

如果你无法接受这个延迟，只能自己实现点击计时器：

```kotlin
var lastTap by remember { mutableLongStateOf(0L) }
detectTapGestures { pos ->
    val now = System.currentTimeMillis()
    if (now - lastTap < 300) { /* 双击 */ } else { /* 单击，立即执行 */ }
    lastTap = now
}
```

代价是双击的第一下会先触发一次单击。**建议先用系统默认的延迟版本**，300ms 在查看器里感知不强，而自己实现容易出 bug。

---

## 6. 缩放时的边界处理

现在图片可以无限拖出屏幕。必须把 `offset` 限制在图片的实际可视范围内。

```kotlin
val baseSize = // 图片 FIT 后的实际显示尺寸
val maxX = (baseSize.width * (scale - 1f)) / 2f
val maxY = (baseSize.height * (scale - 1f)) / 2f

offset = Offset(
    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
    y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
)
```

`baseSize` 怎么拿：用 `Modifier.onSizeChanged` 记住容器尺寸，再根据图片宽高比算出 `ContentScale.Fit` 下的实际显示尺寸。

```kotlin
var container by remember { mutableStateOf(IntSize.Zero) }
// ...
Modifier.onSizeChanged { container = it }
```

> **别用容器尺寸直接算。** 横图在竖屏容器里，FIT 后左右有黑边，用容器宽度算 `maxX` 会让图能拖出黑边。必须用"图片实际显示尺寸"。

### 关于"边缘接力翻页"

Google Photos 的行为是：放大状态下拖到边缘继续拖，会先回弹，再继续拖则翻到下一张。

这个**不建议在第一版做**。它要求完全接管指针事件（不能用 `transformable`），自己实现双指缩放 + 单指平移 + 边缘检测 + 手势接力，代码量翻三倍，还容易出各种边界 bug。

第一版的合理行为是：

- `scale == 1` → Pager 接管横向翻页（天然就有）
- `scale > 1` → 只能平移，拖到边缘就停住
- 想翻页 → 双击缩回 1 倍，或者点缩略图条

这个体验已经够用了。

---

## 7. 下拉关闭

```kotlin
Box(
    Modifier.graphicsLayer {
        translationY = dragY
        alpha = (1f - abs(dragY) / 1400f).coerceIn(0.25f, 1f)
    }
)
```

三个细节：

1. **拖拽时背景不要真的变透明**，让图片自身 alpha 降低就够了。真把窗口做成透明需要 `FLAG_...`，还会闪黑。
2. **阈值 220dp 左右**，松手超过就关闭，否则回弹。别设太小，误触率很高。
3. **速度也要参与判断**。快速轻扫（距离短但速度快）应该也关闭，用 `VelocityTracker` 拿 `velocityY`，`> 1000` 直接退出。

回弹时同样用 `animate(dragY, 0f, spring(dampingRatio = 0.7f))`，别用 tween——回弹要有物理感。

---

## 8. 缩略图条：双向同步

底部一排小图，点哪跳哪，翻页时自动滚动到对应位置。

```kotlin
@Composable
fun ThumbnailStrip(
    photos: List<PhotoDetail>,
    pagerState: PagerState,
    onPick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // 大图 → 缩略图：翻页时让选中的缩略图居中
    LaunchedEffect(pagerState.currentPage) {
        listState.animateScrollToItem(
            index = (pagerState.currentPage - 2).coerceAtLeast(0)
        )
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(photos) { index, photo ->
            val selected = index == pagerState.currentPage
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.uri)
                    .size(120)          // ★ 缩略图一定要指定尺寸，否则按原图解码
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (selected) Modifier.background(
                            Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(8.dp)
                        ) else Modifier
                    )
                    .clickable { onPick(index) },
            )
        }
    }
}
```

两个坑：

- **`size(120)` 必须写。** 不写的话 Coil 会按原图尺寸解码 50 张缩略图，直接 OOM。
- **`animateScrollToItem` 要减 2**，让当前项大致居中，而不是贴在最左边。

---

## 9. EXIF 与玻璃信息面板

### 9.1 EXIF 按需读取

```kotlin
suspend fun loadExif(context: Context, uri: Uri): PhotoDetail = withContext(Dispatchers.IO) {
    val exif = runCatching {
        ExifInterface(context.contentResolver.openInputStream(uri)!!)
    }.getOrNull() ?: return@withContext PhotoDetail(uri, "", "")

    val dateMillis = exif.getAttributeLong(ExifInterface.TAG_DATETIME_ORIGINAL)
    // ... 组装字段
}
```

三条原则：

1. **只在详情页打开时读**，不要在列表阶段批量读；
2. **必须在 IO 线程**，`openInputStream` 是阻塞的；
3. **`runCatching` 兜底**，损坏的 JPEG 会抛异常。

`ExifInterface(InputStream)` 这个构造器在旧版本上有坑（不支持所有格式），AndroidX 的 `androidx.exifinterface` 更稳。

### 9.2 玻璃面板

信息面板贴在底部，用之前那套玻璃参数。因为是"半屏浮层"，只需要其中三层：

```kotlin
Column(
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
        .drawBehind {
            // ① 厚度渐变
            drawRect(Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = 0.16f),
                    0.52f to Color.White.copy(alpha = 0.06f),
                    1.0f to Color.White.copy(alpha = 0.12f),
                ),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ))
            // ② 顶部高光
            drawRect(Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.22f),
                0.35f to Color.Transparent,
            ))
            // ③ 只画上边缘的亮线
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset.Zero,
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
            )
        }
        .navigationBarsPadding()
        .padding(horizontal = 22.dp, vertical = 20.dp),
)
```

**注意：面板贴底时不要再加底部暗角和完整描边**，否则下边缘会有一条突兀的灰线。只保留顶边走线。

---

## 10. 性能清单

| 项 | 做法 | 不做的后果 |
|---|---|---|
| 大图解码 | Coil 按容器尺寸采样 | 一张 4000×3000 约 48MB，三张即 OOM |
| 缩略图 | `size(120)` 显式指定 | 50 张缩略图 = 50 张原图 |
| Pager 预加载 | `beyondBoundsPageCount = 1` | 默认预载过多，内存翻倍 |
| 翻页速度 | `snapPositionalThreshold` 调高 | 轻扫就翻页，浏览大图很烦 |
| EXIF | 进详情页才读 | 首屏卡 1~2 秒 |
| 日期格式化 | 查询阶段做一次 | 滚动时每帧 format，掉帧 |
| 列表数据 | Paging 3 / 分批 | 万张图直接 ANR |
| 硬件位图 | 默认开启，别关 | 关闭后每帧 CPU 上传纹理 |

```kotlin
HorizontalPager(
    state = pagerState,
    beyondBoundsPageCount = 1,   // 只预载前后各一张
    flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    ),
)
```

---

## 11. 排查表

| 现象 | 根因 | 修法 |
|---|---|---|
| 放大后拖不动 | Pager 抢了横向事件 | `userScrollEnabled = !zoomed` |
| 双击后手指一碰就跳 | 用 `Animatable` 与手势抢同一个值 | 改用 `animate()` 逐帧写 state |
| 图片能拖出屏幕 | `offset` 没 clamp | 按 `scale - 1` 计算边界 |
| 横图能拖出黑边 | 用了容器宽度算边界 | 用图片 FIT 后的实际尺寸 |
| 单击隐藏 UI 延迟 300ms | `onTap` 与 `onDoubleTap` 共存 | 接受延迟，或自实现计时器 |
| 缩略图条滑动卡顿 | 没指定 `size()` | 加 `.size(120)` |
| 点开某些照片空白 | 文件已被删除 | 注册 `ContentObserver` |
| 14+ 只显示几张 | 只拿到"部分访问"权限 | 提示 + 提供"选择更多" |
| 信息面板下边缘有灰线 | 贴着底部仍画了完整描边 | 只画上边缘 |
| 快速翻页白屏 | 没加载占位 | 加 `placeholder` / shimmer |
| 退出后内存不释放 | Pager 持有大图引用 | 页面销毁时 `ImageLoader` 清缓存 |

---

## 12. 依赖与目录

```kotlin
dependencies {
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.paging:paging-compose:3.3.0")   // 可选
    // compose-foundation 1.6+ 才有 HorizontalPager 的 userScrollEnabled
}
```

```
gallery/
├── data/
│   ├── PhotoDetail.kt          # 数据模型
│   ├── MediaStoreLoader.kt     # 查询 + 分页 + 权限
│   └── ExifLoader.kt           # 按需读 EXIF
├── ui/
│   ├── PhotoViewerScreen.kt    # Pager 容器 + UI 显隐
│   ├── ZoomablePhoto.kt        # 手势核心
│   ├── ThumbnailStrip.kt       # 缩略图条
│   ├── GlassInfoPanel.kt       # 玻璃信息面板
│   └── TopBar.kt
└── GalleryActivity.kt
```

---

## 附录：第一版该做与不该做

**必做（第一天就要有）**

- MediaStore 查询 + 权限（含 14+ 部分访问兜底）
- Pager + 缩放 + 双击
- 点击隐藏 UI
- 大图采样

**第二版**

- 下拉关闭
- 缩略图条
- EXIF 信息面板

**第三版**

- 边界回弹
- 视频支持
- 编辑 / 删除 / 分享

**别做**

- 边缘接力翻页（成本极高，收益有限）
- 相册时间轴的照片瀑布流（那是另一个功能，不是"查看器"）
- 自定义相机

---

*配套代码：`PhotoViewer.kt`（手势 + 查看器 + 信息面板）*