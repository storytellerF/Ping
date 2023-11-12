package com.storyteller_f.ping.cubism

class Pal {
    private val systemNanoTime: Long
        get() = System.nanoTime()
    private var _lastNanoTime = 0L
    private var _deltaNanoTime = 0L

    // デルタタイムの更新
    fun updateTime() {
        val sCurrentFrame = systemNanoTime
        _deltaNanoTime = sCurrentFrame - _lastNanoTime
        _lastNanoTime = sCurrentFrame
    }

    val deltaTime: Float
        // デルタタイム(前回フレームとの差分)を取得する
        get() =// ナノ秒を秒に変換
            _deltaNanoTime.toFloat() / 1000000000.0f

}
