package com.storyteller_f.ping.cubism

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.live2d.sdk.cubism.core.ICubismLogger
import com.live2d.sdk.cubism.framework.CubismFramework
import com.storyteller_f.ping.DelegatePool

class Delegate(val context: Context) {

    val pal = Pal()
    val view = View(this,)

    private var windowWidth = 0
    private var windowHeight = 0

    /**
     * クリックしているか
     */
    private var isCaptured = false

    init {
        DelegatePool.addDelegate(this)
    }

    fun onSurfaceCreated() {
        Log.d(TAG, "onSurfaceCreated() called")

        // テクスチャサンプリング設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)

        // 透過設定
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Initialize Cubism SDK framework

        CubismFramework.initialize()
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged() called with: width = $width, height = $height")
        // 描画範囲指定
        GLES20.glViewport(0, 0, width, height)
        windowWidth = width
        windowHeight = height

        // AppViewの初期化
        view.initialize(height, width)
    }

    fun onDrawFrame() {
        if (!CubismFramework.isInitialized()) {
            CubismFramework.initialize()
        }
        if (!view.isReady) return
        // 画面初期化
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glClearDepthf(1.0f)

        pal.updateTime()
        view.onDrawFrame(windowWidth, windowHeight)
    }

    fun onTouchBegan(x: Float, y: Float) {
        isCaptured = true
        view.onTouchesBegan(x, y)
    }

    fun onTouchEnd() {
        isCaptured = false
        view.onTouchesEnded()
    }

    fun onTouchMoved(x: Float, y: Float) {
        if (isCaptured) view.onTouchesMoved(x, y)
    }

    fun plugModule(jsonFilePath: String) = view.plugModel(jsonFilePath)

    fun destroy() {
        view.destroy()
        DelegatePool.removeDelegate(this)
    }

    companion object {
        private const val TAG = "Delegate"
    }
}

/**
 * Logging Function class to be registered in the CubismFramework's logging function.
 */
class PrintLogFunction : ICubismLogger {
    override fun print(message: String) {
        Log.d("Book", message)
    }
}