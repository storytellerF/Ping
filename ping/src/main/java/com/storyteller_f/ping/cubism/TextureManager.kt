package com.storyteller_f.ping.cubism

import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.FileInputStream

// テクスチャの管理を行うクラス
class TextureManager {
    // 画像情報データクラス
    class TextureInfo {
        var id = 0 // テクスチャID
        var width = 0 // 横幅
        var height = 0 // 高さ
        var filePath: String? = null // ファイル名
    }

    // 画像読み込み
    // imageFileOffset: glGenTexturesで作成したテクスチャの保存場所
    fun createTextureFromPngFile(filePath: String): TextureInfo {
        // search loaded texture already
        for (textureInfo in textures) {
            if (textureInfo.filePath == filePath) {
                return textureInfo
            }
        }
        // decodeStreamは乗算済みアルファとして画像を読み込むようである
        val bitmap = BitmapFactory.decodeStream(FileInputStream(filePath))

        // Texture0をアクティブにする
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        // OpenGLにテクスチャを生成
        val textureId = IntArray(1)
        GLES20.glGenTextures(1, textureId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])

        // メモリ上の2D画像をテクスチャに割り当てる
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // ミップマップを生成する
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)

        // 縮小時の補間設定
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_LINEAR
        )
        // 拡大時の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        val textureInfo = TextureInfo()
        textureInfo.filePath = filePath
        textureInfo.width = bitmap.getWidth()
        textureInfo.height = bitmap.getHeight()
        textureInfo.id = textureId[0]
        textures.add(textureInfo)

        // bitmap解放
        bitmap.recycle()

        return textureInfo
    }

    private val textures: MutableList<TextureInfo> = ArrayList() // 画像情報のリスト
}
