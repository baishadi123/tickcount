# Android 玻璃拟态进阶指南：从「灰块」到「真玻璃」

> 七层质感拆解 · Compose 完整实现 · 参数调优表
>
> v2.0 · 2026-11 · 配套代码：`PremiumGlass.kt`、`premium-glass.html`

---

## 目录

1. [问题定位：为什么你的玻璃看着廉价](#1-问题定位为什么你的玻璃看着廉价)
2. [七层结构总览](#2-七层结构总览)
3. [逐层拆解与参数](#3-逐层拆解与参数)
4. [Compose 完整实现](#4-compose-完整实现)
5. [CSS 对照实现（H5 / WebView）](#5-css-对照实现h5--webview)
6. [交互：让玻璃"活"起来](#6-交互让玻璃活起来)
7. [叠加真模糊（Haze）](#7-叠加真模糊haze)
8. [浅色主题：白底上怎么让玻璃显形](#8-浅色主题白底上怎么让玻璃显形)
9. [参数调优表](#9-参数调优表)
10. [性能与降级](#10-性能与降级)
11. [排查清单](#11-排查清单)

---

## 1. 问题定位：为什么你的玻璃看着廉价

绝大多数"玻璃效果做不好"的根因，不是模糊值不够大，而是**信息量太少**。

典型的新手实现只有两层：

```kotlin
// ❌ 这就是一块半透明灰砖
Modifier
    .background(Color.White.copy(alpha = 0.14f))
    .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
```

对比真玻璃，它缺了 6 个维度：

| 维度 | 灰块版 | 真玻璃版 | 视觉影响 |
|---|---|---|---|
| 底色 | 单一 alpha | 对角三段渐变（0.175/0.045/0.105） | 厚度感 |
| 高光 | 无 | 大柔光 + 小锐光，跟随指针 | **"对光"，最关键** |
| 描边 | 四周均匀 | 145° 亮→暗→回亮 | 双面反射 |
| 底部 | 与顶部同色 | 黑色 17% 暗角 | 立得住 |
| 纹理 | 无 | 128px 灰噪点，alpha 8% | 去塑料感 |
| 投影 | 纯黑 | 带紫调的传播阴影 | 浮在氛围光里 |

一句话总结：**「透明」不等于「玻璃」。玻璃之所以是玻璃，是因为它改变了穿过它的光。**

---

## 2. 七层结构总览

按绘制顺序（从下到上）：

```
┌─────────────────────────────────────┐
│  ⑧ 带色外阴影（shadow modifier）      │  ← 在 clip 之前
├─────────────────────────────────────┤
│  ⑥ 颗粒噪点        alpha 8%          │
│  ⑤ 底部暗角        black 17%         │
│  底部反射光        #96A0FF 10%       │
│  ④ 小锐光          r = 16% w         │
│  ②③ 大柔光        r = 88% w         │
│  ① 厚度渐变        对角三段          │
├─────────────────────────────────────┤
│  ⑦ 方向性描边      145° 四段         │  ← 最后画，保持锐利
│  顶缘 1px 高光                       │
└─────────────────────────────────────┘
         ↓ 内容（Column children）
```

绘制顺序有两个硬约束：

- **描边必须最后画**，否则会被上面的柔光冲淡，边缘发糊；
- **噪点必须在描边之前**，否则噪点会盖在亮边上，边缘出现毛刺。

---

## 3. 逐层拆解与参数

### ① 厚度渐变

```kotlin
drawRect(Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to tint.copy(alpha = 0.175f),
        0.52f to tint.copy(alpha = 0.045f),
        1.00f to tint.copy(alpha = 0.105f),
    ),
    start = Offset.Zero,
    end = Offset(w, h),
))
```

对角（135°）而不是垂直。中间 0.045 的"谷底"是故意的——玻璃中段最薄，光穿过最多。如果三段都取 0.15，看起来就是一张均匀半透明膜。

### ②③ 彩色渗透 + 大柔光

```kotlin
drawRect(Brush.radialGradient(
    colors = listOf(tint.copy(alpha = 0.30f), Color.Transparent),
    center = Offset(w * spec.x, h * spec.y),   // 跟随指针
    radius = w * 0.88f,
))
```

半径取卡宽的 88%，几乎覆盖整卡，衰减到 0。这一层同时干两件事：模拟背景色被玻璃吸收，以及大范围的环境光反射。

### ④ 小锐光

```kotlin
drawCircle(
    brush = Brush.radialGradient(
        colors = listOf(tint.copy(alpha = 0.34f), Color.Transparent),
        center = Offset(w * spec.x, h * spec.y),
        radius = w * 0.16f,
    ),
    radius = w * 0.16f,
    center = Offset(w * spec.x, h * spec.y),
)
```

半径只有 16%，是点光源在玻璃表面的镜面反射点。**这一层的存在感最强**：没有它，玻璃是哑光的；有它，玻璃是抛光的。

### 底部反射光

```kotlin
drawRect(Brush.verticalGradient(
    0.72f to Color.Transparent,
    1.00f to Color(0xFF96A0FF).copy(alpha = 0.10f),
))
```

玻璃下缘会被底面照亮，形成一道冷色弱光。颜色不要用白，用背景的主色调（这里取蓝紫），更像真实的光线反弹。

### ⑤ 底部暗角

```kotlin
drawRect(Brush.verticalGradient(
    0.58f to Color.Transparent,
    1.00f to Color.Black.copy(alpha = 0.17f),
))
```

从 58% 处开始压暗。低于 17% 压不住，高于 22% 会显脏。

### ⑥ 颗粒噪点

```kotlin
val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
val pixels = IntArray(128 * 128) {
    val v = rnd.nextInt(190, 256)
    (0x10 shl 24) or (v shl 16) or (v shl 8) or v   // 0x10 = alpha 16/255 ≈ 6%
}
```

三个要点：

1. **RGB 三通道同值**，否则会变成彩色雪花；
2. **alpha 控制在 0x08 ~ 0x14**（3% ~ 8%），肉眼几乎不可见，但能消除 banding；
3. 用 `ImageShader(TileMode.Repeated)` 平铺，不需要 png 资源，也不吃内存。

> 这一层是"廉价感"和"高级感"之间最便宜的那道坎。加了它，同样的渐变看起来会贵一档。

### ⑦ 方向性描边

```kotlin
drawRoundRect(
    brush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to tint.copy(alpha = 0.82f),   // 左上：强亮
            0.34f to tint.copy(alpha = 0.10f),   // 中段：几乎消失
            0.63f to tint.copy(alpha = 0.03f),   // 右下：极暗
            1.00f to tint.copy(alpha = 0.52f),   // 边角：回亮
        ),
        start = Offset.Zero,
        end = Offset(w, h),
    ),
    topLeft = Offset(rimPx * 0.5f, rimPx * 0.5f),
    size = Size(w - rimPx, h - rimPx),
    cornerRadius = CornerRadius(cornerPx - rimPx * 0.5f),
    style = Stroke(width = rimPx),
)
```

四段色标是关键。真实玻璃有正反两个面，光在边缘经过两次折射，所以会出现"亮—暗—回亮"的节奏。只用一段渐变的描边，永远做不出这个效果。

### ⑧ 带色外阴影

```kotlin
Modifier.shadow(
    elevation = 26.dp,
    shape = shape,
    ambientColor = Color(0xFF241B4D).copy(alpha = 0.90f),
    spotColor = Color(0xFF0A0820),
)
```

`ambientColor` 和 `spotColor` 需要 **API 28+**，低版本自动降级为纯黑阴影。紫色环境光让卡片看起来浮在氛围里，而不是悬在黑洞上。

---

## 4. Compose 完整实现

```kotlin
@Composable
fun PremiumGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    contentPadding: Dp = 22.dp,
    tint: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val cornerPx = with(density) { cornerRadius.toPx() }
    val rimPx = with(density) { 1.dp.toPx() }
    val noise = rememberNoiseBrush()

    // 镜面高光位置（归一化），默认落在左上
    var spec by remember { mutableStateOf(Offset(0.22f, -0.02f)) }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "glassPress",
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = 26.dp,
                shape = shape,
                ambientColor = Color(0xFF241B4D).copy(alpha = 0.90f),
                spotColor = Color(0xFF0A0820),
            )
            .clip(shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position ?: continue
                        if (size.width > 0 && size.height > 0) {
                            spec = Offset(
                                (pos.x / size.width).coerceIn(0f, 1f),
                                (pos.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }
            .drawBehind { drawGlassLayers(spec, tint, noise, cornerPx, rimPx) }
            .padding(contentPadding),
        content = content,
    )
}
```

### 七层绘制函数

```kotlin
private fun DrawScope.drawGlassLayers(
    spec: Offset,
    tint: Color,
    noise: Brush,
    cornerPx: Float,
    rimPx: Float,
) {
    val w = size.width
    val h = size.height

    // ① 厚度渐变
    drawRect(Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to tint.copy(alpha = 0.175f),
            0.52f to tint.copy(alpha = 0.045f),
            1.00f to tint.copy(alpha = 0.105f),
        ),
        start = Offset.Zero, end = Offset(w, h),
    ))

    // ②③ 大柔光，跟随指针
    drawRect(Brush.radialGradient(
        colors = listOf(tint.copy(alpha = 0.30f), Color.Transparent),
        center = Offset(w * spec.x, h * spec.y),
        radius = w * 0.88f,
    ))

    // ④ 小锐光
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tint.copy(alpha = 0.34f), Color.Transparent),
            center = Offset(w * spec.x, h * spec.y),
            radius = w * 0.16f,
        ),
        radius = w * 0.16f,
        center = Offset(w * spec.x, h * spec.y),
    )

    // 底部反射光
    drawRect(Brush.verticalGradient(
        0.72f to Color.Transparent,
        1.00f to Color(0xFF96A0FF).copy(alpha = 0.10f),
    ))

    // ⑤ 底部暗角
    drawRect(Brush.verticalGradient(
        0.58f to Color.Transparent,
        1.00f to Color.Black.copy(alpha = 0.17f),
    ))

    // ⑥ 颗粒
    drawRect(noise)

    // ⑦ 方向性描边
    drawRoundRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to tint.copy(alpha = 0.82f),
                0.34f to tint.copy(alpha = 0.10f),
                0.63f to tint.copy(alpha = 0.03f),
                1.00f to tint.copy(alpha = 0.52f),
            ),
            start = Offset.Zero, end = Offset(w, h),
        ),
        topLeft = Offset(rimPx * 0.5f, rimPx * 0.5f),
        size = Size(w - rimPx, h - rimPx),
        cornerRadius = CornerRadius(cornerPx - rimPx * 0.5f),
        style = Stroke(width = rimPx),
    )

    // 顶缘 1px 高光
    drawLine(
        color = tint.copy(alpha = 0.55f),
        start = Offset(cornerPx * 0.9f, rimPx * 1.6f),
        end = Offset(w - cornerPx * 0.9f, rimPx * 1.6f),
        strokeWidth = rimPx,
    )
}
```

### 噪点画刷

```kotlin
@Composable
private fun rememberNoiseBrush(): Brush {
    val image = remember {
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val rnd = Random(7)                       // 固定种子，避免每次重组纹理跳动
        val pixels = IntArray(n * n) {
            val v = rnd.nextInt(190, 256)
            (0x10 shl 24) or (v shl 16) or (v shl 8) or v
        }
        bmp.setPixels(pixels, 0, n, 0, 0, n, n)
        bmp.asImageBitmap()
    }
    return remember(image) {
        ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))
    }
}
```

---

## 5. CSS 对照实现（H5 / WebView）

同样的七层，给 Web 端一份等价写法，方便跨端统一视觉：

```css
.glass {
  --mx: 22%;
  --my: 0%;

  position: relative;
  isolation: isolate;
  border-radius: 26px;
  overflow: hidden;

  /* ① 厚度渐变 */
  background: linear-gradient(150deg,
      rgba(255,255,255,.175) 0%,
      rgba(255,255,255,.045) 52%,
      rgba(255,255,255,.105) 100%);

  /* ⑧ 带色外阴影 */
  box-shadow:
    0 2px 0 rgba(255,255,255,.06) inset,
    0 26px 52px -18px rgba(0,0,0,.78),
    0 40px 90px -40px rgba(96,72,255,.50);

  -webkit-backdrop-filter: blur(26px) saturate(190%) brightness(1.07);
  backdrop-filter: blur(26px) saturate(190%) brightness(1.07);
}

/* ②③④⑤ 高光 / 反射 / 暗角 */
.glass::before {
  content: "";
  position: absolute; inset: 0;
  background:
    radial-gradient(88% 66% at var(--mx) var(--my),
        rgba(255,255,255,.30) 0%, rgba(255,255,255,.07) 36%, transparent 62%),
    radial-gradient(130% 55% at 50% 108%, rgba(150,160,255,.20), transparent 62%),
    linear-gradient(180deg, transparent 58%, rgba(0,0,0,.17) 100%);
  mix-blend-mode: screen;
  pointer-events: none;
}

/* ⑦ 方向性描边：用 mask 做出只描边不填充 */
.glass::after {
  content: "";
  position: absolute; inset: 0;
  border-radius: inherit;
  padding: 1px;
  background: linear-gradient(145deg,
      rgba(255,255,255,.82) 0%,
      rgba(255,255,255,.10) 34%,
      rgba(255,255,255,.03) 63%,
      rgba(255,255,255,.52) 100%);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}

.glass > * { position: relative; z-index: 2; }
```

`mask-composite: exclude` 这个技巧值得记住——它让一个带 1px padding 的渐变层只在边框位置显示，是 Web 上做**方向性渐变描边**的标准做法，比 `border-image` 兼容性好得多。

颗粒层建议放在页面级：

```css
body::after {
  content: "";
  position: fixed; inset: 0;
  background-image: url("data:image/svg+xml;...feTurbulence...");
  opacity: .045;
  mix-blend-mode: overlay;
  pointer-events: none;
}
```

---

## 6. 交互：让玻璃"活"起来

### 6.1 高光跟手

```kotlin
.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val pos = event.changes.firstOrNull()?.position ?: continue
            if (size.width > 0 && size.height > 0) {
                spec = Offset(
                    (pos.x / size.width).coerceIn(0f, 1f),
                    (pos.y / size.height).coerceIn(0f, 1f),
                )
            }
        }
    }
}
```

**关键点：`spec` 在 `drawBehind` 内部被读取，所以状态变化只会让绘制阶段失效，不触发重组（recomposition）。**这是把"每帧更新位置"做得不掉帧的前提。如果写成 `Modifier.offset()` 或者在 Composable 作用域里读 `spec`，就会每帧重组，直接卡死。

Web 端等价：

```js
el.addEventListener('pointermove', e => {
  const r = el.getBoundingClientRect();
  el.style.setProperty('--mx', ((e.clientX - r.left) / r.width * 100) + '%');
  el.style.setProperty('--my', ((e.clientY - r.top) / r.height * 100) + '%');
});
```

### 6.2 按压回弹

```kotlin
val scale by animateFloatAsState(
    targetValue = if (pressed) 0.985f else 1f,
    animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
)
```

`dampingRatio = 0.42` 低于临界阻尼（1.0），会有轻微过冲后回稳——手感的来源。用 `tween` 只会得到机械的线性缩放。

scale 通过 `graphicsLayer` 施加，走的是渲染层，不改布局。

---

## 7. 叠加真模糊（Haze）

七层做出的是**质感**，Haze 补的是**穿透**。两者叠加才是完整形态：

```kotlin
implementation("dev.chrisbanes.haze:haze:1.6.5")

val hazeState = rememberHazeState()

Box(Modifier.fillMaxSize()) {
    Image(painter, null, Modifier.fillMaxSize().hazeSource(hazeState))

    PremiumGlassCard(
        modifier = Modifier.hazeEffect(hazeState) {
            blurRadius      = 24.dp
            noiseFactor     = 0.05f
            backgroundColor = Color.White.copy(alpha = 0.04f)
        }
    ) { /* 内容 */ }
}
```

### 叠加时必须调的两处

| 参数 | 单独用时 | 与 Haze 叠加时 | 原因 |
|---|---|---|---|
| 自绘噪点 alpha | `0x10`（6%） | `0x08`（3%） | 两处噪点叠加会显脏 |
| 厚度渐变 | 0.175 / 0.045 / 0.105 | 各降 0.03 | Haze 已提供底色，重复叠加会发白 |

`backgroundColor` 也要压到 0.04 左右，因为 Haze 自己会填充一层底色。

---

## 8. 浅色主题：白底上怎么让玻璃显形

### 8.1 核心矛盾：白玻璃放在白背景上，等于没有玻璃

深色模式的逻辑是「玻璃比背景亮 → 看得见」。

浅色模式这套逻辑会**完全失效**：背景已经是 #F5F5F7 了，你往上叠一层白色半透明，结果只是「白 + 白 = 白」。所以浅色玻璃的第一性问题不是「加白」，而是**建立边界**。

浅色模式必须同时用三个手段围出卡片的轮廓：

| 手段 | 作用 | 缺了会怎样 |
|---|---|---|
| 柔和外阴影 | 把卡片从背景上"托"起来 | 卡片和背景糊成一片 |
| **底边暗线** | 定义卡片的下边界（光的背面） | 上边界有高光能看见，下边界消失 |
| 顶缘高光 | 定义卡片的上边界（光的正面） | 整个卡片没有形状 |

关键认知：**深色玻璃靠"亮"区分，浅色玻璃靠"影子"区分。**

### 8.2 六个必须反转的参数

| 层 | 深色 | 浅色 | 为什么要反 |
|---|---|---|---|
| ① 厚度渐变 | 白 0.175 / 0.045 / 0.105 | 白 0.80 / 0.55 / 0.72 | 浅色要"比背景亮一档"，透明度得大幅提高 |
| ③ 大柔光 | alpha 0.30，半径 0.88w | alpha 0.55，半径 0.72w | 浅色背景漫反射强，柔光要更亮但更收敛 |
| ④ 小锐光 | alpha 0.34，半径 0.16w | alpha 0.85，半径 **0.11w** | 浅色环境下高光更小、更锐、更亮 |
| ⑤ 底部暗角 | 黑 0.17，从 58% 起 | 黑 **0.05**，从 74% 起 | 浅色下暗角稍重就像脏了 |
| ⑥ 噪点 | **白**噪，alpha 6% | **黑**噪，alpha 4% | 浅色面上白噪点完全不可见 |
| ⑧ 外阴影 | 带紫黑，26dp | 中性冷灰，18dp | 浅色用彩色阴影会显脏，用硬阴影会有"污渍感" |

另外两个细节：

- 浅色的 `ambientColor` / `spotColor` 要用 `#64748B` / `#0F172A` 这类中性冷灰，**不要用紫**；
- 浅色的 `elevation` 反而要**降低**（26 → 18dp）但**扩大扩散**，靠"大而淡"的影子，不靠"深而实"的影子。

### 8.3 改为 Token 结构（否则浅色要改几十处硬编码）

```kotlin
data class GlassTokens(
    val bodyStops: List<Pair<Float, Color>>,
    val glowAlpha: Float,  val glowRadius: Float,
    val specAlpha: Float,  val specRadius: Float,
    val bottomReflect: Color,
    val vignetteAlpha: Float, val vignetteStart: Float,
    val noiseIsDark: Boolean, val noiseAlpha: Int,
    val rimStops: List<Pair<Float, Color>>,
    val topLineAlpha: Float,
    val elevation: Dp,
    val ambient: Color,
    val spot: Color,
    val edgeLine: Color,           // 浅色专用底边暗线；深色传 Transparent
    val textPrimary: Color,
    val textSecondary: Color,
)

val DarkGlass = GlassTokens(
    bodyStops = listOf(
        0.00f to Color.White.copy(alpha = 0.175f),
        0.52f to Color.White.copy(alpha = 0.045f),
        1.00f to Color.White.copy(alpha = 0.105f),
    ),
    glowAlpha = 0.30f, glowRadius = 0.88f,
    specAlpha = 0.34f, specRadius = 0.16f,
    bottomReflect = Color(0xFF96A0FF).copy(alpha = 0.10f),
    vignetteAlpha = 0.17f, vignetteStart = 0.58f,
    noiseIsDark = false, noiseAlpha = 0x10,
    rimStops = listOf(
        0.00f to Color.White.copy(alpha = 0.82f),
        0.34f to Color.White.copy(alpha = 0.10f),
        0.63f to Color.White.copy(alpha = 0.03f),
        1.00f to Color.White.copy(alpha = 0.52f),
    ),
    topLineAlpha = 0.55f,
    elevation = 26.dp,
    ambient = Color(0xFF241B4D).copy(alpha = 0.90f),
    spot = Color(0xFF0A0820),
    edgeLine = Color.Transparent,
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.62f),
)

val LightGlass = GlassTokens(
    bodyStops = listOf(
        0.00f to Color.White.copy(alpha = 0.80f),
        0.52f to Color(0xFFEFF3FA).copy(alpha = 0.55f),   // 中段略带冷调，避免死白
        1.00f to Color.White.copy(alpha = 0.72f),
    ),
    glowAlpha = 0.55f, glowRadius = 0.72f,
    specAlpha = 0.85f, specRadius = 0.11f,
    bottomReflect = Color.White.copy(alpha = 0.30f),
    vignetteAlpha = 0.05f, vignetteStart = 0.74f,
    noiseIsDark = true, noiseAlpha = 0x0C,
    rimStops = listOf(
        0.00f to Color.White.copy(alpha = 1.00f),
        0.35f to Color.White.copy(alpha = 0.45f),
        0.65f to Color.White.copy(alpha = 0.55f),
        1.00f to Color.White.copy(alpha = 0.95f),
    ),
    topLineAlpha = 1.00f,
    elevation = 18.dp,
    ambient = Color(0xFF64748B).copy(alpha = 0.18f),
    spot = Color(0xFF0F172A).copy(alpha = 0.10f),
    edgeLine = Color(0xFF64748B).copy(alpha = 0.14f),   // ★ 浅色的关键
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF0F172A).copy(alpha = 0.60f),
)

@Composable
fun glassTokens(dark: Boolean = isSystemInDarkTheme()): GlassTokens =
    if (dark) DarkGlass else LightGlass
```

### 8.4 浅色专属：底边暗线

这是浅色模式唯一需要**新增**绘制的一层。玻璃的下缘是光的背面，在浅色环境里会呈现一条极窄的灰线，它是整个卡片"立起来"的支点。

```kotlin
// drawGlassLayers 末尾追加
if (t.edgeLine.alpha > 0f) {
    drawLine(
        color = t.edgeLine,
        start = Offset(cornerPx * 0.8f, h - rimPx),
        end = Offset(w - cornerPx * 0.8f, h - rimPx),
        strokeWidth = rimPx,
    )
}
```

α 控制在 **0.12 ~ 0.16**。低于 0.10 看不见，高于 0.20 会变成一条明显的灰边，反而廉价。

### 8.5 噪点也要反相

浅色背景上，白色噪点等于没有。浅色必须用**深色**噪点：

```kotlin
private fun buildNoise(t: GlassTokens): Brush {
    val n = 128
    val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val rnd = Random(7)
    val pixels = IntArray(n * n) {
        val v = if (t.noiseIsDark) rnd.nextInt(0, 66) else rnd.nextInt(190, 256)
        (t.noiseAlpha shl 24) or (v shl 16) or (v shl 8) or v
    }
    bmp.setPixels(pixels, 0, n, 0, 0, n, n)
    bmp.asImageBitmap()
}
```

### 8.6 浅色背景的要求：不能用纯白

浅色玻璃同样需要"被照的东西"，只是方向反过来——背景要比玻璃**暗一点点**，且带**低饱和彩色**。

| 可用 | 不可用 |
|---|---|
| 浅灰蓝渐变 `#EEF2F7 → #E1E8F5` | 纯白 `#FFFFFF` |
| 马卡龙色斑（薰衣草/薄荷/蜜桃），饱和度 30~45% | 高饱和纯色块 |
| 带轻微明暗的图片（虚化后） | 纯色平铺 |

两条量化红线：

- 背景与玻璃的**亮度差 ≤ 20%**，超过就会过曝成一片白；
- 背景**饱和度控制在 0.3 ~ 0.45**，太灰玻璃没颜色可透，太艳会吃掉玻璃。

同时浅色的模糊参数要收一收：

```css
/* ❌ 深色那套直接搬到浅色，会过曝 */
backdrop-filter: blur(26px) saturate(190%) brightness(1.07);

/* ✅ 浅色 */
backdrop-filter: blur(24px) saturate(120%) brightness(1.02);
```

`saturate` 从 190% 降到 120%，`brightness` 从 1.07 降到 1.02。浅色背景本身已经够亮，再放大只会发白刺眼。

### 8.7 CSS 对照

```css
/* 浅色玻璃 */
.glass.light {
  background: linear-gradient(150deg,
      rgba(255,255,255,.80) 0%,
      rgba(239,243,250,.55) 52%,
      rgba(255,255,255,.72) 100%);

  box-shadow:
    0 1px 0 rgba(255,255,255,1) inset,          /* 顶缘亮线，比深色更亮 */
    0 1px 0 rgba(100,116,139,.14) inset,         /* 底边暗线（inset 模拟） */
    0 8px 20px -6px rgba(15,23,42,.10),
    0 24px 48px -20px rgba(15,23,42,.14);

  -webkit-backdrop-filter: blur(24px) saturate(120%) brightness(1.02);
  backdrop-filter: blur(24px) saturate(120%) brightness(1.02);
}

.glass.light::before {
  background:
    radial-gradient(72% 60% at var(--mx) var(--my), rgba(255,255,255,.95) 0%, rgba(255,255,255,.25) 26%, transparent 52%),
    linear-gradient(180deg, rgba(255,255,255,.55) 0%, transparent 18%,
                    transparent 78%, rgba(100,116,139,.06) 100%);
  mix-blend-mode: normal;   /* ★ 浅色不能用 screen，会过曝 */
}

.glass.light::after {
  background: linear-gradient(145deg,
      rgba(255,255,255,1) 0%,
      rgba(255,255,255,.45) 35%,
      rgba(255,255,255,.55) 65%,
      rgba(255,255,255,.95) 100%);
}
```

注意最后那条 `mix-blend-mode`：深色用 `screen` 提亮，浅色必须改成 `normal`，否则白色叠白色直接烧掉。

### 8.8 内容层也要跟着换

```kotlin
Text(color = t.textPrimary,   fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
Text(color = t.textSecondary, fontSize = 13.sp)
```

- 浅色正文用 `#0F172A` @ 0.60，**不要**用 `black.copy(0.6)`，纯黑在浅色玻璃上会发硬；
- 图标底座：深色是 `white.copy(.16)` + 白描边；浅色要改成 `white.copy(.75)` + `#64748B @ .12` 描边，否则底座会消失。

---

## 9. 参数调优表

### 想调什么，改哪一层

| 目标 | 调整项 | 方向 | 副作用 |
|---|---|---|---|
| 更通透 | ① 厚度渐变 alpha ↓ | 0.175 → 0.12 | 文字对比度下降 |
| 更厚实 | ⑤ 底部暗角 alpha ↑ | 0.17 → 0.22 | 超过 0.25 显脏 |
| 更"反光" | ④ 小锐光 alpha ↑ | 0.34 → 0.45 | 太强会像塑料高光 |
| 更"柔" | ② 大柔光半径 ↑ | 0.88w → 1.1w | 会溢出卡片被裁剪 |
| 边缘更立体 | ⑦ 首尾 alpha ↑ | 0.82/0.52 → 0.95/0.65 | 过亮会像白色描边 |
| 去掉塑料感 | ⑥ 噪点 alpha ↑ | 0x10 → 0x14 | 超过 0x20 能看到麻点 |
| 更"浮" | ⑧ elevation + 带色阴影 alpha ↑ | 26dp → 34dp | 阴影面积变大，注意下方元素 |
| 更"薄" | ① 中段谷值 ↓ | 0.045 → 0.02 | 低于 0.02 失去层次 |

### 圆角与尺寸

| 卡片尺寸 | 推荐圆角 | 说明 |
|---|---|---|
| 小（< 120dp，如按钮/chip） | 高度的一半，或 999（全圆） | 胶囊 |
| 中（120 ~ 240dp，列表卡） | 20 ~ 22dp | 最通用 |
| 大（> 240dp，主面板） | 26 ~ 30dp | 圆角过小会显得硬 |

**圆角与模糊值是正相关的**：圆角越大，边缘的折射弧越长，模糊值也应该越大（20dp 圆角配 20dp 模糊，28dp 配 26dp）。

---

## 10. 性能与降级

### 10.1 绘制成本

七层全是 `DrawScope` 的填充操作，单卡片约 **6 次 draw call**，在现代设备上可忽略。真正贵的是 Haze 和 blur。

| 场景 | 单卡成本 | 建议 |
|---|---|---|
| 纯七层模拟 | 极低 | 列表可放心用 |
| 七层 + Haze | 高（每帧抓像素） | 可见卡片 ≤ 3 |
| 七层嵌套（卡中卡） | 中 | 最多两层 |

### 10.2 三档降级

```kotlin
enum class GlassTier { FULL, LITE, FLAT }

@Composable
fun rememberGlassTier(): GlassTier {
    val isDark = isSystemInDarkTheme()
    return when {
        Build.VERSION.SDK_INT < 28 -> GlassTier.LITE     // 无 ambientColor
        Build.VERSION.SDK_INT < 31 -> GlassTier.LITE     // 无 GPU blur
        else -> GlassTier.FULL
    }
}
```

- **FULL**：七层全开；
- **LITE**：去掉噪点（省一次全卡填充）、小锐光改为静态、阴影用默认黑；
- **FLAT**：只保留①+⑦，用纯色 + 1px 描边，最省。

### 10.3 缓存

`Brush` 对象不要在 `drawBehind` 里每次新建。如果卡片的尺寸固定，用 `drawWithCache`：

```kotlin
.drawWithCache {
    val bodyBrush = Brush.linearGradient(..., end = Offset(size.width, size.height))
    onDrawBehind { drawRect(bodyBrush) }
}
```

不过 `drawWithCache` 无法覆盖跟随指针的高光（每帧都在变），那两层留给 `drawBehind`。

---

## 11. 排查清单

| 现象 | 根因 | 修法 |
|---|---|---|
| 卡片像半透明灰砖 | 只有底色 + 描边两层 | 补高光、暗角、噪点 |
| 高光不跟手 | 位置写在 constant 里 | 用 `pointerInput` + `mutableStateOf` |
| 高光跟手但滑动卡顿 | 状态在 Composable 作用域被读取 | 移到 `drawBehind` 内部读取 |
| 边缘发糊、描边看不见 | 描边画在柔光之前 | 把 `drawRoundRect` 移到最后的绘制步骤 |
| 边缘有毛刺 | 噪点画在描边之后 | 噪点放描边之前 |
| 渐变色带明显 | 缺噪点层 | 加 `0x10` 噪点 |
| 噪点看起来是"麻点" | alpha 过高（> 0x20） | 降到 `0x08 ~ 0x14` |
| 卡片发白、糊成一片 | 与 Haze 叠加时底色重复 | 厚度渐变各降 0.03，`backgroundColor` 降到 0.04 |
| 阴影被裁掉 | `clip` 在 `shadow` 之前 | 顺序改为 `shadow → clip` |
| 按压没手感 | 用了 `tween` | 换 `spring(dampingRatio = 0.42f)` |
| 圆角处描边有缺口 | 描边没有内缩 | `topLeft` 和 `size` 各让出半个 `rimPx` |
| 浅色模式下卡片完全看不见 | 白叠白，缺边界 | 补底边暗线 + 柔和外阴影 |
| 浅色卡片发白过曝 | 沿用了深色的 brightness/saturate | 降到 1.02 / 120% |
| 浅色卡片下边界消失 | 没有底边暗线 | 画 `edgeLine`，alpha 0.12~0.16 |
| 浅色颗粒看不见 | 用了白噪点 | 反相为深色噪点，alpha 4% |
| 浅色阴影显脏 | 沿用了紫色 ambientColor | 换中性冷灰 `#64748B` |

---

## 附录：一句话版本

> 玻璃之所以是玻璃，是因为它**改变了穿过它的光**。
> 半透明只解决"看得见背后"，高光才解决"光是怎么被改变的"。
> 没有高光和噪点的玻璃，本质上是一块磨砂塑料。

---

*配套文件：`PremiumGlass.kt`（Compose 实现） · `premium-glass.html`（七层可视化 Demo，含新旧对比开关）*