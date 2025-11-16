package com.storyteller_f.ping.shader

/**
 * 管理渲染用到的三类缓冲区：
 * - 顶点坐标 VBO（屏幕上的两个三角形组成的矩形）
 * - 纹理坐标 VBO（与顶点一一对应的采样坐标）
 * - 索引缓冲 EBO（以索引方式绘制两个三角形）
 *
 * 设计思路：
 * - 在 Java/Kotlin 侧先用 `ByteBuffer.allocateDirect` 创建连续的原生内存，再通过 `glBufferData`
 *   传给 GPU，这样避免了 GC 干扰与不必要的拷贝，且与 OpenGL 的期望内存布局一致。
 * - 分离位置与纹理坐标两个 VBO，便于在着色器中分别绑定不同的 attribute，提升可读性与可维护性。
 * - 使用 `GL_STATIC_DRAW`，因为这些数据在渲染期间不会改变，驱动可以进行更激进的优化。
 */

import android.opengl.GLES20 // OpenGL ES 2.0 接口
import java.nio.ByteBuffer // 直接缓冲区分配（原生内存）
import java.nio.ByteOrder // 指定字节序为本机字节序
import java.nio.FloatBuffer // 浮点型缓冲视图
import java.nio.IntBuffer // 整型缓冲视图

open class GLBuffer { // 封装 VBO/EBO 的创建与数据上传

    private val vertices: FloatBuffer = run { // 顶点坐标缓冲的构建
        // 屏幕覆盖用的四个顶点（两个三角形），范围为裁剪空间 [-1, 1]
        val vertexArray = floatArrayOf( // 顶点数组：裁剪空间四个角点
            -1.0f, -1.0f, // bottom left
            -1.0f, 1.0f,  // top left
            1.0f, -1.0f,  // bottom right
            1.0f, 1.0f    // top right
        )
        ByteBuffer.allocateDirect( // 分配连续原生内存
            vertexArray.size * BYTES_PER_FLOAT // 按元素个数与字节数计算容量
        ).run {
            order(ByteOrder.nativeOrder()).asFloatBuffer().apply { // 设为本机字节序并转为 FloatBuffer 视图
                put(vertexArray).position(0) // 写入数据并重置读写位置到起点
            }
        }
    }
    private val texCoordinationBuffer: FloatBuffer = run { // 纹理坐标缓冲的构建
        // 与顶点一一对应的纹理坐标，采用 OpenGL 纹理坐标系 [0,1]
        val texCoordinationArray = floatArrayOf( // 与顶点一一对应的纹理坐标
            0.0f, 1.0f,  // bottom left
            0.0f, 0.0f,  // top left
            1.0f, 1.0f,  // bottom right
            1.0f, 0.0f   // top right
        )
        ByteBuffer.allocateDirect( // 分配纹理坐标缓冲的原生内存
            texCoordinationArray.size * BYTES_PER_FLOAT // 计算容量
        ).run {
            order(ByteOrder.nativeOrder()).asFloatBuffer().apply { // 创建浮点视图
                put(texCoordinationArray).position(0) // 写入并归位
            }
        }
    }

    /**
     * 用于 EBO 的索引数据，按照两个三角形的绘制顺序组织，避免重复顶点传输。
     */
    private val indicesBuffer: IntBuffer = run { // 索引缓冲的构建（EBO 数据）
        val indexArray = intArrayOf(0, 1, 2, 3, 2, 1) // 两个三角形的索引顺序
        ByteBuffer.allocateDirect( // 分配原生内存
            indexArray.size * BYTES_PER_INT // 计算容量
        ).run {
            order(ByteOrder.nativeOrder()).asIntBuffer().apply { // 整型视图
                put(indexArray).position(0) // 写入并归位
            }
        }
    }

    /**
     * GPU 侧的实际缓冲对象数组：
     * - [0] 顶点坐标 VBO
     * - [1] 纹理坐标 VBO
     * - [2] 索引缓冲 EBO
     *
     * 在初始化时：
     * - 先 `glGenBuffers` 申请句柄，再分别绑定并通过 `glBufferData` 上传。
     * - 每次上传后主动解除绑定，避免后续错误地写入同一目标；这是典型的 OpenGL 习惯用法。
     */
    private val buffers: IntArray by lazy { // GPU 端缓冲对象句柄，延迟创建
        IntArray(3).apply { // 依次存放：位置 VBO、纹理 VBO、索引 EBO
            //获取指定的缓冲区
            GLES20.glGenBuffers(size, this, 0) // 生成 3 个缓冲对象 id 到数组
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this[0]) // 绑定位置 VBO 到 ARRAY_BUFFER 目标
            GLES20.glBufferData( // 上传位置数据到 GPU
                GLES20.GL_ARRAY_BUFFER,
                vertices.capacity() * BYTES_PER_FLOAT, // 数据字节数
                vertices, // 源数据缓冲
                GLES20.GL_STATIC_DRAW // 静态使用提示
            )
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0) // 解绑 ARRAY_BUFFER，避免误写

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this[1]) // 绑定纹理坐标 VBO
            GLES20.glBufferData( // 上传纹理坐标数据
                GLES20.GL_ARRAY_BUFFER,
                texCoordinationBuffer.capacity() * BYTES_PER_FLOAT, // 字节数
                texCoordinationBuffer, // 源数据缓冲
                GLES20.GL_STATIC_DRAW // 静态使用
            )
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0) // 解绑 ARRAY_BUFFER

            //绑定EBO
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, this[2]) // 绑定索引缓冲到 ELEMENT_ARRAY_BUFFER
            GLES20.glBufferData( // 上传索引数据到 GPU
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indicesBuffer.capacity() * BYTES_PER_INT, // 字节数
                indicesBuffer, // 源索引数据
                GLES20.GL_STATIC_DRAW // 静态使用
            )
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0) // 解绑 EBO，记录结束
        }
    }

    /**
     * 暴露底层缓冲对象 id，便于外部按照约定索引访问。
     */
    operator fun get(index: Int): Int { // 通过下标访问缓冲对象 id
        return buffers[index] // 返回约定位置的句柄
    }
}