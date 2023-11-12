package com.storyteller_f.ping.cubism

/**
 * タッチマネージャー
 */
class TouchManager {
    /**
     * タッチ開始時のxの値
     */
    private var startX = 0f

    /**
     * タッチ開始時のyの値
     */
    private var startY = 0f

    /**
     * シングルタッチ時のxの値
     */
    var lastX = 0f
        private set

    /**
     * シングルタッチ時のyの値
     */
    var lastY = 0f
        private set

    /**
     * 2本以上でタッチしたときの指の距離
     */
    private var lastTouchDistance = 0f

    /**
     * シングルタッチ時はtrue
     */
    private var isTouchSingle = false

    /**
     * フリップが有効かどうか
     */
    private var isFlipAvailable = false

    /**
     * タッチ開始時のイベント
     *
     * @param deviceX タッチした画面のxの値
     * @param deviceY タッチした画面のyの値
     */
    fun touchesBegan(deviceX: Float, deviceY: Float) {
        lastX = deviceX
        lastY = deviceY
        startX = deviceX
        startY = deviceY
        lastTouchDistance = -1.0f
        isFlipAvailable = true
        isTouchSingle = true
    }

    /**
     * ドラッグ時のイベント
     *
     * @param deviceX タッチした画面のxの値
     * @param deviceY タッチした画面のyの値
     */
    fun touchesMoved(deviceX: Float, deviceY: Float) {
        lastX = deviceX
        lastY = deviceY
        lastTouchDistance = -1.0f
        isTouchSingle = true
    }

    // ----- getter methods -----
}
