package com.storyteller_f.ping.shader

import android.content.Context
import android.opengl.GLES20
import com.storyteller_f.ping.R
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class GLES20WallpaperRenderer(context: Context) :
    GLWallpaperRenderer(
        context,
        R.raw.vertex_20,
        R.raw.fragment_20,
        2
    ) {
    private val positionLocation by lazy { GLES20.glGetAttribLocation(program, "in_position") }
    private val texCoordinationLocation by lazy {
        GLES20.glGetAttribLocation(
            program,
            "in_texture_coordination"
        )
    }

    override fun onSurfaceCreated(gl10: GL10, eglConfig: EGLConfig) {
        initGl()
        positionLocation
        texCoordinationLocation
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    }

    override fun drawImage() {
        bindData(buffers[0], positionLocation)
        bindData(buffers[1], texCoordinationLocation)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[2])
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_INT, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        GLES20.glDisableVertexAttribArray(texCoordinationLocation)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        GLES20.glUseProgram(0)
    }

    companion object
}