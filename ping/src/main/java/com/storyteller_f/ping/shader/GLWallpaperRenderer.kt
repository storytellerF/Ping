package com.storyteller_f.ping.shader

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** 参数以不可变快照跨线程传递；所有矩阵、GL 句柄和 SurfaceTexture 归 GL 线程所有。 */
internal abstract class GLWallpaperRenderer(
    protected val context: Context,
    private val vertexRes: Int,
    private val fragmentRes: Int,
    val version: Int,
) : GLSurfaceView.Renderer {
    private val parameters = MutableStateFlow(RenderParameters())
    private var binding: SurfaceTextureBinding? = null
    private var displayed: SurfaceTextureBinding? = null
    private var contextId = 0L
    private var hasContext = false
    private val rendererState = MutableStateFlow(RendererSnapshot())
    val inspection = rendererState.asStateFlow()
    private val retiredBindings = mutableListOf<SurfaceTextureBinding>()
    protected var program = 0
        private set
    protected lateinit var buffers: GLBuffer
        private set
    private var mvpLocation = -1

    // 在 setRenderer 前安装；后续不再修改。
    var requestRender: () -> Unit = {}
    var scheduleCleanup: () -> Unit = {}
    var onOutputCreated: (VideoOutput) -> Unit = {}
    var onOutputDestroyed: (VideoOutput) -> Unit = {}

    fun selectSource(sourceId: Long) {
        parameters.update { it.copy(sourceId = sourceId, video = null) }
        requestRender()
    }

    fun setVideoMatrix(sourceId: Long, video: VideoMatrix) {
        if (video.width <= 0 || video.height <= 0) return
        parameters.update { if (it.sourceId == sourceId) it.copy(video = video) else it }
        requestRender()
    }

    fun setOffset(offset: Offset) {
        parameters.update { it.copy(offset = offset) }
        requestRender()
    }

    final override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        // onSurfaceCreated 代表一个新 context，绝不能复用旧 context 的数值句柄。
        check(binding == null)
        contextId++
        hasContext = true
        val vertex = compileShaderResourceGLES20(context, GLES20.GL_VERTEX_SHADER, vertexRes)
        try {
            val fragment = compileShaderResourceGLES20(context, GLES20.GL_FRAGMENT_SHADER, fragmentRes)
            try {
                program = linkProgramGLES20(vertex, fragment)
            } finally {
                GLES20.glDeleteShader(fragment)
            }
        } finally {
            GLES20.glDeleteShader(vertex)
        }
        mvpLocation = GLES20.glGetUniformLocation(program, "mvp")
        buffers = GLBuffer()
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        initializeGeometry()
        ensureSource(parameters.value)
        publishInspection()
    }

    final override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        parameters.update { it.copy(screenWidth = width, screenHeight = height) }
    }

    private fun ensureSource(parameters: RenderParameters) {
        if (parameters.sourceId == 0L || binding?.sourceId == parameters.sourceId) return
        binding?.let(::retire)
        val created = SurfaceTextureBinding(parameters.sourceId, contextId, requestRender, scheduleCleanup)
        binding = created
        onOutputCreated(created.output)
    }

    final override fun onDrawFrame(gl: GL10?) {
        if (!hasContext) return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val parameters = parameters.value
        ensureSource(parameters)
        val candidate = binding
        val current = if (candidate?.render(parameters) == true) {
            displayed = candidate
            candidate
        } else {
            displayed?.takeIf { it.render(parameters) }
        }
        releaseRetiredOutputs()
        if (current == null) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, current.mvpMatrix, 0)
        drawImage()
    }

    private fun retire(binding: SurfaceTextureBinding) {
        if (binding in retiredBindings) return
        binding.retire()
        retiredBindings += binding
        onOutputDestroyed(binding.output)
    }

    /**
     * EGLContextFactory 在 GL 线程、销毁 context 前调用。此时 EGL 已不再 current，
     * 只释放非 GL 对象；program/VBO/EBO/OES/VAO 随紧接着的 eglDestroyContext 一起释放。
     * 旧输出先失效，等待播放器线程完成解绑；不可提前废弃仍被解码器使用的生产队列。
     */
    fun onContextDestroyed() {
        listOfNotNull(binding, displayed).distinct().forEach(::retire)
        binding = null
        displayed = null
        hasContext = false
        program = 0
        mvpLocation = -1
        releaseRetiredOutputs()
    }

    /** 可在没有当前 EGL context 时调用，但始终由 GLSurfaceView 的 GL 线程执行。 */
    fun releaseRetiredOutputs() {
        val iterator = retiredBindings.iterator()
        while (iterator.hasNext()) {
            val retired = iterator.next()
            if (retired !== displayed && retired.output.canRelease) {
                retired.release(deleteTexture = hasContext && retired.contextId == contextId)
                iterator.remove()
            }
        }
        publishInspection()
    }

    private fun publishInspection() {
        rendererState.value = RendererSnapshot(
            contextId, hasContext, binding?.sourceId, displayed?.sourceId, retiredBindings.size,
        )
    }

    /** 播放器线程关闭交接后、GL 线程退出前完成清理，包括已暂停且没有 context 的情况。 */
    fun onRendererStopped() {
        onContextDestroyed()
        check(retiredBindings.isEmpty()) { "Wallpaper producer still owns a retired output" }
    }

    protected abstract fun initializeGeometry()
    protected abstract fun drawImage()
}

internal data class RendererSnapshot(
    val contextId: Long = 0,
    val hasContext: Boolean = false,
    val pendingSource: Long? = null,
    val displayedSource: Long? = null,
    val retiredOutputs: Int = 0,
)
