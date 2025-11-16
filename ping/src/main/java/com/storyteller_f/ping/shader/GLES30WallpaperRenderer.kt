package com.storyteller_f.ping.shader

import android.content.Context
import android.opengl.GLES30
import com.storyteller_f.ping.R
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 面向 OpenGL ES 3.0 的壁纸渲染器。
 *
 * 设计要点：
 * - 使用 VAO（顶点数组对象）一次性记录 attribute 绑定与 EBO 绑定，减少每帧调用与状态错误。
 * - attribute 位置固定为 0/1，需与着色器 layout 或默认绑定保持一致，便于快速绑定。
 */
internal class GLES30WallpaperRenderer(context: Context) :
    GLWallpaperRenderer(context, R.raw.vertex_30, R.raw.fragment_30, 3) { // 指定 ES3 的顶点/片元着色器与版本
    private val positionLocation = 0 // attribute 位置：顶点坐标
    private val texCoordinationLocation = 1 // attribute 位置：纹理坐标
    private val vertexArrays = IntArray(1) // VAO 句柄容器

    /**
     * 创建并配置 VAO，记录 VBO/EBO 的绑定关系，之后绘制只需绑定 VAO。
     */
    override fun onSurfaceCreated(gl10: GL10, eglConfig: EGLConfig) { // Surface 创建回调
        initGl() // 初始化固定 GL 状态

        //VAO 顶点数组对象
        GLES30.glGenVertexArrays(vertexArrays.size, vertexArrays, 0) // 生成 VAO 句柄数组
        GLES30.glBindVertexArray(vertexArrays[0]) // 绑定 VAO，使后续的绑定记录到该 VAO
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[2]) // 在 VAO 下绑定 EBO，索引绑定随 VAO 记录

        bindData(buffers[0], positionLocation) // 绑定顶点坐标 VBO 并配置 attribute 位置 0
        bindData(buffers[1], texCoordinationLocation) // 绑定纹理坐标 VBO 并配置 attribute 位置 1

        GLES30.glBindVertexArray(0) // 解绑 VAO，避免后续误改其状态
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // 设置清屏颜色为不透明黑色
    }

    /**
     * 绘制时仅绑定 VAO 并调用 `glDrawElements`，最小化状态管理成本。
     */
    override fun drawImage() { // 每帧绘制逻辑
        GLES30.glBindVertexArray(vertexArrays[0]) // 绑定 VAO，使用预记录的属性/EBO
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES, // 三角形绘制模式
            6,//组成一个矩形的索引数（两个三角形）
            GLES30.GL_UNSIGNED_INT, // 索引类型
            0 // 从索引缓冲起始位置开始
        )
        GLES30.glBindVertexArray(0) // 解绑 VAO，清理状态

        GLES30.glUseProgram(0) // 解绑程序对象
    }

    companion object
}
