package com.storyteller_f.ping.shader

import android.opengl.Matrix
import android.view.Surface

/** 每个 EGL context 创建一份；除帧通知外，仅 GL 线程可以访问。 */
internal class SurfaceTextureBinding(
    val sourceId: Long,
    val contextId: Long,
    requestRender: () -> Unit,
    scheduleCleanup: () -> Unit,
) {
    private val texture = OESTexture()
    private val frames = FrameSignal()
    private val surfaceTexture = texture.build().apply {
        setOnFrameAvailableListener {
            if (frames.offer()) requestRender()
        }
    }
    val output = VideoOutput(sourceId, Surface(surfaceTexture), scheduleCleanup)
    val mvpMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private sealed interface Frame {
        data object Waiting : Frame
        data class Displayed(val video: VideoMatrix) : Frame
    }
    private var frame: Frame = Frame.Waiting
    private var bufferVideo: VideoMatrix? = null

    /** 矩阵只使用已锁存帧的尺寸；其他来源的参数不能重新解释保留中的旧帧。 */
    fun render(parameters: RenderParameters): Boolean {
        val incoming = parameters.video?.takeIf { parameters.sourceId == sourceId }
        if (incoming != null) {
            if (bufferVideo != incoming) {
                surfaceTexture.setDefaultBufferSize(incoming.realWidth, incoming.realHeight)
                bufferVideo = incoming
            }
            if (frames.consume()) {
                surfaceTexture.updateTexImage()
                frame = Frame.Displayed(incoming)
            }
        }
        val video = (frame as? Frame.Displayed)?.video ?: return false
        Matrix.setIdentityM(mvpMatrix, 0)
        val width = parameters.screenWidth
        val height = parameters.screenHeight
        if (width <= 0 || height <= 0) return false
        val wider = video.realWidth.toFloat() / video.realHeight >= width.toFloat() / height
        val scale = if (wider) {
            video.realWidth.toFloat() * height / (width.toFloat() * video.realHeight)
        } else {
            video.realHeight.toFloat() * width / (height.toFloat() * video.realWidth)
        }
        Matrix.scaleM(mvpMatrix, 0, if (wider) scale else 1f, if (wider) 1f else scale, 1f)
        if (video.rotation % 360 != 0) {
            Matrix.rotateM(mvpMatrix, 0, -video.rotation.toFloat(), 0f, 0f, 1f)
        }
        val maxOffset = (1f - 1f / scale) / 2f
        Matrix.translateM(
            mvpMatrix, 0,
            if (wider) parameters.offset.xOffset * maxOffset else 0f,
            if (wider) 0f else parameters.offset.yOffset * maxOffset, 0f
        )
        texture.bind()
        return true
    }

    fun retire() {
        output.retire()
        frames.close()
        surfaceTexture.setOnFrameAvailableListener(null)
    }

    fun release(deleteTexture: Boolean) {
        check(output.canRelease) { "Producer must detach before releasing SurfaceTexture" }
        surfaceTexture.release()
        if (deleteTexture) texture.release()
    }
}

/** GL 线程生成、主线程持有并释放 Surface。失效后的交接必须丢弃。 */
internal data class OutputSnapshot(val sourceId: Long, val valid: Boolean, val surfaceReleased: Boolean)

internal fun interface OutputInspection {
    fun snapshot(): OutputSnapshot
}

internal class VideoOutput(
    override val sourceId: Long,
    private val surface: Surface,
    private val scheduleCleanup: () -> Unit,
) : ProducerOutput, OutputInspection {
    private val lease = OutputLease()
    private var returned = false // 播放器线程所有。
    override val isValid: Boolean get() = lease.isValid
    val canRelease: Boolean get() = lease.canRelease
    override fun acquire(): Boolean = lease.acquire()
    fun retire() = lease.retire() // GL 线程撤销。
    /** 在播放器线程连接生产者；归还和释放仍由输出会话管理。 */
    fun connectTo(attach: (Surface) -> Unit) = attach(surface)
    override fun snapshot() = OutputSnapshot(sourceId, isValid, !surface.isValid)

    override fun finishProducerUse() {
        if (returned) return
        surface.release()
        returned = true
        lease.detach()
        scheduleCleanup()
    }
}

internal data class RenderParameters(
    val sourceId: Long = 0,
    val video: VideoMatrix? = null,
    val offset: Offset = Offset(),
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
)
