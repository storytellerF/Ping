package com.storyteller_f.ping.shader

/**
 * 视频的原始尺寸与旋转信息。
 *
 * - 播放器与元数据常将旋转以 90/180/270 度标注，但宽高仍给出「拍摄时」的原始值。
 * - `realWidth`/`realHeight` 根据旋转是否导致横竖翻转进行交换，便于后续矩阵计算与缓冲区设置。
 */
data class VideoMatrix(val width: Int, val height: Int, val rotation: Int) { // 原始宽、高与旋转角度
    private val horizontalFlip: Boolean // 是否需要横竖翻转（90/270 度）
        get() = rotation % 180 != 0 // 旋转为奇数个 90 度则为真

    val realHeight = if (horizontalFlip) width else height // 根据旋转后的实际高度
    val realWidth = if (horizontalFlip) height else width // 根据旋转后的实际宽度
}