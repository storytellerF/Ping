package com.storyteller_f.ping.shader

/** 平台输出适配器；所有生产者操作只由 OutputSession 在播放器线程调用。 */
internal interface ProducerOutput {
    val sourceId: Long
    val isValid: Boolean
    fun acquire(): Boolean
    fun finishProducerUse()
}

/**
 * 主线程所有。一次交接包含取得、绑定和归还，关闭后仍接收并释放晚到输出。
 * attach 抛出异常前必须停止可能已连接到候选输出的生产者，会话随后归还该输出。
 */
internal class OutputSession<T : ProducerOutput>(private val attach: (T?) -> Unit) {
    private sealed interface State<out T> {
        data class Open<T>(val sourceId: Long, val output: T?) : State<T>
        data object Closed : State<Nothing>
    }
    private var state: State<T> = State.Open(0, null)
    val current: T? get() = (state as? State.Open)?.output
    val hasCurrentSource: Boolean get() = (state as? State.Open)?.let {
        it.output?.let { output -> output.sourceId == it.sourceId && output.isValid } == true
    } == true

    fun selectSource(sourceId: Long) {
        val open = state as? State.Open ?: return
        state = open.copy(sourceId = sourceId)
    }

    fun offer(output: T) {
        val open = state as? State.Open
        if (open == null || output.sourceId != open.sourceId || !output.acquire()) {
            output.finishProducerUse()
            return
        }
        try {
            attach(output)
        } catch (failure: Throwable) {
            output.finishProducerUse()
            throw failure
        }
        state = open.copy(output = output)
        open.output?.finishProducerUse()
    }

    fun ifCurrent(output: T, action: () -> Unit) {
        if (current === output) action()
    }

    fun close() {
        if (state is State.Closed) return
        val previous = current
        try {
            attach(null)
        } finally {
            state = State.Closed
            previous?.finishProducerUse()
        }
    }
}
