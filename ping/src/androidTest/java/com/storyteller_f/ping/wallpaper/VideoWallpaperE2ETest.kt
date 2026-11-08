package com.storyteller_f.ping.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.opengl.GLSurfaceView
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.ping.pagerDataStore
import com.storyteller_f.ping.preview
import com.storyteller_f.ping.selected
import com.storyteller_f.ping.shader.OutputHandoff
import com.storyteller_f.ping.shader.VideoOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 真实系统壁纸测试：会替换当前壁纸，仅在专用测试设备上显式启用。
 * 使用 am instrument --no-hidden-api-checks -e videoWallpaperE2E true，
 * 反射只用于观察 framework 管理的 Engine 及应用私有状态，不向生产代码添加测试入口。
 */
class VideoWallpaperE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui = instrumentation.uiAutomation
    private val manager = WallpaperManager.getInstance(context)
    private val directory = File(context.filesDir, "video-wallpaper-e2e")
    private var enabled = false
    private var previousSelected: String? = null
    private var previousPreview: String? = null
    private var previousComponent: ComponentName? = null

    // instrumentation 测试以同步入口连接 suspend 操作，主 Looper 始终可以处理服务和播放器回调。
    @Before fun prepare() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("videoWallpaperE2E") == "true")
        previousComponent = manager.wallpaperInfo?.component
        val preferences = context.pagerDataStore.data.first()
        previousSelected = preferences[selected]
        previousPreview = preferences[preview]
        enabled = true
        directory.mkdirs()
        listOf("red", "blue", "green").forEach { name ->
            instrumentation.context.assets.open("video-wallpaper/$name.mp4").use { input ->
                File(directory, "$name.mp4").outputStream().use { input.copyTo(it) }
            }
        }
        select("red")
        setComponent(ComponentName(context, PingPagerService::class.java))
        home()
        await("initial engine and player") { engine()?.let { it.isVisible && player(it)?.isPlaying == true } == true }
        awaitColor(Color.RED)
        Log.i("VideoWallpaperE2E", "Initial real wallpaper ready")
    }

    @After fun restore() = runBlocking<Unit> {
        if (!enabled) return@runBlocking
        val stopping = onMain { engine() }
        val stoppingThread = onMain { stopping?.let { field(view(it), "mGLThread") as Thread } }
        try {
            setComponent(previousComponent?.takeUnless { it.className == PingPagerService::class.java.name }
                ?: ComponentName("com.android.systemui", "com.android.systemui.wallpapers.ImageWallpaper"))
            await("video engines and GL cleanup finished") {
                engine() == null && stoppingThread?.isAlive != true &&
                    (stopping == null || (field(stopping, "engineJob") as Job).isCancelled)
            }
        } finally {
            context.pagerDataStore.edit {
                previousSelected?.let { value -> it[selected] = value } ?: it.remove(selected)
                previousPreview?.let { value -> it[preview] = value } ?: it.remove(preview)
            }
            directory.deleteRecursively()
        }
    }

    @Test fun switchesSameSizeAndPortraitVideosWithoutReplacingEngine() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        for ((name, color) in listOf("blue" to Color.BLUE, "green" to Color.GREEN, "red" to Color.RED,
            "blue" to Color.BLUE, "red" to Color.RED)) {
            val previous = onMain { checkNotNull(player(original)) }
            select(name)
            await("replacement player for $name") { player(original)?.let { it !== previous && it.isPlaying } == true }
            awaitColor(color)
            assertMoving(name)
            onMain {
                assertSame(original, engine())
                assertThrows(IllegalStateException::class.java) { previous.isPlaying }
            }
        }
        evidence("video-switching")
    }

    @Test fun hidingPausesAndReturningResumesSamePlayer() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val originalPlayer = onMain { checkNotNull(player(original)) }
        repeat(3) {
            hide()
            await("hidden and paused") { !original.isVisible && !originalPlayer.isPlaying }
            val position = onMain { originalPlayer.currentPosition }
            delay(450)
            onMain { assertEquals("Hidden playback must not advance", position, originalPlayer.currentPosition) }
            home()
            await("visible and playing") { original.isVisible && originalPlayer.isPlaying }
            onMain { assertSame(original, engine()); assertSame(originalPlayer, player(original)) }
            assertMoving("resume-$it")
        }
        evidence("hidden-resumed")
    }

    @Test fun destroyingEngineReleasesPlayerSurfaceAndGlThreadThenCanReapply() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val originalPlayer = onMain { checkNotNull(player(original)) }
        val output = onMain { checkNotNull(output(original)) }
        val glThread = onMain { field(view(original), "mGLThread") as Thread }
        setComponent(ComponentName("com.android.systemui", "com.android.systemui.wallpapers.ImageWallpaper"))
        await("engine destruction and GL cleanup") { (field(original, "engineJob") as Job).isCancelled && !glThread.isAlive }
        onMain {
            assertNull(field(original, "playback"))
            assertNull(output(original))
            assertFalse(output.isValid)
            assertFalse(output.surface.isValid)
            assertFalse("GL thread must exit", glThread.isAlive)
            assertThrows(IllegalStateException::class.java) { originalPlayer.isPlaying }
        }
        select("blue")
        delay(300)
        onMain { assertNull("Destroyed engine must not observe a new selection", field(original, "playback")) }
        setComponent(ComponentName(context, PingPagerService::class.java))
        home()
        await("new engine") { engine()?.let { it !== original && player(it)?.isPlaying == true } == true }
        awaitColor(Color.BLUE)
        assertMoving("reapplied")
        evidence("engine-reapplied")
    }

    @Test fun destroyingPausedEngineReleasesRetiredQueueWithoutCurrentContext() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val previous = onMain {
            view(original).preserveEGLContextOnPause = false
            checkNotNull(output(original))
        }
        val glThread = onMain { field(view(original), "mGLThread") as Thread }
        hide()
        await("paused with a retired queue") { !original.isVisible && !previous.isValid }
        onMain { assertTrue(previous.surface.isValid) }
        setComponent(ComponentName("com.android.systemui", "com.android.systemui.wallpapers.ImageWallpaper"))
        await("paused engine and GL thread terminated") {
            (field(original, "engineJob") as Job).isCancelled && !glThread.isAlive
        }
        onMain {
            assertNull(field(original, "playback"))
            assertNull(output(original))
            assertFalse(previous.surface.isValid)
            assertTrue(previous.canRelease)
            val renderer = checkNotNull(field(original, "renderer"))
            assertTrue("All retired SurfaceTextures must be released", (field(renderer, "retiredBindings") as List<*>).isEmpty())
        }
        home()
    }

    @Test fun eglContextRecreationRebindsLiveDecoderAndResumesFrames() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val originalPlayer = onMain { checkNotNull(player(original)) }
        repeat(3) {
            val previous = onMain {
                view(original).preserveEGLContextOnPause = false
                checkNotNull(output(original))
            }
            Log.i("VideoWallpaperE2E", "Forcing context destruction: $it")
            hide()
            await("context destroyed and old output retired") { !original.isVisible && !previous.isValid }
            onMain { assertTrue("Keep the old queue alive until the decoder switches surfaces", previous.surface.isValid) }
            Log.i("VideoWallpaperE2E", "Old context destroyed: $it")
            home()
            await("new context output attached") {
                output(original)?.let { it !== previous && it.isValid && originalPlayer.isPlaying } == true
            }
            onMain {
                assertSame(original, engine())
                assertSame("Context replacement must retain the live decoder", originalPlayer, player(original))
                assertFalse("Release the old Surface after replacement", previous.surface.isValid)
                assertTrue(previous.canRelease)
            }
            Log.i("VideoWallpaperE2E", "New context attached: $it")
            awaitColor(Color.RED)
            assertMoving("context-$it")
        }
        evidence("egl-recreated")
    }

    private suspend fun select(name: String) {
        context.pagerDataStore.edit {
            it[selected] = File(directory, "$name.mp4").path
            it.remove(preview)
        }
    }

    private fun setComponent(component: ComponentName) {
        ui.adoptShellPermissionIdentity()
        try {
            assertEquals(true, manager.javaClass.getMethod("setWallpaperComponent", ComponentName::class.java).invoke(manager, component))
        } finally {
            ui.dropShellPermissionIdentity()
        }
    }

    private fun home() = context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun hide() = context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }
    private suspend fun await(description: String, condition: () -> Boolean) {
        try {
            withTimeout(15000) { while (!onMain(condition)) delay(30) }
        } catch (failure: Exception) {
            Log.e("VideoWallpaperE2E", "Failed waiting for: $description", failure)
            throw failure
        }
    }

    private fun field(instance: Any, name: String): Any? {
        val type = generateSequence(instance.javaClass as Class<*>?) { it.superclass }
            .first { candidate -> candidate.declaredFields.any { it.name == name } }
        return type.getDeclaredField(name).apply { isAccessible = true }.get(instance)
    }

    private fun engine(): WallpaperService.Engine? {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getDeclaredMethod("currentActivityThread").invoke(null)
        val services = field(thread, "mServices") as Map<*, *>
        val service = services.values.filterIsInstance<PingPagerService>().singleOrNull() ?: return null
        val engines = field(service, "mActiveEngines")
        val active = when (engines) {
            is Map<*, *> -> engines.values
            is Collection<*> -> engines
            else -> error("Unsupported framework engine collection")
        }
        return active.mapNotNull { item ->
            if (item is WallpaperService.Engine) item else item?.let { field(it, "mEngine") as? WallpaperService.Engine }
        }.singleOrNull { !it.isPreview }
    }

    private fun player(engine: WallpaperService.Engine): MediaPlayer? =
        field(engine, "playback")?.let { field(it, "player") as MediaPlayer }

    @Suppress("UNCHECKED_CAST")
    private fun output(engine: WallpaperService.Engine): VideoOutput? =
        (field(engine, "outputs") as OutputHandoff<VideoOutput>).current

    private fun view(engine: WallpaperService.Engine): GLSurfaceView =
        (field(engine, "surfaceView" + "$" + "delegate") as Lazy<*>).value as GLSurfaceView

    private suspend fun awaitColor(color: Int) {
        try {
            withTimeout(15000) {
                while (true) {
                    val bitmap = checkNotNull(ui.takeScreenshot())
                    val matching = (10..90 step 5).sumOf { x -> (15..75 step 5).count { y ->
                        val pixel = bitmap.getPixel(bitmap.width * x / 100, bitmap.height * y / 100)
                        kotlin.math.abs(Color.red(pixel) - Color.red(color)) < 45 &&
                            kotlin.math.abs(Color.green(pixel) - Color.green(color)) < 45 &&
                            kotlin.math.abs(Color.blue(pixel) - Color.blue(color)) < 45
                    } }
                    bitmap.recycle()
                    if (matching > 100) return@withTimeout
                    delay(100)
                }
            }
        } catch (failure: Exception) {
            evidence("color-timeout")
            onMain {
                val current = engine()
                Log.e("VideoWallpaperE2E", "Color timeout: visible=${current?.isVisible} playing=${current?.let { player(it)?.isPlaying }} position=${current?.let { player(it)?.currentPosition }}")
            }
            throw failure
        }
    }

    private suspend fun assertMoving(label: String) {
        val first = checkNotNull(ui.takeScreenshot())
        try {
            withTimeout(8000) {
                while (true) {
                    delay(150)
                    val next = checkNotNull(ui.takeScreenshot())
                    val differences = (10..90 step 2).sumOf { x -> (30..70 step 2).count { y ->
                        first.getPixel(first.width * x / 100, first.height * y / 100) !=
                            next.getPixel(next.width * x / 100, next.height * y / 100)
                    } }
                    next.recycle()
                    if (differences > 30) break
                }
            }
            Log.i("VideoWallpaperE2E", "Moving video verified: $label")
        } finally { first.recycle() }
    }

    private fun evidence(name: String) {
        val bitmap = checkNotNull(ui.takeScreenshot())
        try {
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
