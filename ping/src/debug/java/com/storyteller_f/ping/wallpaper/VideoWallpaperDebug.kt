package com.storyteller_f.ping.wallpaper

/** Debug 构建的只读观察入口；测试控制与快照读取分开，不依赖 framework 私有字段。 */
internal object VideoWallpaperDebug {
    class Engine(val inspection: VideoWallpaperInspection, private val preserve: (Boolean) -> Unit) {
        fun snapshot() = inspection.snapshot()
        fun output() = inspection.output()
        fun preserveContextOnPause(enabled: Boolean) = preserve(enabled)
    }
    private val active = mutableMapOf<VideoWallpaperInspection, Engine>()
    val engines: List<Engine> get() = active.values.toList()
    fun register(inspection: VideoWallpaperInspection, preserve: (Boolean) -> Unit) {
        active[inspection] = Engine(inspection, preserve)
    }
    fun remove(inspection: VideoWallpaperInspection) { active.remove(inspection) }
}
