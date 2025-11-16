package com.storyteller_f.ping.shader

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * 将 `SurfaceTexture`、播放器与渲染矩阵（MVP）串联起来的桥接类：
 * - 负责创建/销毁 `SurfaceTexture` 并将其绑定给播放器；
 * - 根据视频真实尺寸与屏幕尺寸计算裁剪与平移所需的 MVP 矩阵；
 * - 通过 `isReady` 在渲染线程拉取新帧并驱动纹理更新，规避部分设备上回调停止的问题。
 *
 * 设计动机：
 * - 播放器的居中/裁剪行为不一致，统一由我们在 GL 侧用矩阵解决，保证可控与跨设备一致性。
 * - 通过 updated/rendered 计数主动轮询 `updateTexImage`，修复 `OnFrameAvailableListener` 失效的机型。
 */
class SurfaceTextureBinding(private val mvpMatrix: FloatArray, private val textures: OESTexture) {
    private var surfaceTexture: SurfaceTexture? = null // 当前使用的 SurfaceTexture

    // Fix bug like https://stackoverflow.com/questions/14185661/surfacetexture-onframeavailablelistener-stops-being-called
    private var updatedFrame: Long = 0 // 收到的新帧计数
    private var renderedFrame: Long = 0 // 已渲染的帧计数

    private var screenSize: Size? = null // 屏幕尺寸
    private var videoMatrix: VideoMatrix? = null // 视频尺寸与旋转信息
    private var offset: Offset = Offset() // 当前滚动偏移

    private var maxOffset: Offset? = null // 最大允许偏移

    fun setScreenSize(size: Size) {
        Log.d(TAG, "setScreenSize() called with: size = $size") // 记录日志
        val oldSize = this.screenSize // 读取旧尺寸
        if (oldSize != size) { // 尺寸变化触发更新
            this.screenSize = size // 保存新尺寸
            updateMaxOffset() // 重算最大偏移
            updateMatrix() // 重算 MVP 矩阵
        }
    }

    fun setVideoMatrix(videoMatrix: VideoMatrix, player: MediaPlayer) {
        Log.d(TAG, "setVideoMatrix() called with: matrix = $videoMatrix") // 记录日志
        // MediaMetadataRetriever always give us raw width and height and won't rotate them.
        // So we rotate them by ourselves.
        val oldVideoMatrix = this.videoMatrix // 旧视频矩阵
        if (oldVideoMatrix != videoMatrix) { // 发生变化时更新
            this.videoMatrix = videoMatrix // 保存新矩阵
            Log.i(TAG, "setVideoMatrix: new matrix $videoMatrix") // 打印信息日志
            updateMaxOffset() // 重算最大偏移
            updateMatrix() // 重算 MVP 矩阵
            attachTo(player) // 重新创建 SurfaceTexture 并绑定播放器
        }
    }

    fun setOffset(offset: Offset) {
        Log.d(TAG, "setOffset() called with: offset = $offset $maxOffset") // 记录日志
        val maxOffset1 = maxOffset ?: return // 若尚未计算最大偏移则返回
        val oldXOffset = this.offset // 读取旧偏移
        val rectified = Offset((offset.xOffset * maxOffset1.xOffset).let { // 修正 X 偏移值
            if (it < 0.001) 0f else it // 过小值置零
        }, (offset.yOffset * maxOffset1.yOffset).let { // 修正 Y 偏移值
            if (it < 0.001) 0f else it // 过小值置零
        })
        if (rectified != oldXOffset) { // 有实际变化
            this.offset = rectified // 更新偏移
            Log.i(TAG, "setOffset: new offset $rectified") // 信息日志
            updateMatrix() // 更新矩阵
        }
    }

    private fun attachTo(player: MediaPlayer) {
        // 重建 `SurfaceTexture` 并与播放器建立新的 `Surface`，确保分辨率/旋转更新后正确输出到纹理。
        createSurfaceTexture()
        player.setSurface(Surface(surfaceTexture))
    }

    private fun createSurfaceTexture() {
        surfaceTexture?.release() // 释放旧纹理
        updatedFrame = 0 // 重置计数
        renderedFrame = 0 // 重置计数
        surfaceTexture = textures.build().apply { // 使用 OES 纹理创建 SurfaceTexture
            val videoMatrix1 = videoMatrix!! // 读取当前视频矩阵
            // 设置缓冲区尺寸为视频的真实宽高（考虑旋转后），避免采样拉伸或变形。
            setDefaultBufferSize(videoMatrix1.realWidth, videoMatrix1.realWidth) // 配置缓冲尺寸
            setOnFrameAvailableListener { ++updatedFrame } // 新帧回调递增计数
        }
    }

    private fun updateMaxOffset() {
        Log.d(TAG, "updateMaxOffset() called $videoMatrix $screenSize ${Thread.currentThread()}") // 打印当前状态
        val matrix = videoMatrix ?: return // 视频矩阵未就绪
        val size = screenSize ?: return // 屏幕尺寸未就绪
        val videoMoreWidth = getVideoMoreWidth(matrix, size) // 判断是否更宽
        maxOffset = if (videoMoreWidth) { // 横向裁剪
            val screenWidthExpected = matrix.realWidth.toFloat() * size.height / matrix.realHeight // 期望屏幕宽度
            val widthOffset = 1.0f - size.width.toFloat() / screenWidthExpected // 多余比例
            Offset(abs(widthOffset) / 2) // 左右平分
        } else { // 纵向裁剪
            val screenHeightExpected = matrix.realHeight.toFloat() * size.width / matrix.realWidth // 期望屏幕高度
            val heightOffset = 1.0f - size.height.toFloat() / screenHeightExpected // 多余比例
            Offset(yOffset = abs(heightOffset) / 2) // 上下平分
        }

        Log.i(TAG, "updateMaxOffset: $maxOffset") // 打印结果
    }

    private var updatingMatrix = AtomicBoolean(false) // 是否正在更新矩阵，防止并发
    private fun updateMatrix() {
        Log.d(
            TAG, // 日志标签
            "updateMatrix() called $videoMatrix $screenSize $offset ${Thread.currentThread()}" // 当前状态
        )
        val matrix = videoMatrix ?: return // 数据未就绪
        val size = screenSize ?: return // 数据未就绪
        if (!updatingMatrix.compareAndSet(false, true)) { // 置位失败说明已有更新在进行
            return
        }
        val offset1 = offset // 缓存偏移
        val videoMoreWidth = getVideoMoreWidth(matrix, size) // 判断宽高比关系
        val videoRotation = matrix.rotation // 读取旋转元数据
        // Players are buggy and unclear, so we do crop by ourselves.
        // Start with an identify matrix.
        for (i in 0..15) mvpMatrix[i] = 0.0f // 清零矩阵
        mvpMatrix[15] = 1.0f // w 分量为 1
        mvpMatrix[10] = mvpMatrix[15] // z 分量为 1
        mvpMatrix[5] = mvpMatrix[10] // y 分量为 1
        mvpMatrix[0] = mvpMatrix[5] // x 分量为 1
        // OpenGL model matrix: scaling, rotating, translating.
        if (videoMoreWidth) {
            Log.d(TAG, "updateMatrix: crop x") // 横向裁剪
            // Treat video and screen width as 1, and compare width to scale.
            val widthRatio = matrix.realWidth / getFitVideoWidth(size, matrix) // 计算缩放比例
            Matrix.scaleM(mvpMatrix, 0, widthRatio, 1f, 1f) // 按 X 缩放
            // Some video recorder save video frames in direction differs from recoring,
            // and add a rotation metadata. Need to detect and rotate them.
            if (videoRotation % 360 != 0) { // 存在旋转元数据
                Matrix.rotateM(mvpMatrix, 0, -videoRotation.toFloat(), 0f, 0f, 1f) // 纠正旋转
            }
            Matrix.translateM(mvpMatrix, 0, offset1.xOffset, 0f, 0f) // 按 X 平移
        } else {
            Log.d(TAG, "updateMatrix: crop y") // 纵向裁剪
            // Treat video and screen height as 1, and compare height to scale.
            val heightRatio = matrix.realHeight / getFitVideoHeight(size, matrix) // 计算缩放比例
            Matrix.scaleM(mvpMatrix, 0, 1f, heightRatio, 1f) // 按 Y 缩放
            // Some video recorder save video frames in direction differs from recoring,
            // and add a rotation metadata. Need to detect and rotate them.
            if (videoRotation % 360 != 0) { // 存在旋转元数据
                Matrix.rotateM(mvpMatrix, 0, -videoRotation.toFloat(), 0f, 0f, 1f) // 纠正旋转
            }
            Matrix.translateM(mvpMatrix, 0, 0f, offset1.yOffset, 0f) // 按 Y 平移
        }
        updatingMatrix.set(false) // 更新结束
    }

    /**
     * 视频比屏幕更宽一些，所以根据屏幕尺寸和视频高度重新确定视频宽度。
     */
    private fun getFitVideoWidth(size: Size, matrix: VideoMatrix) =
        size.width.toFloat() * matrix.realHeight / size.height // 按高度适配推导视频宽度

    /**
     * 视频比屏幕更高一些，所以根据屏幕尺寸和视频宽度重新确定通过视频高度。
     */
    private fun getFitVideoHeight(size: Size, matrix: VideoMatrix) =
        size.height.toFloat() * matrix.realWidth / size.width // 按宽度适配推导视频高度

    /**
     * @return 如果为true，视频比屏幕更加宽。完全覆盖屏幕之后左右两边会有空余，这部分空余用于
     * 桌面滚动。反之亦然。
     */
    private fun getVideoMoreWidth(videoMatrix1: VideoMatrix, size: Size): Boolean {
        val videoRatio1 = videoMatrix1.let { // 视频宽高比
            it.realWidth.toFloat() / it.realHeight
        }
        val screenRatio1 = size.let { // 屏幕宽高比
            it.width.toFloat() / it.height
        }
        return videoRatio1 >= screenRatio1 // 视频更宽返回 true
    }

    val isReady: Boolean get() { // 渲染是否就绪
        if (updatingMatrix.get()) return false // 矩阵更新中
        val texture = surfaceTexture ?: return false // 纹理尚未创建
        if (renderedFrame < updatedFrame) { // 有新帧
            texture.updateTexImage() // 拉取最新帧
            ++renderedFrame // 标记已渲染
        }
        return true // 可以渲染
    }
    
    companion object {
        private const val TAG = "SurfaceTextureBinding" // 日志标签
    }
}