package com.storyteller_f.ping.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.edit
import com.storyteller_f.ping.pagerDataStore
import com.storyteller_f.ping.preview
import com.storyteller_f.ping.selected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** 测试环境的保存与恢复成对执行；恢复组件之前先恢复它要读取的选择参数。 */
internal class WallpaperTestEnvironment(
    private val stop: suspend () -> Unit,
    private val restoreSelection: suspend () -> Unit,
    private val restoreComponent: suspend () -> Unit,
) {
    var selectionRestored: Boolean = false
        private set

    /**
     * 各阶段失败后仍执行后续恢复，最终抛出首次异常，其他异常按发生顺序附加。
     * 父协程取消不能中断环境恢复；每一步的挂起等待最多 15 秒，恢复后继续传播取消。
     */
    suspend fun restore() {
        var firstFailure: Throwable? = null
        suspend fun attempt(action: suspend () -> Unit) {
            try {
                withTimeout(15000) { action() }
            } catch (failure: Throwable) {
                val original = firstFailure
                if (original == null) firstFailure = failure
                else if (original !== failure) original.addSuppressed(failure)
            }
        }
        withContext(NonCancellable) {
            attempt(stop)
            attempt {
                restoreSelection()
                selectionRestored = true
            }
            attempt(restoreComponent)
        }
        firstFailure?.let { throw it }
        currentCoroutineContext().ensureActive()
    }

    companion object {
        val staticComponent = ComponentName("com.android.systemui", "com.android.systemui.wallpapers.ImageWallpaper")
        suspend fun capture(
            context: Context,
            manager: WallpaperManager,
            setComponent: (ComponentName) -> Unit,
        ): WallpaperTestEnvironment = capture(context, manager, setComponent, stop = { stopVideo(setComponent) })

        suspend fun capture(
            context: Context,
            manager: WallpaperManager,
            setComponent: (ComponentName) -> Unit,
            stop: suspend () -> Unit,
        ): WallpaperTestEnvironment {
            val preferences = context.pagerDataStore.data.first()
            val component = manager.wallpaperInfo?.component
            val selectedPath = preferences[selected]
            val previewPath = preferences[preview]
            return WallpaperTestEnvironment(
                stop = stop,
                restoreSelection = {
                    context.pagerDataStore.edit {
                        selectedPath?.let { value -> it[selected] = value } ?: it.remove(selected)
                        previewPath?.let { value -> it[preview] = value } ?: it.remove(preview)
                    }
                },
                restoreComponent = { setComponent(component ?: staticComponent) },
            )
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
