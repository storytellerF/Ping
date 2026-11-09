package com.storyteller_f.ping.shader

import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.opengles.GL10

class WallpaperRendererTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun es2RendersAfterContextRecreation() = verifyRecreation(GLES20WallpaperRenderer(context))
    @Test fun es3RendersAfterContextRecreation() = verifyRecreation(GLES30WallpaperRenderer(context))

    private fun verifyRecreation(renderer: GLWallpaperRenderer) {
        repeat(2) {
            withRenderer(renderer) { harness ->
                harness.paint(harness.output, Color.BLUE)
                assertEquals(255, harness.pixels()[2].toInt() and 255)
                assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
            }
        }
    }

    @Test fun waitingForNextVideoPreservesOldPixelsAndCropUntilItsFirstFrame() {
        withRenderer(GLES20WallpaperRenderer(context)) { harness ->
            harness.paint(harness.output, Color.RED, striped = true)
            val previous = harness.pixels()
            harness.select(2)
            harness.draw()
            assertArrayEquals("Missing metadata must retain the old frame and crop", previous, harness.pixels())
            harness.renderer.setVideoMatrix(2, VideoMatrix(180, 320, 0))
            harness.draw()
            assertArrayEquals("New metadata must wait for its own first frame", previous, harness.pixels())
            harness.renderer.setVideoMatrix(1, VideoMatrix(100, 100, 0)) // 旧播放器的晚到尺寸。
            harness.draw()
            assertArrayEquals(previous, harness.pixels())
            harness.paint(harness.output, Color.GREEN)
            assertEquals(255, harness.pixels()[1].toInt() and 255)
            assertEquals(2L, harness.renderer.inspection.value.displayedSource)
            assertEquals(0, harness.renderer.inspection.value.retiredOutputs)
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        }
    }

    // runBlocking 保持 EGL 的线程亲和性；主 Looper 仍可交付 SurfaceTexture 帧通知。
    private fun withRenderer(renderer: GLWallpaperRenderer, test: suspend (Harness) -> Unit) = runBlocking<Unit> {
        val egl = EGLContext.getEGL() as EGL10
        val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        check(egl.eglInitialize(display, IntArray(2)))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(egl.eglChooseConfig(display, intArrayOf(
            EGL10.EGL_RENDERABLE_TYPE, if (renderer.version == 2) 4 else 64,
            EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
            EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_NONE,
        ), configs, 1, count))
        check(count[0] > 0)
        val config = checkNotNull(configs[0])
        val factory = WallpaperContextFactory(renderer)
        val eglContext = factory.createContext(egl, display, config)
        val target = egl.eglCreatePbufferSurface(display, config, intArrayOf(
            EGL10.EGL_WIDTH, 16, EGL10.EGL_HEIGHT, 16, EGL10.EGL_NONE,
        ))
        check(egl.eglMakeCurrent(display, target, target, eglContext))
        val harness = Harness(renderer, eglContext.gl as GL10)
        try {
            harness.select(1)
            renderer.setVideoMatrix(1, VideoMatrix(320, 180, 0))
            renderer.onSurfaceCreated(harness.gl, config)
            renderer.onSurfaceChanged(harness.gl, 16, 16)
            test(harness)
        } finally {
            harness.close()
            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
            egl.eglDestroySurface(display, target)
            factory.destroyContext(egl, display, eglContext)
            egl.eglTerminate(display)
        }
    }

    private class Harness(val renderer: GLWallpaperRenderer, val gl: GL10) {
        private val frames = Channel<Unit>(Channel.CONFLATED)
        private val outputs = OutputSession<VideoOutput> { }
        val output: VideoOutput get() = checkNotNull(outputs.current)
        init {
            renderer.requestRender = { frames.trySend(Unit); Unit }
            renderer.scheduleCleanup = { renderer.releaseRetiredOutputs() }
            renderer.onOutputCreated = { outputs.offer(it) }
        }
        fun select(sourceId: Long) { outputs.selectSource(sourceId); renderer.selectSource(sourceId) }
        fun draw() = renderer.onDrawFrame(gl)
        suspend fun paint(output: VideoOutput, color: Int, striped: Boolean = false) {
            while (frames.tryReceive().isSuccess) { /* 丢弃参数变更产生的绘制请求。 */ }
            output.connectTo { surface ->
                val canvas = surface.lockCanvas(null)
                try {
                    canvas.drawColor(color)
                    if (striped) canvas.drawRect(0f, 0f, canvas.width * 0.4f, canvas.height.toFloat(), Paint().apply { this.color = Color.BLUE })
                } finally { surface.unlockCanvasAndPost(canvas) }
            }
            withTimeout(5000) { frames.receive() }
            draw()
        }
        fun pixels(): ByteArray {
            val buffer = ByteBuffer.allocateDirect(16 * 16 * 4)
            GLES20.glReadPixels(0, 0, 16, 16, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            return ByteArray(buffer.capacity()).also { buffer.get(it) }
        }
        fun close() {
            outputs.close()
            renderer.onRendererStopped()
            frames.close()
        }
    }
}
