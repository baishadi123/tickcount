# 相册照片查看器指南（存档）

> 查看器手势与性能要点存档。已落地于 `ui/components/PhotoViewer.kt`（ViewerMedia 双源）。

## 手势仲裁（四个必须处理的冲突）

1. **缩放 vs 翻页**：`userScrollEnabled = !zoomed`（zoomed = scale > 1.01，不是 1——松手常停在 1.0001）
2. **单指平移 vs 下拉关闭**：按缩放状态分——放大归平移，静止归下拉；offset 按图片 **FIT 后实际尺寸** clamp（横图不能拖进自己的黑边）
3. **双击缩放**：用顶层 `animate()` 逐帧写 state，**不用 Animatable**（手势与动画抢同一值会跳）
4. **单击藏 UI vs 双击**：detectTapGestures 双 handler 共存有 ~300ms 延迟，接受

分水岭之外：`Modifier.transformable` 会认领单指 pan——与 Pager 同用必灭翻页，手写双指/单指分发（见 ZoomablePhoto）。

## 下拉关闭

阈值 ~380px 或甩动速度 >1000px/s 关闭；中途松手 spring(0.7) 归位；整叠跟手缩放。

## 性能清单

- 全屏页**整流解码**（系统 loadThumbnail 是格子微缩图，全屏必糊）；缩略条 `size≈120` 显式采样
- Pager `beyondViewportPageCount = 1`；EXIF 按需 + IO 线程 + runCatching
- 缩略条双向同步：`animateScrollToItem(currentPage - 2)` 居中

## 排查速查

放大拖不动→Pager 抢事件；双击后一碰就跳→用了 Animatable；图拖出屏→offset 没 clamp；横图拖出黑边→用了容器尺寸算边界。
