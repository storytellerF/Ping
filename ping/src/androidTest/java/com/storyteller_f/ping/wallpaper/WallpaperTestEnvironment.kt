package com.storyteller_f.ping.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.edit
import com.storyteller_f.ping.pagerDataStore
import com.storyteller_f.ping.preview
import com.storyteller_f.ping.selected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** 测试环境的保存与恢复成对执行；恢复组件之前先恢复它要读取的选择参数。 */
internal class WallpaperTestEnvironment private constructor(
    private val context: Context,
    private val component: ComponentName?,
    private val selectedPath: String?,
    private val previewPath: String?,
    private val setComponent: (ComponentName) -> Unit,
) {
    suspend fun restore() {
        stopVideo(setComponent)
        context.pagerDataStore.edit {
            selectedPath?.let { value -> it[selected] = value } ?: it.remove(selected)
            previewPath?.let { value -> it[preview] = value } ?: it.remove(preview)
        }
        setComponent(component ?: staticComponent)
    }

    companion object {
        val staticComponent = ComponentName("com.android.systemui", "com.android.systemui.wallpapers.ImageWallpaper")
        suspend fun capture(context: Context, manager: WallpaperManager, setComponent: (ComponentName) -> Unit): WallpaperTestEnvironment {
            val preferences = context.pagerDataStore.data.first()
            return WallpaperTestEnvironment(context, manager.wallpaperInfo?.component, preferences[selected], preferences[preview], setComponent)
        }
        suspend fun stopVideo(setComponent: (ComponentName) -> Unit) {
            val stopping = withContext(Dispatchers.Main) { VideoWallpaperDebug.engines }
            setComponent(staticComponent)
            withTimeout(15000) {
                while (!withContext(Dispatchers.Main) {
                    VideoWallpaperDebug.engines.isEmpty() && stopping.all {
                        val state = it.snapshot()
                        state.destroyed && state.glStopped && !state.observing
                    }
                }) delay(30)
            }
        }
    }
}
