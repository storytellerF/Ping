package com.storyteller_f.ping.shader

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLES30

const val BYTES_PER_FLOAT = 4 // 单个 Float 的字节数
const val BYTES_PER_INT = 4 // 单个 Int 的字节数

/**
 * 获取视频第一帧（关键帧或最近的帧），常用于生成预览缩略图。
 *
 * 说明：
 * - `getFrameAtTime(-1)` 让框架选择一个合适的时间点（通常是第一关键帧）。
 * - 捕获并吞掉 `RuntimeException`，避免上层因为个别媒体格式异常而崩溃。
 * - 使用 `finally` 安全释放资源，防止 fd 泄漏。
 */
fun Context.firstFrame(uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever() // 创建元数据检索器

    return try {
        retriever.setDataSource(this, uri) // 设置数据源为指定 Uri
        retriever.getFrameAtTime(-1) // 获取第一帧（或最近关键帧）
    } catch (e: RuntimeException) {
        e.printStackTrace() // 打印异常便于排查
        null // 出错时返回空
    } finally {
        try {
            retriever.release() // 释放检索器资源
        } catch (e: RuntimeException) {
            // Ignore failures while cleaning up.
            e.printStackTrace() // 清理失败时忽略但打印日志
        }
    }
}

@Throws(RuntimeException::class)
/**
 * 编译着色器资源（ES 2.0 API）。
 *
 * - 从 `raw` 资源读取 GLSL 源码，使用 `glCompileShader` 编译。
 * - 检查编译状态，若失败则读取 `infoLog` 抛出异常，便于定位问题。
 */
fun compileShaderResourceGLES20(
    context: Context, shaderType: Int, shaderRes: Int
): Int {
    val shaderSource = context.resources.openRawResource(shaderRes).bufferedReader().use { // 读取 raw 资源为字符串
        it.readText() // 读取全部文本
    }
    val shader = GLES20.glCreateShader(shaderType) // 创建着色器对象
    if (shader == 0) {
        throw RuntimeException("Failed to create shader") // 创建失败抛错
    }
    GLES20.glShaderSource(shader, shaderSource) // 指定源码
    GLES20.glCompileShader(shader) // 编译着色器
    val status = IntArray(1) // 编译状态数组
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0) // 查询编译状态
    if (status[0] == 0) { // 编译失败
        val log = GLES20.glGetShaderInfoLog(shader) // 读取错误日志
        GLES20.glDeleteShader(shader) // 删除着色器对象
        throw RuntimeException(log) // 抛出日志便于定位
    }
    return shader // 返回着色器 id
}

@Throws(RuntimeException::class)
/**
 * 链接渲染程序（ES 2.0 API）。
 *
 * - 将顶点/片元着色器附加到 program 并链接。
 * - 检查链接状态，失败时读取 `infoLog` 抛出异常，保证错误显式曝光。
 */
fun linkProgramGLES20(
    vertShader: Int, fragShader: Int
): Int {
    val program = GLES20.glCreateProgram() // 创建程序对象
    if (program == 0) {
        throw RuntimeException("Failed to create program") // 创建失败抛错
    }
    GLES20.glAttachShader(program, vertShader) // 附加顶点着色器
    GLES20.glAttachShader(program, fragShader) // 附加片元着色器
    GLES20.glLinkProgram(program) // 链接程序
    val status = IntArray(1) // 链接状态数组
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0) // 查询链接状态
    if (status[0] == 0) { // 链接失败
        val log = GLES20.glGetProgramInfoLog(program) // 读取错误日志
        GLES20.glDeleteProgram(program) // 删除程序对象
        throw RuntimeException(log) // 抛出日志便于定位
    }
    return program // 返回程序 id
}

/**
 * 将 VBO 绑定到指定 attribute，并设置其格式。
 *
 * 注意：
 * - 此方法不负责关闭/解绑数组对象（交由调用方或 VAO 管理），以避免过度包裹导致状态不透明。
 * - 顶点由两个浮点数组成（x,y），步长为 `2 * BYTES_PER_FLOAT`，从偏移 0 开始。
 */
fun bindData(dataIndex: Int, targetIndex: Int) {
    //激活
    GLES20.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dataIndex) // 绑定 VBO 到 ARRAY_BUFFER
    GLES20.glEnableVertexAttribArray(targetIndex) // 开启指定 attribute
    GLES20.glVertexAttribPointer(
        targetIndex, // attribute 索引
        2,//组成一个顶点的数据个数（x,y）
        GLES20.GL_FLOAT,//数据类型为浮点
        false,//是否需要 GPU 归一化（不需要）
        2 * BYTES_PER_FLOAT,//每个顶点步长（字节数）
        0 // 缓冲区起始偏移
    )
}