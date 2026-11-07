package com.storyteller_f.ping

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runners.Parameterized
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.motion.CubismMotion
import com.live2d.sdk.cubism.framework.motion.CubismMotionManager
import com.live2d.sdk.cubism.framework.physics.CubismPhysics
import com.storyteller_f.ping.cubism.Live2DModel
import com.storyteller_f.ping.cubism.Pal
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(Parameterized::class)
class Live2DDeviceTest(private val modelName: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun models() = listOf("Haru", "Hiyori", "Mao", "Mark", "Natori", "Rice", "Wanko", "Ren").map { arrayOf(it) }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun copyAssets(path: String, target: File) {
        val assets = instrumentation.context.assets
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            target.parentFile!!.mkdirs()
            assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else children.forEach { copyAssets("$path/$it", File(target, it)) }
    }

    private fun withModel(test: (Live2DModel, File) -> Unit) {
        val directory = File(context.filesDir, "live2d-validation/$modelName")
        copyAssets(modelName, directory)
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display, null, 0, null, 0))
        val config = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 4,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE), 0, config, 0, 1, count, 0))
        val eglContext = EGL14.eglCreateContext(display, config[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, config[0],
            intArrayOf(EGL14.EGL_WIDTH, 512, EGL14.EGL_HEIGHT, 512, EGL14.EGL_NONE), 0)
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, eglContext))
        GLES20.glViewport(0, 0, 512, 512)
        com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid.reloadShader()
        val model = Live2DModel(directory.path, "$modelName.model3.json", context, Pal())
        var failure: Throwable? = null
        try {
            model.setup()
            test(model, directory)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try { model.release() } catch (cleanup: Throwable) {
                if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
            }
            com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid.staticRelease()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
    }

    @Test fun texturedModelRendersOnDeviceGpu() = withModel { model, directory ->
        val shader = com.live2d.sdk.cubism.framework.rendering.android.CubismShaderAndroid.getInstance()
        val sets = shader.javaClass.getDeclaredField("shaderSets").apply { isAccessible = true }
        fun compiledPrograms(): Set<Int> = (sets.get(shader) as List<*>).map { entry ->
            entry!!.javaClass.getDeclaredField("shaderProgram").apply { isAccessible = true }.getInt(entry)
        }.filter { it != 0 }.toSet()
        assertTrue("Renderer initialization must not eagerly compile shaders", compiledPrograms().isEmpty())
        GLES20.glViewport(0, 0, 512, 512)
        GLES20.glClearColor(1f, 1f, 1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // Apply the app's pose, blink and physics initialization before drawing.
        model.update()
        model.draw(CubismMatrix44.create())
        GLES20.glFinish()
        val programs = compiledPrograms().size
        assertTrue("Expected only model-required shaders, got $programs", programs in 1..32)
        android.util.Log.i("Live2DDeviceTest", "$modelName compiled_programs=$programs")
        assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        val pixels = ByteBuffer.allocateDirect(512 * 512 * 4)
        GLES20.glReadPixels(0, 0, 512, 512, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        val bytes = ByteArray(pixels.capacity())
        pixels.rewind(); pixels.get(bytes)
        var colored = 0
        for (i in bytes.indices step 4)
            if ((bytes[i].toInt() and 255) < 240 || (bytes[i+1].toInt() and 255) < 240 ||
                (bytes[i+2].toInt() and 255) < 240) colored++
        assertTrue("Expected textured character pixels, got $colored", colored > 10000)
        val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
        pixels.rewind(); bitmap.copyPixelsFromBuffer(pixels)
        val upright = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, 512, 512,
            android.graphics.Matrix().apply { preScale(1f, -1f) }, false)
        File(context.getExternalFilesDir(null), "live2d-$modelName.png").outputStream().use {
            upright.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        upright.recycle()
        bitmap.recycle()
    }

    @Test fun selectedModelPersistsForWallpaperService() {
        val directory = File(context.filesDir, "live2d-validation/$modelName")
        copyAssets(modelName, directory)
        val path = File(directory, "$modelName.model3.json").path
        runBlocking {
            context.bookDataStore.edit { it[selected] = path }
            assertEquals(path, context.bookDataStore.data.first().selectedWallPaper())
        }
    }

    @Test fun failedReplacementKeepsCurrentWallpaper() = withModel { _, directory ->
        val delegate = com.storyteller_f.ping.cubism.Delegate(context)
        val manager = com.storyteller_f.ping.cubism.Live2DManager(context, delegate.view, Pal())
        val current = manager.javaClass.getDeclaredField("live2DModel").apply { isAccessible = true }
        try {
            manager.plugModel(File(directory, "$modelName.model3.json").path)
            assertTrue(manager.isReady)
            val original = current.get(manager)
            manager.plugModel(File(directory, "missing-model.model3.json").path)
            assertTrue("Failed replacement must leave a ready wallpaper", manager.isReady)
            assertSame(original, current.get(manager))
            assertTrue("Failed candidate must not be retried on every frame", manager.isReady)
            manager.onDrawFrame(512, 512)
            GLES20.glFinish()
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        } finally {
            manager.releaseCurrentModule()
        }
    }

    @Test fun authoredMotionChangesModelParameters() = withModel { instance, directory ->
        val json = JSONObject(File(directory, "$modelName.model3.json").readText())
        val groups = json.getJSONObject("FileReferences").getJSONObject("Motions")
        val model = instance.model
        val before = FloatArray(model.parameterCount) { model.getParameterValue(it) }
        for (group in groups.keys()) {
            val clips = groups.getJSONArray(group)
            for (clip in 0 until clips.length()) {
                before.indices.forEach { model.setParameterValue(it, before[it]) }
                val path = clips.getJSONObject(clip).getString("File")
                val bytes = File(directory, path).readBytes()
                val duration = JSONObject(String(bytes)).getJSONObject("Meta").getDouble("Duration")
                val manager = CubismMotionManager()
                manager.setEventCallback({ _, _, _ -> }, null)
                manager.startMotionPriority(CubismMotion.create(bytes), 3)
                var changed = false
                repeat((duration * 60).toInt().coerceAtLeast(120)) {
                    manager.updateMotion(model, 1f/60f)
                    model.update()
                    changed = changed || before.indices.any {
                        kotlin.math.abs(before[it] - model.getParameterValue(it)) > 0.001f
                    }
                }
                assertTrue("$modelName/$path did not affect model", changed)
            }
        }
    }

    @Test fun physicsRespondsToChangingInput() = withModel { instance, directory ->
        val json = JSONObject(File(directory, "$modelName.model3.json").readText())
        val path = json.getJSONObject("FileReferences").getString("Physics")
        val physics = CubismPhysics.create(File(directory, path).readBytes())
        val model = instance.model
        val settings = JSONObject(File(directory, path).readText()).getJSONArray("PhysicsSettings")
        val inputs = buildSet {
            for (i in 0 until settings.length()) {
                val sources = settings.getJSONObject(i).getJSONArray("Input")
                for (j in 0 until sources.length()) add(sources.getJSONObject(j).getJSONObject("Source").getString("Id"))
            }
        }.map { CubismFramework.getIdManager().getId(it) }
        var changed = false
        repeat(180) { frame ->
            inputs.forEach { model.setParameterValue(it, (kotlin.math.sin(frame * 0.12) * 30).toFloat()) }
            val before = FloatArray(model.parameterCount) { model.getParameterValue(it) }
            physics.evaluate(model, 1f/60f)
            changed = changed || before.indices.any { kotlin.math.abs(before[it] - model.getParameterValue(it)) > 0.001f }
            model.update()
        }
        assertTrue("Physics never changed an output parameter", changed)
    }
}
