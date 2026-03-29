package com.storyteller_f.ping.cubism

import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.math.CubismViewMatrix
import kotlin.math.abs

class View(delegate: Delegate) {

    private val live2DManager = Live2DManager(delegate.context, this, delegate.pal)
    val isReady get() = live2DManager.isReady

    private val deviceToScreen = CubismMatrix44.create() // デバイス座標からスクリーン座標に変換するための行列
    private val viewMatrix = CubismViewMatrix() // 画面表示の拡縮や移動の変換を行う行列

    /**
     * レンダリングターゲットのクリアカラー
     */
    private val clearColor = floatArrayOf(1f, 1f, 1f, 0f)

    private val touchManager = TouchManager()

    // ビューを初期化する
    fun initialize(height: Int, width: Int) {
        val ratio = width.toFloat() / height.toFloat()
        val left = -ratio
        val bottom: Float = LogicalView.LEFT.value
        val top: Float = LogicalView.RIGHT.value

        // デバイスに対応する画面範囲。Xの左端、Xの右端、Yの下端、Yの上端
        viewMatrix.setScreenRect(left, ratio, bottom, top)
        viewMatrix.scale(Scale.DEFAULT.value, Scale.DEFAULT.value)

        // 単位行列に初期化
        deviceToScreen.loadIdentity()
        if (width > height) {
            val screenW = abs((ratio - left).toDouble()).toFloat()
            deviceToScreen.scaleRelative(screenW / width, -screenW / width)
        } else {
            val screenH = abs((top - bottom).toDouble()).toFloat()
            deviceToScreen.scaleRelative(screenH / height, -screenH / height)
        }
        deviceToScreen.translateRelative(-width * 0.5f, -height * 0.5f)

        // 表示範囲の設定
        viewMatrix.maxScale = Scale.MAX.value // 限界拡大率
        viewMatrix.minScale = Scale.MIN.value // 限界縮小率

        // 表示できる最大範囲
        viewMatrix.setMaxScreenRect(
            MaxLogicalView.LEFT.value,
            MaxLogicalView.RIGHT.value,
            MaxLogicalView.BOTTOM.value,
            MaxLogicalView.TOP.value
        )
    }

    // 描画する
    fun onDrawFrame(width: Int, height: Int) {
        // モデルの描画
        live2DManager.onDrawFrame(
            width,
            height
        )
    }

    /**
     * タッチされたときに呼ばれる
     *
     * @param pointX スクリーンX座標
     * @param pointY スクリーンY座標
     */
    fun onTouchesBegan(pointX: Float, pointY: Float) {
        touchManager.touchesBegan(pointX, pointY)
    }

    /**
     * タッチしているときにポインターが動いたら呼ばれる
     *
     * @param pointX スクリーンX座標
     * @param pointY スクリーンY座標
     */
    fun onTouchesMoved(pointX: Float, pointY: Float) {
        val viewX = transformViewX(touchManager.lastX)
        val viewY = transformViewY(touchManager.lastY)
        touchManager.touchesMoved(pointX, pointY)
        live2DManager.onDrag(viewX, viewY)
    }

    /**
     * タッチが終了したら呼ばれる
     *
     */
    fun onTouchesEnded() {
        // タッチ終了
        live2DManager.onDrag(0.0f, 0.0f)

        // シングルタップ
        // 論理座標変換した座標を取得
        val x: Float = deviceToScreen.transformX(touchManager.lastX)
        // 論理座標変換した座標を取得
        val y: Float = deviceToScreen.transformY(touchManager.lastY)
        live2DManager.onTap(x, y)
    }

    /**
     * X座標をView座標に変換する
     *
     * @param deviceX デバイスX座標
     * @return ViewX座標
     */
    private fun transformViewX(deviceX: Float): Float {
        // 論理座標変換した座標を取得
        val screenX: Float = deviceToScreen.transformX(deviceX)
        // 拡大、縮小、移動後の値
        return viewMatrix.invertTransformX(screenX)
    }

    /**
     * Y座標をView座標に変換する
     *
     * @param deviceY デバイスY座標
     * @return ViewY座標
     */
    private fun transformViewY(deviceY: Float): Float {
        // 論理座標変換した座標を取得
        val screenY: Float = deviceToScreen.transformY(deviceY)
        // 拡大、縮小、移動後の値
        return viewMatrix.invertTransformX(screenY)
    }

    /**
     * レンダリング先をデフォルト以外に切り替えた際の背景クリア色設定
     *
     * @param r 赤(0.0~1.0)
     * @param g 緑(0.0~1.0)
     * @param b 青(0.0~1.0)
     */
    fun setRenderingTargetClearColor(r: Float, g: Float, b: Float) {
        clearColor[0] = r
        clearColor[1] = g
        clearColor[2] = b
    }

    fun destroy() {
        live2DManager.releaseCurrentModule()
    }

    fun plugModel(jsonFilePath: String) {
        live2DManager.plugModel(jsonFilePath)
    }

}
