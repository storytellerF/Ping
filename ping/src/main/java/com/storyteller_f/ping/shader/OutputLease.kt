package com.storyteller_f.ping.shader

import java.util.concurrent.atomic.AtomicReference

/** GL 线程撤销输出与播放器线程取得/归还生产者所有权之间的原子交接。 */
internal class OutputLease {
    private enum class State { AVAILABLE, ATTACHED, DETACHED, RETIRING, RETIRED }
    private val state = AtomicReference(State.AVAILABLE)

    fun acquire(): Boolean = state.compareAndSet(State.AVAILABLE, State.ATTACHED)
    val isValid: Boolean get() = state.get().let { it == State.AVAILABLE || it == State.ATTACHED }
    val canRelease: Boolean get() = state.get() == State.RETIRED

    fun retire() {
        state.updateAndGet {
            when (it) {
                State.AVAILABLE, State.DETACHED -> State.RETIRED
                State.ATTACHED -> State.RETIRING
                State.RETIRING, State.RETIRED -> it
            }
        }
    }

    /** 必须在 setSurface(null/新输出) 返回且 Surface 已释放后调用。 */
    fun detach() {
        state.updateAndGet {
            when (it) {
                State.AVAILABLE, State.ATTACHED -> State.DETACHED
                State.RETIRING -> State.RETIRED
                State.DETACHED, State.RETIRED -> it
            }
        }
    }
}
