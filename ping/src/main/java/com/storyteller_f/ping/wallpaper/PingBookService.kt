package com.storyteller_f.ping.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
import android.os.Build
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.storyteller_f.ping.bookDataStore
import com.storyteller_f.ping.cubism.Delegate
import com.storyteller_f.ping.cubism.GLRenderer
import com.storyteller_f.ping.selectedWallPaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext


class PingBookService : WallpaperService() {
    val job = Job()
    val scope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = job + Dispatchers.Main

    }


    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind() called with: intent = $intent")
        return super.onUnbind(intent)
    }

    override fun onCreateEngine(): Engine {
        Log.d(TAG, "onCreateEngine() called")
        return CubismEngine(this, System.currentTimeMillis())
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        super.onDestroy()
        job.cancel()
    }

    private inner class CubismEngine(private val inContext: Context, val index: Long) :
        WallpaperService.Engine() {
        private var currentThumbnail: Bitmap? = null

        private val renderer = GLRenderer(Delegate(inContext))

        private val surfaceView = GLPingSurfaceView(inContext).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = RENDERMODE_CONTINUOUSLY
        }

        inner class GLPingSurfaceView(context: Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder = surfaceHolder
            fun destroy() = super.onDetachedFromWindow()
        }

        init {
            scope.launch {
                inContext.bookDataStore.data.mapNotNull { preferences ->
                    // No type safety.
                    preferences.selectedWallPaper()
                }.distinctUntilChanged().collectLatest { jsonFilePath ->
                    val parent = File(jsonFilePath).parent
                    val thumbnail = File(parent, "thumbnail.jpg")
                    Log.i(
                        TAG,
                        "wallpaper $jsonFilePath thumb${thumbnail.absolutePath} ${thumbnail.exists()}"
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && thumbnail.exists()) {
                        currentThumbnail = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(thumbnail.absolutePath)
                        }
                        notifyColorsChanged()
                    }
                    renderer.plugModel(jsonFilePath)
                }
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
            Log.d(TAG, "onCreate() $index called with: surfaceHolder = $surfaceHolder")
            super.onCreate(surfaceHolder)
        }

        override fun onDestroy() {
            Log.d(TAG, "onDestroy() $index called")
            super.onDestroy()
            renderer.destroy()
            surfaceView.destroy()
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
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?, format: Int, width: Int, height: Int
        ) {
            Log.d(
                TAG,
                "onSurfaceChanged() $index called with: holder = $holder, format = $format, width = $width, height = $height"
            )
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder?) {
            Log.d(TAG, "onSurfaceRedrawNeeded() called with: holder = $holder")
            super.onSurfaceRedrawNeeded(holder)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            Log.d(TAG, "onSurfaceCreated() $index called with: holder = $holder")
            super.onSurfaceCreated(holder)
            holder ?: return
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            Log.d(TAG, "onSurfaceDestroyed() $index called with: holder = $holder")
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

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            event ?: return

            renderer.onTouchEvent(event)
            return super.onTouchEvent(event)
        }
    }

    companion object {
        private const val TAG = "PingBookService"
    }
}

