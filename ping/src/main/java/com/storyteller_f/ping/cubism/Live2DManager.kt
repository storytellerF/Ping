package com.storyteller_f.ping.cubism

import android.content.Context
import android.util.Log
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import java.io.File

/**
 * サンプルアプリケーションにおいてCubismModelを管理するクラス。
 * モデル生成と破棄、モデル切り替えを行う。
 */
class Live2DManager(
    val context: Context,
    val view: View,
    private val pal: Pal
) {

    val isReady: Boolean
        get() {
            loadModelIfNeed()
            return live2DModel?.let {
                !it.isUpdated
            } ?: false
        }
    private var live2DModel: Live2DModel? = null
    private var pendingModelPath: String? = null

    // onUpdateメソッドで使用されるキャッシュ変数
    private val viewMatrix: CubismMatrix44 = CubismMatrix44.create()
    private val projection: CubismMatrix44 = CubismMatrix44.create()

    // モデル更新処理及び描画処理を行う
    fun onDrawFrame(width: Int, height: Int) {
        val model = live2DModel ?: return
        projection.loadIdentity()
        if (model.model.getCanvasWidth() > 1.0f && width < height) {
            // 横に長いモデルを縦長ウィンドウに表示する際モデルの横サイズでscaleを算出する
            model.modelMatrix.setWidth(2.0f)
            projection.scale(1.0f, width.toFloat() / height.toFloat())
        } else {
            projection.scale(height.toFloat() / width.toFloat(), 1.0f)
        }

        // 必要があればここで乗算する
        viewMatrix.multiplyByMatrix(projection)

        model.update()
        model.draw(projection) // 参照渡しなのでprojectionは変質する
    }

    /**
     * 画面をドラッグした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    fun onDrag(x: Float, y: Float) {
        val appModel = live2DModel ?: return
        appModel.setDragging(x, y)
    }

    /**
     * シーンを切り替える
     *
     */
    private fun loadModel(jsonFilePath: String) {
        Log.d(TAG, "loadModel() called with: jsonFilePath = $jsonFilePath")
        val file = File(jsonFilePath)
        val candidate = Live2DModel(file.parent!!, file.name, context, pal)
        try {
            candidate.setup()
        } catch (error: Exception) {
            try {
                candidate.release()
            } catch (cleanup: Exception) {
                error.addSuppressed(cleanup)
            }
            Log.e(TAG, "Model preparation failed; retaining current model", error)
            return
        }
        val previous = live2DModel
        live2DModel = candidate
        previous?.release()
        Log.i(TAG, "loadModel: $live2DModel")

        // 別レンダリング先を選択した際の背景クリア色
        val clearColor = floatArrayOf(1.0f, 1.0f, 1.0f)
        view.setRenderingTargetClearColor(
            clearColor[0],
            clearColor[1],
            clearColor[2]
        )
    }

    fun plugModel(jsonFilePath: String) {
        // The GL thread retains the current model and displayed buffer while
        // preparing its replacement. A failed load must not blank the wallpaper.
        pendingModelPath = jsonFilePath
    }

    private fun loadModelIfNeed() {
        val json = pendingModelPath
        if (json != null) {
            pendingModelPath = null
            Log.d(TAG, "loadModelIfNeed() called")
            loadModel(json)
        }
    }

    fun releaseCurrentModule() {
        pendingModelPath = null
        live2DModel?.release()
        live2DModel = null
    }

    companion object {
        private const val TAG = "Live2DManager"
    }
}
