package com.storyteller_f.ping.shader

/**
 * 所有方法由播放器线程调用。acquire 原子取得输出所有权，attach 返回后才释放旧输出。
 * 接收者负责释放每个交接，包括关闭后晚到或未能取得所有权的交接。
 */
internal class OutputHandoff<T : Any>(
    private val acquire: (T) -> Boolean,
    private val attach: (T?) -> Unit,
    private val release: (T) -> Unit,
) {
    private sealed interface State<out T> {
        data class Open<T>(val output: T?) : State<T>
        data object Closed : State<Nothing>
    }
    private var state: State<T> = State.Open(null)
    val current: T? get() = (state as? State.Open)?.output

    fun offer(output: T) {
        val previous = state
        if (previous is State.Closed || !acquire(output)) {
            release(output)
            return
        }
        attach(output)
        state = State.Open(output)
        (previous as State.Open).output?.let(release)
    }

    fun revoke(output: T) {
        if (current !== output) return
        attach(null)
        state = State.Open(null)
        release(output)
    }

    fun close() {
        val previous = current
        if (state is State.Closed) return
        attach(null)
        state = State.Closed
        previous?.let(release)
    }
}
