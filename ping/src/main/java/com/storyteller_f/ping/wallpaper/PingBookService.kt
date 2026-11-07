package com.storyteller_f.ping.wallpaper

import android.content.Context
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.storyteller_f.ping.bookDataStore
import com.storyteller_f.ping.cubism.Delegate
import com.storyteller_f.ping.cubism.GLRenderer
import com.storyteller_f.ping.selectedWallPaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class PingBookService : WallpaperService() {
    override fun onCreateEngine(): Engine = BookEngine()

    private inner class BookEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val renderer = GLRenderer(Delegate(this@PingBookService))
        private val surface = WallpaperSurface(this@PingBookService).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
        }
        init {
            setTouchEventsEnabled(false)
        }
        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            scope.launch {
                bookDataStore.data.mapNotNull { it.selectedWallPaper(isPreview) }
                    .distinctUntilChanged().collect { path ->
                        surface.queueEvent { renderer.plugModel(path) }
                    }
            }
        }
        private inner class WallpaperSurface(context: Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder = this@BookEngine.surfaceHolder
            fun detach() = super.onDetachedFromWindow()
        }
        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) surface.onResume() else surface.onPause()
        }
        override fun onDestroy() {
            scope.cancel()
            surface.queueEvent { renderer.destroy() }
            surface.detach()
            super.onDestroy()
        }
    }
}
