package com.storyteller_f.ping.shader

import android.graphics.Color
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
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
    @Test fun es2RendersAfterContextRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        verifyContextRecreation(GLES20WallpaperRenderer(context))
    }

    @Test fun es3RendersAfterContextRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        verifyContextRecreation(GLES30WallpaperRenderer(context))
    }

    // runBlocking 在 instrumentation 工作线程保持 EGL 的线程亲和性，主 Looper 仍可交付帧回调。
    private fun verifyContextRecreation(renderer: GLWallpaperRenderer) = runBlocking {
        val egl = EGLContext.getEGL() as EGL10
        val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        check(egl.eglInitialize(display, IntArray(2)))
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(egl.eglChooseConfig(display, intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, if (renderer.version == 2) 4 else 64,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_NONE,
            ), configs, 1, count))
            check(count[0] > 0)
            val config = checkNotNull(configs[0])
            val factory = WallpaperContextFactory(renderer)
            repeat(2) {
                val context = factory.createContext(egl, display, config)
                val target = egl.eglCreatePbufferSurface(display, config, intArrayOf(
                    EGL10.EGL_WIDTH, 16, EGL10.EGL_HEIGHT, 16, EGL10.EGL_NONE,
                ))
                var output: VideoOutput? = null
                try {
                    check(egl.eglMakeCurrent(display, target, target, context))
                    val gl = context.gl as GL10
                    renderer.setVideoMatrix(VideoMatrix(320, 180, 0))
                    val frame = CompletableDeferred<Unit>()
                    renderer.requestRender = { frame.complete(Unit) }
                    renderer.onOutputCreated = { output = it }
                    renderer.onSurfaceCreated(gl, config)
                    renderer.onSurfaceChanged(gl, 16, 16)
                    val created = checkNotNull(output)
                    assertTrue(created.isValid)
                    val canvas = created.surface.lockCanvas(null)
                    try {
                        assertEquals(320, canvas.width)
                        assertEquals(180, canvas.height)
                        canvas.drawColor(Color.BLUE)
                    } finally {
                        created.surface.unlockCanvasAndPost(canvas)
                    }
                    withTimeout(5000) { frame.await() }
                    renderer.onDrawFrame(gl)
                    assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
                    val pixel = ByteBuffer.allocateDirect(4)
                    GLES20.glReadPixels(8, 8, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel)
                    assertEquals(0, pixel.get(0).toInt() and 255)
                    assertEquals(0, pixel.get(1).toInt() and 255)
                    assertEquals(255, pixel.get(2).toInt() and 255)
                    renderer.setVideoMatrix(VideoMatrix(180, 320, 0))
                    renderer.setOffset(Offset(0.5f, 0.5f))
                    renderer.onDrawFrame(gl)
                    assertSame(created, output) // 尺寸与矩阵更新不替换生产队列。
                    assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
                } finally {
                    egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                    egl.eglDestroySurface(display, target)
                    factory.destroyContext(egl, display, context)
                    output?.let {
                        assertFalse(it.isValid)
                        it.surface.release()
                    }
                }
            }
        } finally {
            egl.eglTerminate(display)
        }
    }
}
