package com.storyteller_f.ping.shader

import android.opengl.Matrix
import android.view.Surface

/** 每个 EGL context 创建一份；除帧通知外，仅 GL 线程可以访问。 */
internal class SurfaceTextureBinding(requestRender: () -> Unit) {
    private val texture = OESTexture()
    private val frames = FrameSignal()
    private val surfaceTexture = texture.build().apply {
        setOnFrameAvailableListener {
            if (frames.offer()) requestRender()
        }
    }
    val output = VideoOutput(Surface(surfaceTexture))
    val mvpMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private var parameters: RenderParameters? = null
    private var hasFrame = false

    fun update(parameters: RenderParameters) {
        if (this.parameters == parameters) return
        if (this.parameters?.video != parameters.video) {
            parameters.video?.let {
                surfaceTexture.setDefaultBufferSize(it.realWidth, it.realHeight)
            }
        }
        this.parameters = parameters
        Matrix.setIdentityM(mvpMatrix, 0)
        val video = parameters.video ?: return
        val width = parameters.screenWidth
        val height = parameters.screenHeight
        if (width <= 0 || height <= 0) return
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
    }

    fun latchFrame(): Boolean {
        if (frames.consume()) {
            surfaceTexture.updateTexImage()
            hasFrame = true
        }
        texture.bind()
        return hasFrame
    }

    fun retire() {
        output.retire()
        frames.close()
        surfaceTexture.setOnFrameAvailableListener(null)
    }

    fun release() {
        check(output.canRelease) { "Producer must detach before releasing SurfaceTexture" }
        surfaceTexture.release()
    }
}

/** GL 线程生成、主线程持有并释放 Surface。失效后的交接必须丢弃。 */
internal class VideoOutput(val surface: Surface) {
    private val lease = OutputLease()
    val isValid: Boolean get() = lease.isValid
    val canRelease: Boolean get() = lease.canRelease
    fun acquire(): Boolean = lease.acquire()
    fun retire() = lease.retire()
    fun detach() = lease.detach()
}

internal data class RenderParameters(
    val video: VideoMatrix? = null,
    val offset: Offset = Offset(),
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
)
