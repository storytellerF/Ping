package com.storyteller_f.ping.shader

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20

/**
 * 外部 OES 纹理的封装。
 *
 * 背景：
 * - `SurfaceTexture` 将视频帧写入到一个外部纹理目标 `GL_TEXTURE_EXTERNAL_OES`。
 * - 采样 OES 纹理需要在片元着色器使用 `samplerExternalOES`，且必须使用 `GL_CLAMP_TO_EDGE` 避免边缘出血。
 */
class OESTexture {
    /**
     * 设置外部纹理。
     */
    private val textures: IntArray by lazy {
        IntArray(1).apply { // 仅需一个外部纹理
            //生成之后会存储到数组中
            GLES20.glGenTextures(size, this, 0) // 生成纹理 id
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, this[0]) // 绑定到 OES 纹理目标
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR // 最小化过滤：线性
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR // 放大过滤：线性
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE // S 方向包裹：边缘钳制
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE // T 方向包裹：边缘钳制
            )
        }
    }

    /**
     * 生成并返回与外部纹理绑定的 `SurfaceTexture`，播放器将向该纹理写入最新视频帧。
     */
    fun build(): SurfaceTexture {
        return SurfaceTexture(textures[0]) // 使用该纹理 id 创建 SurfaceTexture
    }
}