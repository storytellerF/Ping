package com.storyteller_f.ping.shader

import java.util.concurrent.atomic.AtomicReference

/** SurfaceTexture 的任意线程回调只发布通知；一次绘制合并消费所有已到达的帧。 */
internal class FrameSignal {
    private enum class State { IDLE, PENDING, CLOSED }
    private val state = AtomicReference(State.IDLE)

    fun offer(): Boolean = state.compareAndSet(State.IDLE, State.PENDING)
    fun consume(): Boolean = state.compareAndSet(State.PENDING, State.IDLE)
    fun close() {
        state.set(State.CLOSED)
    }
    val isClosed: Boolean get() = state.get() == State.CLOSED
}
