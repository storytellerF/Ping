package com.storyteller_f.ping.shader

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import javax.microedition.khronos.opengles.GL10

/**
 * 壁纸渲染器抽象基类，封装公共的 GL 初始化、程序编译/链接、MVP 矩阵设置以及纹理/SurfaceTexture 绑定。
 *
 * 设计原则：
 * - 统一在此处完成 Program 的编译与链接，子类只负责各版本差异化的绑定与绘制逻辑。
 * - 关闭深度测试/面剔除/混合，因为本场景仅绘制一个覆盖屏幕的 2D 矩形，不需要这些状态。
 * - 通过 `mvp` uniform 将裁剪与旋转/位移传入着色器，以纯矩阵方式实现裁剪与平移，避免依赖播放器行为。
 */
abstract class GLWallpaperRenderer(
    protected val context: Context, // Android 上下文，用于资源加载
    val vertexRes: Int, // 顶点着色器资源 id
    val fragmentRes: Int, // 片元着色器资源 id
    val version: Int // OpenGL ES 版本标记
) : GLSurfaceView.Renderer {

    private val mvpMatrix = floatArrayOf( // MVP 矩阵，初始化为单位矩阵
        1.0f, 0.0f, 0.0f, 0.0f,// 第一行
        0.0f, 1.0f, 0.0f, 0.0f,// 第二行
        0.0f, 0.0f, 1.0f, 0.0f,// 第三行
        0.0f, 0.0f, 0.0f, 1.0f// 第四行
    )

    protected val buffers by lazy { // 顶点/纹理/索引缓冲的封装
        GLBuffer()
    }

    private val textures by lazy { // 外部 OES 纹理封装
        OESTexture()
    }

    val binding by lazy { // 将 SurfaceTexture、播放器与矩阵计算关联
        SurfaceTextureBinding(mvpMatrix, textures)
    }

    protected val program by lazy { // 编译并链接着色器程序
        linkProgramGLES20(
            compileShaderResourceGLES20(
                context, GLES30.GL_VERTEX_SHADER, vertexRes // 编译顶点着色器
            ), compileShaderResourceGLES20(
                context, GLES30.GL_FRAGMENT_SHADER, fragmentRes // 编译片元着色器
            )
        )
    }
    private val mvpLocation by lazy { GLES20.glGetUniformLocation(program, "mvp") } // 获取 mvp uniform 位置

    /**
     * 设置与本场景匹配的固定 GL 状态，避免不必要的性能开销与视觉副作用。
     */
    fun initGl() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST) // 关闭深度测试（2D 覆盖不需要）
        GLES20.glDepthMask(false) // 关闭深度写入
        GLES20.glDisable(GLES20.GL_CULL_FACE) // 关闭背面剔除
        GLES20.glDisable(GLES20.GL_BLEND) // 关闭颜色混合
        mvpLocation // 触发 lazy，缓存 uniform 位置
        binding // 触发 lazy，初始化绑定对象
    }

    //生命周期函数
    override fun onSurfaceChanged(gl10: GL10, width: Int, height: Int) =
        GLES20.glViewport(0, 0, width, height) // 更新视口为新尺寸

    /**
     * 每帧：
     * - 若 `SurfaceTexture` 有新帧则更新；
     * - 清屏并使用 program；
     * - 设置 mvp 矩阵；
     * - 调用子类的具体绘制逻辑。
     */
    override fun onDrawFrame(gl: GL10?) {
        if (!binding.isReady) return // 若无新帧或矩阵正在更新则跳过
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT) // 清除颜色缓冲
        GLES20.glUseProgram(program) // 使用当前着色器程序
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvpMatrix, 0) // 上传 mvp 矩阵

        drawImage() // 调用具体版本的绘制实现
    }

    abstract fun drawImage()
    //生命周期函数
}
