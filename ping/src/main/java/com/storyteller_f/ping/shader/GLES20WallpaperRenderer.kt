package com.storyteller_f.ping.shader

import android.content.Context
import android.opengl.GLES20
import com.storyteller_f.ping.R

/**
 * 面向 OpenGL ES 2.0 的壁纸渲染器。
 *
 * 设计差异：
 * - ES 2.0 无 VAO，attribute 位置通过 `glGetAttribLocation` 动态查询。
 * - 绑定与绘制时需要手动启用/禁用 attribute，显式管理状态以避免串场。
 */
internal class GLES20WallpaperRenderer(context: Context) :
    GLWallpaperRenderer(
        context, // 上下文用于加载资源
        R.raw.vertex_20, // 顶点着色器资源 id（ES 2.0 版本）
        R.raw.fragment_20, // 片元着色器资源 id（ES 2.0 版本）
        2 // 指定使用的 GL 版本号
    ) {
    private var positionLocation = -1
    private var texCoordinationLocation = -1

    /**
     * 初始化 GL 状态与着色器变量位置。
     */
    override fun initializeGeometry() {
        positionLocation = GLES20.glGetAttribLocation(program, "in_position")
        texCoordinationLocation = GLES20.glGetAttribLocation(program, "in_texture_coordination")
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // 设置清屏颜色为不透明黑色
    }

    /**
     * 绑定顶点/纹理坐标缓冲并使用 EBO 绘制两个三角形构成的矩形。
     * ES 2.0 需要在绘制后手动关闭 attribute 并解绑，保持渲染状态干净。
     */
    override fun drawImage() {
        bindData(buffers[0], positionLocation) // 绑定位置 VBO 到 attribute 位置
        bindData(buffers[1], texCoordinationLocation) // 绑定纹理坐标 VBO 到 attribute 位置

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[2]) // 绑定索引缓冲
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0) // 按索引绘制两个三角形
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0) // 解绑索引缓冲，清理状态

        GLES20.glDisableVertexAttribArray(texCoordinationLocation) // 关闭纹理坐标 attribute
        GLES20.glDisableVertexAttribArray(positionLocation) // 关闭位置 attribute
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0) // 解绑顶点缓冲目标

        GLES20.glUseProgram(0) // 解绑着色器程序
    }

    companion object
}