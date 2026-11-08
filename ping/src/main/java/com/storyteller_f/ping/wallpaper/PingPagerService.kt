package com.storyteller_f.ping.wallpaper

import android.app.ActivityManager
import android.app.WallpaperColors
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.storyteller_f.ping.pagerDataStore
import com.storyteller_f.ping.selectedWallPaper
import com.storyteller_f.ping.shader.GLES20WallpaperRenderer
import com.storyteller_f.ping.shader.GLES30WallpaperRenderer
import com.storyteller_f.ping.shader.GLWallpaperRenderer
import com.storyteller_f.ping.shader.Offset
import com.storyteller_f.ping.shader.OutputHandoff
import com.storyteller_f.ping.shader.VideoOutput
import com.storyteller_f.ping.shader.VideoMatrix
import com.storyteller_f.ping.shader.WallpaperContextFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PingPagerService : WallpaperService() {
    private val job = Job()

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind() called with: intent = $intent")
        return super.onUnbind(intent)
    }

    override fun onCreateEngine(): Engine {
        Log.d(TAG, "onCreateEngine() called")
        return PingEngine(this)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        super.onDestroy()
        job.cancel()
    }

    private inner class PingEngine(inContext: Context) : WallpaperService.Engine() {
        private var currentThumbnail: Bitmap? = null
        private val engineJob = Job(job)
        private val engineScope = CoroutineScope(engineJob + Dispatchers.Main)
        private val mainHandler = Handler(Looper.getMainLooper())
        private var destroyed = false
        private var visible = false
        private var playback: Playback? = null
        private val outputs = OutputHandoff<VideoOutput>(
            acquire = { it.acquire() },
            attach = { output ->
                playback?.player?.setSurface(output?.surface)
                updatePlayback(output != null)
            },
            release = {
                it.surface.release()
                it.detach()
                surfaceView.queueEvent { renderer.releaseRetiredOutputs() }
            },
        )
        private val renderer: GLWallpaperRenderer = run {
            val systemService =
                ContextCompat.getSystemService(inContext, ActivityManager::class.java)
                    ?: throw Exception("无法获得activity manager")
            val deviceConfigurationInfo = systemService.deviceConfigurationInfo
            when {
                deviceConfigurationInfo.reqGlEsVersion >= 0x30000 -> {
                    GLES30WallpaperRenderer(inContext)
                }

                deviceConfigurationInfo.reqGlEsVersion >= 0x20000 -> {
                    GLES20WallpaperRenderer(inContext)
                }

                else -> throw RuntimeException("can not get gl version")
            }
        }

        private val surfaceView: GLPingSurfaceView by lazy {
            GLPingSurfaceView(inContext).apply {
                setEGLContextClientVersion(renderer.version)
                preserveEGLContextOnPause = true
                setEGLContextFactory(WallpaperContextFactory(renderer))
                renderer.requestRender = { requestRender() }
                renderer.onOutputCreated = { output -> mainHandler.post { outputs.offer(output) } }
                renderer.onOutputDestroyed = { output ->
                    mainHandler.post {
                        // 保留旧生产队列到新 Surface 直接替换它；先 setSurface(null) 会拆除
                        // 已暂停的视频解码器，部分实现随后虽报告 playing，却不再产出帧。
                        if (outputs.current === output) updatePlayback(false)
                    }
                }
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
        }

        inner class GLPingSurfaceView(context: Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder = surfaceHolder
            fun destroy() {
                // queueEvent 在暂停时仍会执行；先清理再退出，避免退出标志抢先丢弃清理事件。
                queueEvent {
                    renderer.onRendererStopped()
                    mainHandler.post { detachAfterCleanup() }
                }
            }

            private fun detachAfterCleanup() = super.onDetachedFromWindow()
        }

        val data = inContext.pagerDataStore.data

        private fun observeLatestUri() {
            engineScope.launch {
                data.mapNotNull { preferences ->
                    // No type safety.
                    preferences.selectedWallPaper(isPreview)
                }.distinctUntilChanged().collectLatest {
                    val file = File(it)
                    val thumbnail = File(file.parentFile, "thumbnail.jpg")
                    Log.i(
                        TAG,
                        "wallpaper $it thumb${thumbnail.absolutePath} ${thumbnail.exists()}"
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        currentThumbnail = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(thumbnail.absolutePath)
                        }
                        notifyColorsChanged()
                    }
                    replacePlayer(it)
                }
            }
        }

        // MediaPlayer 的构造、回调和所有操作均在主 Looper 上。
        private fun replacePlayer(path: String) {
            releasePlayer()
            renderer.setVideoMatrix(null)
            val player = MediaPlayer()
            playback = Playback.Preparing(player)
            player.isLooping = true
            player.setOnVideoSizeChangedListener { source, width, height ->
                if (!destroyed && playback?.player === source) {
                    renderer.setVideoMatrix(VideoMatrix(width, height, 0))
                }
            }
            player.setOnPreparedListener { source ->
                if (!destroyed && playback?.player === source) {
                    playback = Playback.Ready(source)
                    updatePlayback()
                }
            }
            player.setOnErrorListener { source, what, extra ->
                if (!destroyed && playback?.player === source) {
                    Log.e(TAG, "Wallpaper playback failed: what=$what extra=$extra")
                    releasePlayer()
                }
                true
            }
            try {
                player.setSurface(outputs.current?.takeIf { it.isValid }?.surface)
                player.setDataSource(path)
                player.prepareAsync()
            } catch (failure: Exception) {
                releasePlayer()
                Log.e(TAG, "Unable to prepare wallpaper video", failure)
            }
        }

        private fun updatePlayback(hasOutput: Boolean = outputs.current?.isValid == true) {
            val ready = playback as? Playback.Ready ?: return
            if (visible && hasOutput) {
                if (!ready.player.isPlaying) ready.player.start()
            } else if (ready.player.isPlaying) {
                ready.player.pause()
            }
        }

        private fun releasePlayer() {
            val previous = playback ?: return
            playback = null
            previous.player.run {
                setOnPreparedListener(null)
                setOnVideoSizeChangedListener(null)
                setOnErrorListener(null)
                setSurface(null)
                release()
            }
        }

        override fun onCommand(
            action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean
        ): Bundle? {
            Log.d(
                TAG,
                "onCommand() called with: action = $action, x = $x, y = $y, z = $z, extras = $extras, resultRequested = $resultRequested"
            )
            return null
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            Log.d(TAG, "onCreate() called with: surfaceHolder = $surfaceHolder")
            super.onCreate(surfaceHolder)
            surfaceView
            observeLatestUri()
        }

        override fun onDestroy() {
            Log.d(TAG, "onDestroy() called")
            super.onDestroy()
            destroyed = true
            engineJob.cancel()
            outputs.close()
            releasePlayer()
            // 先断开生产者；GL 线程完成清理后再退出并销毁 context。
            surfaceView.destroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) surfaceView.onResume()
            updatePlayback()
            if (!visible) surfaceView.onPause()
            super.onVisibilityChanged(visible)
        }

        /**
         * OffsetStep 是page 索引变化。
         * PixelOffset 是像素变化。
         */
        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            Log.d(
                TAG,
                "onOffsetsChanged() called with: xOffset = $xOffset, yOffset = $yOffset, xOffsetStep = $xOffsetStep, yOffsetStep = $yOffsetStep, xPixelOffset = $xPixelOffset, yPixelOffset = $yPixelOffset"
            )
            super.onOffsetsChanged(
                xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset
            )
            renderer.setOffset(Offset(xOffset, yOffset))
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?, format: Int, width: Int, height: Int
        ) {
            Log.d(
                TAG,
                "onSurfaceChanged() called with: holder = $holder, format = $format, width = $width, height = $height"
            )
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder?) {
            Log.d(TAG, "onSurfaceRedrawNeeded() called with: holder = $holder")
            super.onSurfaceRedrawNeeded(holder)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            Log.d(TAG, "onSurfaceCreated() called with: holder = $holder")
            super.onSurfaceCreated(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            Log.d(TAG, "onSurfaceDestroyed() called with: holder = $holder")
            super.onSurfaceDestroyed(holder)
        }

        override fun onComputeColors() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            currentThumbnail?.let {
                WallpaperColors.fromBitmap(it).apply {
                    Log.i(TAG, "onComputeColors: ${this.primaryColor}")
                }
            }
        } else {
            null
        }
    }

    private sealed interface Playback {
        val player: MediaPlayer
        data class Preparing(override val player: MediaPlayer) : Playback
        data class Ready(override val player: MediaPlayer) : Playback
    }

    companion object {
        private const val TAG = "PingPagerService"
    }
}

