package com.storyteller_f.ping.shader

/**
 * 屏幕滚动偏移量（归一化坐标）。
 *
 * - `xOffset`/`yOffset` 取值通常在 [-0.5, 0.5] 范围内，用于左右/上下平移裁剪后的视频。
 * - 实际可用的最大偏移由 `SurfaceTextureBinding` 根据视频与屏幕的宽高比计算得到。
 */
data class Offset(val xOffset: Float = 0f, val yOffset: Float = 0f) // 偏移量数据结构