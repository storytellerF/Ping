package com.storyteller_f.ping.wallpaper

/** 发布构建不保留诊断对象或测试控制。 */
internal object VideoWallpaperDebug {
    fun register(inspection: VideoWallpaperInspection, preserve: (Boolean) -> Unit) = Unit
    fun remove(inspection: VideoWallpaperInspection) = Unit
}
