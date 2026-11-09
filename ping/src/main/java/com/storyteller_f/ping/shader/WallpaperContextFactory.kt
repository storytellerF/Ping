package com.storyteller_f.ping.shader

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

/** 在 pause、context 丢失和 detach 时撤销输出；生产者解绑后的 SurfaceTexture 清理由 GL 线程完成。 */
internal class WallpaperContextFactory(
    private val renderer: GLWallpaperRenderer,
) : GLSurfaceView.EGLContextFactory {
    override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): EGLContext =
        egl.eglCreateContext(
            display, config, EGL10.EGL_NO_CONTEXT,
            intArrayOf(EGL_CONTEXT_CLIENT_VERSION, renderer.version, EGL10.EGL_NONE),
        )

    override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
        try {
            renderer.onContextDestroyed()
        } finally {
            check(egl.eglDestroyContext(display, context)) { "Failed to destroy wallpaper EGL context" }
        }
    }
    private companion object {
        const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
    }
}
