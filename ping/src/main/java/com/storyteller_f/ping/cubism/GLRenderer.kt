package com.storyteller_f.ping.cubism

import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.storyteller_f.ping.DelegatePool
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer(private val delegate: Delegate) : GLSurfaceView.Renderer {

    // Called at initialization (when the drawing context is lost and recreated).
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) = delegate.onSurfaceCreated()

    // Mainly called when switching between landscape and portrait.
    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) =
        delegate.onSurfaceChanged(width, height)

    // Called repeatedly for drawing.
    override fun onDrawFrame(unused: GL10) = DelegatePool.judge(delegate) {
        delegate.onDrawFrame()
    }

    fun destroy() = delegate.destroy()

    fun plugModel(jsonPath: String) = delegate.plugModule(jsonPath)

    fun onTouchEvent(event: MotionEvent) {
        val pointX = event.x
        val pointY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> delegate.onTouchBegan(pointX, pointY)
            MotionEvent.ACTION_UP -> delegate.onTouchEnd()
            MotionEvent.ACTION_MOVE -> delegate.onTouchMoved(pointX, pointY)
        }
    }

}
