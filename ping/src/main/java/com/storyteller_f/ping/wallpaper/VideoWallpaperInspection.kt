package com.storyteller_f.ping.wallpaper

import com.storyteller_f.ping.shader.OutputSnapshot
import com.storyteller_f.ping.shader.OutputInspection
import com.storyteller_f.ping.shader.RendererSnapshot

/** 仅供主线程读取的不可变诊断快照，不暴露 Engine、MediaPlayer 或 GL 对象。 */
internal interface VideoWallpaperInspection {
    fun snapshot(): VideoWallpaperSnapshot
    fun output(): OutputInspection?
}

internal data class VideoWallpaperSnapshot(
    val isPreview: Boolean,
    val visible: Boolean,
    val sourceId: Long,
    val playerState: String,
    val positionMs: Int?,
    val playersCreated: Int,
    val playersReleased: Int,
    val observing: Boolean,
    val destroyed: Boolean,
    val glStopped: Boolean,
    val output: OutputSnapshot?,
    val renderer: RendererSnapshot,
)
