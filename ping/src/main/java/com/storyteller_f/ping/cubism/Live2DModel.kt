package com.storyteller_f.ping.cubism

import android.content.Context
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismDefaultParameterId
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.ICubismModelSetting
import com.live2d.sdk.cubism.framework.effect.CubismBreath
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink
import com.live2d.sdk.cubism.framework.id.CubismId
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.motion.ACubismMotion
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion
import com.live2d.sdk.cubism.framework.motion.CubismMotion
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import java.io.File
import java.io.FileInputStream
import java.util.Random

class Live2DModel(
    private val modelPath: String,
    private val modelJsonName: String,
    val context: Context,
    private val pal: Pal,
) : CubismUserModel() {

    private val textureManager = TextureManager()

    private lateinit var modelSetting: ICubismModelSetting

    /**
     * モデルに設定されたまばたき機能用パラメーターID
     */
    private val eyeBlinkIds: MutableList<CubismId> = ArrayList()

    /**
     * モデルに設定されたリップシンク機能用パラメーターID
     */
    private val lipSyncIds: MutableList<CubismId> = ArrayList()

    /**
     * 読み込まれているモーションのマップ
     */
    private val motions: MutableMap<String, ACubismMotion?> = HashMap()

    /**
     * 読み込まれている表情のマップ
     */
    private val expressions: MutableMap<String, ACubismMotion> = HashMap()

    /**
     * パラメーターID: ParamAngleX
     */
    private val idParamAngleX: CubismId

    /**
     * パラメーターID: ParamAngleY
     */
    private val idParamAngleY: CubismId

    /**
     * パラメーターID: ParamAngleZ
     */
    private val idParamAngleZ: CubismId

    /**
     * パラメーターID: ParamBodyAngleX
     */
    private val idParamBodyAngleX: CubismId

    /**
     * パラメーターID: ParamEyeBallX
     */
    private val idParamEyeBallX: CubismId

    /**
     * パラメーターID: ParamEyeBallY
     */
    private val idParamEyeBallY: CubismId

    init {
        if (MOC_CONSISTENCY_VALIDATION_ENABLE) {
            mocConsistency = true
        }
        if (DEBUG_LOG_ENABLE) {
            debugMode = true
        }
        val idManager = CubismFramework.getIdManager()
        idParamAngleX = idManager.getId(CubismDefaultParameterId.ParameterId.ANGLE_X.id)
        idParamAngleY = idManager.getId(CubismDefaultParameterId.ParameterId.ANGLE_Y.id)
        idParamAngleZ = idManager.getId(CubismDefaultParameterId.ParameterId.ANGLE_Z.id)
        idParamBodyAngleX =
            idManager.getId(CubismDefaultParameterId.ParameterId.BODY_ANGLE_X.id)
        idParamEyeBallX = idManager.getId(CubismDefaultParameterId.ParameterId.EYE_BALL_X.id)
        idParamEyeBallY = idManager.getId(CubismDefaultParameterId.ParameterId.EYE_BALL_Y.id)
    }

    fun setup() {
        Log.d(TAG, "setup() called ${Thread.currentThread()}")

        // Setup model
        isUpdated = true
        isInitialized = false

        // json読み込み
        val buffer = readFile(modelJsonName)
        modelSetting = CubismModelSettingJson(buffer)

        try {
            setupModelInternal(modelSetting)
        } catch (e: Exception) {
            Log.e(TAG, "setup: ", e)
        }

        // Setup renderer.
        val renderer = CubismRendererAndroid.create()
        setupRenderer(renderer)
        setupTextures()

        isUpdated = false
        isInitialized = true
    }

    /**
     * モデルの更新処理。モデルのパラメーターから描画状態を決定する
     */
    fun update() {
        val deltaTimeSeconds = pal.deltaTime
        dragManager.update(deltaTimeSeconds)
        dragX = dragManager.x
        dragY = dragManager.y

        // モーションによるパラメーター更新の有無
        var isMotionUpdated = false

        //前回セーブされた状態をロード
        val cubismModel = model
        cubismModel.loadParameters()

        // モーションの再生がない場合、待機モーションの中からランダムで再生する
        if (motionManager.isFinished()) {
            startRandomMotion(MotionGroup.IDLE.id, Priority.IDLE.priority)
        } else {
            // モーションを更新
            isMotionUpdated = motionManager.updateMotion(cubismModel, deltaTimeSeconds)
        }

        // モデルの状態を保存
        cubismModel.saveParameters()

        // 不透明度
        opacity = cubismModel.modelOpacity

        // eye blink
        // メインモーションの更新がないときだけまばたきする
        if (!isMotionUpdated) {
            if (eyeBlink != null) {
                eyeBlink.updateParameters(cubismModel, deltaTimeSeconds)
            }
        }

        // expression
        if (expressionManager != null) {
            // 表情でパラメータ更新（相対変化）
            expressionManager.updateMotion(cubismModel, deltaTimeSeconds)
        }

        // ドラッグ追従機能
        // ドラッグによる顔の向きの調整
        cubismModel.addParameterValue(idParamAngleX, dragX * 30) // -30から30の値を加える
        cubismModel.addParameterValue(idParamAngleY, dragY * 30)
        cubismModel.addParameterValue(idParamAngleZ, dragX * dragY * -30)

        // ドラッグによる体の向きの調整
        cubismModel.addParameterValue(idParamBodyAngleX, dragX * 10) // -10から10の値を加える

        // ドラッグによる目の向きの調整
        cubismModel.addParameterValue(idParamEyeBallX, dragX) // -1から1の値を加える
        cubismModel.addParameterValue(idParamEyeBallY, dragY)

        // Breath Function
        if (breath != null) {
            breath.updateParameters(cubismModel, deltaTimeSeconds)
        }

        // Physics Setting
        if (physics != null) {
            physics.evaluate(cubismModel, deltaTimeSeconds)
        }

        // Lip Sync Setting
        if (lipSync) {
            // リアルタイムでリップシンクを行う場合、システムから音量を取得して0~1の範囲で値を入力します
            val value = 0.0f
            for (i in lipSyncIds.indices) {
                val lipSyncId: CubismId = lipSyncIds[i]
                cubismModel.addParameterValue(lipSyncId, value, 0.8f)
            }
        }

        // Pose Setting
        if (pose != null) {
            pose.updateParameters(cubismModel, deltaTimeSeconds)
        }
        cubismModel.update()
    }

    /**
     * 引数で指定したモーションの再生を開始する。
     *
     * @param group モーショングループ名
     * @param number グループ内の番号
     * @param priority 優先度
     * @param onFinishedMotionHandler モーション再生終了時に呼び出されるコールバック関数。nullの場合は呼び出されない。
     * @return 開始したモーションの識別番号を返す。個別のモーションが終了したか否かを判定するisFinished()の引数で使用する。開始できない時は「-1」
     */
    private fun startMotion(
        group: String,
        number: Int,
        priority: Int,
        onFinishedMotionHandler: IFinishedMotionCallback?
    ): Int {
        if (priority == Priority.FORCE.priority) {
            motionManager.reservationPriority = priority
        } else if (!motionManager.reserveMotion(priority)) {
            return -1
        }

        // ex) idle_0
        val name = group + "_" + number
        val motion = motions[name] as CubismMotion?
        if (motion == null) {
            return -1
        } else {
            motion.setFinishedMotionHandler(onFinishedMotionHandler)
        }

        // load sound files
        val voice = modelSetting.getMotionSoundFileName(group, number)
        if (voice.isNotEmpty()) {
            val path = File(modelPath, voice).absolutePath

            // 別スレッドで音声再生
            val voicePlayer = WavFileHandler(path)
            voicePlayer.start()
        }
        return motionManager.startMotionPriority(motion, priority)
    }

    /**
     * ランダムに選ばれたモーションの再生を開始する。
     * コールバック関数が渡されなかった場合にそれをnullとして同メソッドを呼び出す。
     *
     * @param group モーショングループ名
     * @param priority 優先度
     * @return 開始したモーションの識別番号。個別のモーションが終了したか否かを判定するisFinished()の引数で使用する。開始できない時は「-1」
     */
    @JvmOverloads
    fun startRandomMotion(
        group: String,
        priority: Int,
        onFinishedMotionHandler: IFinishedMotionCallback? = null
    ): Int {
        if (modelSetting.getMotionCount(group) == 0) {
            return -1
        }
        val random = Random()
        val number: Int = random.nextInt(Int.MAX_VALUE) % modelSetting.getMotionCount(group)
        return startMotion(group, number, priority, onFinishedMotionHandler)
    }

    fun draw(matrix: CubismMatrix44) {
        if (model == null) return

        // キャッシュ変数の定義を避けるために、multiplyByMatrix()ではなく、multiply()を使用する。
        CubismMatrix44.multiply(
            modelMatrix.array,
            matrix.array,
            matrix.array
        )
        val renderer = this.getRenderer<CubismRendererAndroid>()
        renderer.mvpMatrix = matrix
        renderer.drawModel()
    }

    /**
     * 当たり判定テスト
     * 指定IDの頂点リストから矩形を計算し、座標が矩形範囲内か判定する
     *
     * @param hitAreaName 当たり判定をテストする対象のID
     * @param x 判定を行うx座標
     * @param y 判定を行うy座標
     * @return 当たっているならtrue
     */
    fun hitTest(hitAreaName: String, x: Float, y: Float): Boolean {
        // 透明時は当たり判定なし
        if (opacity < 1) return false
        val count: Int = modelSetting.getHitAreasCount()
        for (i in 0 until count) {
            if (modelSetting.getHitAreaName(i) == hitAreaName) {
                val drawID: CubismId = modelSetting.getHitAreaId(i)
                return isHit(drawID, x, y)
            }
        }
        // 存在しない場合はfalse
        return false
    }

    /**
     * 引数で指定した表情モーションを設定する
     *
     * @param expressionID 表情モーションのID
     */
    private fun setExpression(expressionID: String) {
        val motion: ACubismMotion? = expressions[expressionID]
        if (motion != null) {
            expressionManager.startMotionPriority(motion, Priority.FORCE.priority)
        }
    }

    /**
     * ランダムに選ばれた表情モーションを設定する
     */
    fun setRandomExpression() {
        if (expressions.isEmpty()) return
        val random = Random()
        val number = random.nextInt(Int.MAX_VALUE) % expressions.size
        for ((i, key) in expressions.keys.withIndex()) {
            if (i == number) {
                setExpression(key)
                return
            }
        }
    }

    private fun setupModelInternal(setting: ICubismModelSetting) {
        // Load Cubism Model
        loadModel(setting)

        // load expression files(.exp3.json)
        loadExpression(setting)

        // Physics
        loadPhysics(setting)

        // Pose
        loadPose(setting)

        // Load eye blink data
        loadEyeBlinkData(setting)

        // Load Breath Data
        loadBreathData()


        // Load UserData
        loadUserData(setting)


        // LipSyncIds
        setLipSync(setting)

        // Set layout
        setLayout(setting)
        model.saveParameters()

        // Load motions
        preloadMotionGroup(setting)
    }

    private fun preloadMotionGroup(setting: ICubismModelSetting) {
        for (i in 0 until setting.getMotionGroupCount()) {
            val group: String = setting.getMotionGroupName(i)
            preLoadMotionGroup(group)
        }
        motionManager.stopAllMotions()
    }

    private fun loadModel(setting: ICubismModelSetting) {
        val fileName: String = setting.getModelFileName()
        if (fileName != "") {
            val buffer = readFile(fileName)
            loadModel(buffer, mocConsistency)
        }
    }

    private fun setLipSync(setting: ICubismModelSetting) {
        val lipSyncIdCount: Int = setting.getLipSyncParameterCount()
        for (i in 0 until lipSyncIdCount) {
            lipSyncIds.add(setting.getLipSyncParameterId(i))
        }
    }

    private fun setLayout(setting: ICubismModelSetting) {
        val layout: Map<String, Float> = HashMap()

        // レイアウト情報が存在すればその情報からモデル行列をセットアップする
        if (setting.getLayoutMap(layout)) {
            modelMatrix.setupFromLayout(layout)
        }
    }

    private fun loadUserData(setting: ICubismModelSetting) {
        val name: String = setting.getUserDataFile()
        if (name.isNotEmpty()) {
            val buffer = readFile(name)
            loadUserData(buffer)
        }
    }

    private fun loadBreathData() {
        breath = CubismBreath.create().apply {
            setParameters(buildList {
                add(
                    CubismBreath.BreathParameterData(
                        idParamAngleX,
                        0.0f,
                        15.0f,
                        6.5345f,
                        0.5f
                    )
                )
                add(
                    CubismBreath.BreathParameterData(
                        idParamAngleY,
                        0.0f,
                        8.0f,
                        3.5345f,
                        0.5f
                    )
                )
                add(
                    CubismBreath.BreathParameterData(
                        idParamAngleZ,
                        0.0f,
                        10.0f,
                        5.5345f,
                        0.5f
                    )
                )
                add(
                    CubismBreath.BreathParameterData(
                        idParamBodyAngleX,
                        0.0f,
                        4.0f,
                        15.5345f,
                        0.5f
                    )
                )
                add(
                    CubismBreath.BreathParameterData(
                        CubismFramework.getIdManager()
                            .getId(CubismDefaultParameterId.ParameterId.BREATH.id),
                        0.5f,
                        0.5f,
                        3.2345f,
                        0.5f
                    )
                )
            })
        }
    }

    private fun loadEyeBlinkData(setting: ICubismModelSetting) {
        val eyeBlinkParameterCount = setting.getEyeBlinkParameterCount()
        if (eyeBlinkParameterCount > 0) {
            eyeBlink = CubismEyeBlink.create(setting)
            // EyeBlinkIds
            for (i in 0 until eyeBlinkParameterCount) {
                eyeBlinkIds.add(setting.getEyeBlinkParameterId(i))
            }
        }
    }

    private fun loadPose(setting: ICubismModelSetting) {
        val name: String = setting.getPoseFileName()
        if (name.isNotEmpty()) {
            val buffer = readFile(name)
            loadPose(buffer)
        }
    }

    private fun readFile(name: String) = FileInputStream(File(modelPath, name)).buffered().use {
        it.readBytes()
    }

    private fun loadPhysics(setting: ICubismModelSetting) {
        val name: String = setting.getPhysicsFileName()
        if (name.isNotEmpty()) {
            val buffer = readFile(name)
            loadPhysics(buffer)
        }
    }

    private fun loadExpression(setting: ICubismModelSetting) {
        if (setting.getExpressionCount() > 0) {
            val count: Int = setting.getExpressionCount()
            for (i in 0 until count) {
                val name: String = setting.getExpressionName(i)
                val fileName = setting.getExpressionFileName(i)
                val buffer = readFile(fileName)
                val motion: CubismExpressionMotion = loadExpression(buffer)
                expressions[name] = motion
            }
        }
    }

    /**
     * モーションデータをグループ名から一括でロードする。
     * モーションデータの名前はModelSettingから取得する。
     *
     * @param group モーションデータのグループ名
     */
    private fun preLoadMotionGroup(group: String) {
        val count: Int = modelSetting.getMotionCount(group)
        for (i in 0 until count) {
            // ex) idle_0
            val name = group + "_" + i
            val fileName: String = modelSetting.getMotionFileName(group, i)
            if (fileName.isNotEmpty()) {
                val buffer = readFile(fileName)

                // If a motion cannot be loaded, a process is skipped.
                val tmp: CubismMotion = loadMotion(buffer) ?: continue
                val fadeInTime: Float = modelSetting.getMotionFadeInTimeValue(group, i)
                if (fadeInTime != -1.0f) {
                    tmp.fadeInTime = fadeInTime
                }
                val fadeOutTime: Float = modelSetting.getMotionFadeOutTimeValue(group, i)
                if (fadeOutTime != -1.0f) {
                    tmp.fadeOutTime = fadeOutTime
                }
                tmp.setEffectIds(eyeBlinkIds, lipSyncIds)
                motions[name] = tmp
            }
        }
    }

    /**
     * OpenGLのテクスチャユニットにテクスチャをロードする
     */
    private fun setupTextures() {
        for (modelTextureNumber in 0 until modelSetting.getTextureCount()) {
            // テクスチャ名が空文字だった場合はロード・バインド処理をスキップ
            val textureFileName = modelSetting.getTextureFileName(modelTextureNumber)
            if (textureFileName.isEmpty()) {
                continue
            }

            // OpenGL ESのテクスチャユニットにテクスチャをロードする
            val texturePath = File(modelPath, textureFileName).absolutePath
            val texture = textureManager.createTextureFromPngFile(texturePath)
            val glTextureNumber: Int = texture.id

            val renderer = this.getRenderer<CubismRendererAndroid>()
            renderer.bindTexture(modelTextureNumber, glTextureNumber)
            renderer.isPremultipliedAlpha(PREMULTIPLIED_ALPHA_ENABLE)
        }
    }

    fun release() {
        delete()
    }

    companion object {
        private const val TAG = "Live2DModel"
    }
}


fun readFile(filePath: String) = FileInputStream(filePath).buffered().use {
    it.readBytes()
}