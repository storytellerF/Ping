package com.storyteller_f.ping

import android.opengl.EGL14
import android.opengl.GLES20
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import com.live2d.sdk.cubism.framework.rendering.android.CubismShaderAndroid
import com.live2d.sdk.cubism.framework.rendering.android.shaderindex.CubismShaderIndexConstants
import org.junit.Assert.*
import org.junit.Test

class ShaderVariantsDeviceTest {
    @Test fun allShaderVariantsCompileAndLegacyModesReusePrograms() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display, null, 0, null, 0))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 4,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0))
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0)
        try {
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
            CubismRendererAndroid.reloadShader()
            val shader = CubismShaderAndroid.getInstance()
            val lookup = shader.javaClass.getDeclaredMethod("getShaderSet", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
            val programs = (0 until CubismShaderIndexConstants.SHADER_COUNT).map { index ->
                val set = lookup.invoke(shader, index)!!
                set.javaClass.getDeclaredField("shaderProgram").apply { isAccessible = true }.getInt(set)
                    .also { assertTrue("Shader $index failed", GLES20.glIsProgram(it)) }
            }
            assertEquals(482, programs.toSet().size)
            repeat(CubismShaderIndexConstants.MASK_TYPE_COUNT) { variant ->
                val normal = programs[CubismShaderIndexConstants.NORMAL_OVER_BASE_INDEX + variant]
                assertEquals(normal, programs[CubismShaderIndexConstants.ADD_COMPATIBLE_BASE_INDEX + variant])
                assertEquals(normal, programs[CubismShaderIndexConstants.MULTIPLY_COMPATIBLE_BASE_INDEX + variant])
            }
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
            CubismRendererAndroid.staticRelease()
            assertTrue(programs.none { GLES20.glIsProgram(it) })
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        } finally {
            CubismRendererAndroid.staticRelease()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
