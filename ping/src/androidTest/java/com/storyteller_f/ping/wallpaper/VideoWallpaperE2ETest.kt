package com.storyteller_f.ping.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.Settings
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.ping.pagerDataStore
import com.storyteller_f.ping.preview
import com.storyteller_f.ping.selected
import kotlinx.coroutines.Dispatchers
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

/** 会替换系统壁纸，只在专用设备上显式启用；应用内部通过 Debug 只读快照观察。 */
class VideoWallpaperE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui = instrumentation.uiAutomation
    private val manager = WallpaperManager.getInstance(context)
    private val component = ComponentName(context, PingPagerService::class.java)
    private lateinit var directory: File
    private var environment: WallpaperTestEnvironment? = null

    @Before fun prepare() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("videoWallpaperE2E") == "true")
        environment = WallpaperTestEnvironment.capture(context, manager, ::setComponent)
        directory = File(context.cacheDir, "video-wallpaper-e2e-${System.nanoTime()}").apply { mkdirs() }
        listOf("red", "blue", "green").forEach { name ->
            instrumentation.context.assets.open("video-wallpaper/$name.mp4").use { input ->
                File(directory, "$name.mp4").outputStream().use { input.copyTo(it) }
            }
        }
        select("red")
        setComponent(component)
        home()
        await("initial playback") { engine()?.snapshot()?.playerState == "playing" }
        awaitColor(Color.RED)
    }

    @After fun restore() = runBlocking<Unit> {
        val environment = environment ?: return@runBlocking
        try { environment.restore() } finally {
            if (::directory.isInitialized) directory.deleteRecursively()
        }
    }

    @Test fun switchesSameSizeAndPortraitVideosWithoutReplacingEngine() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        for ((name, color) in listOf("blue" to Color.BLUE, "green" to Color.GREEN, "red" to Color.RED,
            "blue" to Color.BLUE, "red" to Color.RED)) {
            val previous = onMain { original.snapshot() }
            select(name)
            await("replacement for $name") { original.snapshot().let { it.sourceId > previous.sourceId && it.playerState == "playing" } }
            awaitColor(color)
            assertMoving(name)
            onMain {
                assertSame(original, engine())
                val state = original.snapshot()
                assertEquals(state.playersCreated - 1, state.playersReleased)
                assertEquals(state.sourceId, state.renderer.displayedSource)
            }
        }
        evidence("video-switching")
    }

    @Test fun failedReplacementKeepsLastFrameAndCropUntilAnotherVideoSucceeds() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val previous = onMain { original.snapshot() }
        // 文件不存在，准备过程不会产生新尺寸或首帧，保留画面必须仍使用旧裁剪。
        select("missing")
        await("failed candidate retains displayed source") { original.snapshot().let {
            it.sourceId > previous.sourceId && it.playerState == "stopped" && it.renderer.pendingSource == it.sourceId
        } }
        // 生命周期快照先于 SurfaceFlinger 最后一次合成；等待提交稳定，再验证旧帧不动。
        withTimeout(5000) {
            var previousFrame = checkNotNull(ui.takeScreenshot())
            try {
                while (true) {
                    delay(250)
                    val next = checkNotNull(ui.takeScreenshot())
                    val difference = pixelDifferences(previousFrame, next)
                    previousFrame.recycle()
                    previousFrame = next
                    if (difference == 0) break
                }
            } finally { previousFrame.recycle() }
        }
        awaitColor(Color.RED)
        onMain { assertEquals(previous.sourceId, original.snapshot().renderer.displayedSource) }
        select("green")
        await("recovery playback") { original.snapshot().playerState == "playing" }
        awaitColor(Color.GREEN)
        assertMoving("recovered-after-failed-selection")
    }

    @Test fun hidingPausesAndReturningResumesSamePlayer() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val created = onMain { original.snapshot().playersCreated }
        repeat(3) {
            hide()
            await("hidden and paused") { original.snapshot().let { !it.visible && it.playerState == "paused" } }
            val position = onMain { original.snapshot().positionMs }
            delay(450)
            onMain { assertEquals(position, original.snapshot().positionMs) }
            home()
            await("resumed") { original.snapshot().let { it.visible && it.playerState == "playing" } }
            onMain { assertSame(original, engine()); assertEquals(created, original.snapshot().playersCreated) }
            assertMoving("resume-$it")
        }
        evidence("hidden-resumed")
    }

    @Test fun destroyingEngineReleasesResourcesThenCanReapply() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val output = onMain { checkNotNull(original.output()) }
        WallpaperTestEnvironment.stopVideo(::setComponent)
        assertClosed(original)
        onMain { assertTrue(output.snapshot().surfaceReleased) }
        select("blue")
        delay(300)
        assertClosed(original)
        setComponent(component)
        home()
        await("new engine") { engine()?.let { it !== original && it.snapshot().playerState == "playing" } == true }
        awaitColor(Color.BLUE)
        assertMoving("reapplied")
        evidence("engine-reapplied")
    }

    @Test fun destroyingPausedEngineReleasesRetiredQueueWithoutCurrentContext() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val previous = onMain {
            original.preserveContextOnPause(false)
            checkNotNull(original.output())
        }
        hide()
        await("context retired") { !original.snapshot().renderer.hasContext && !previous.snapshot().valid }
        onMain { assertFalse(previous.snapshot().surfaceReleased) }
        WallpaperTestEnvironment.stopVideo(::setComponent)
        assertClosed(original)
        onMain { assertTrue(previous.snapshot().surfaceReleased) }
        home()
    }

    @Test fun eglContextRecreationRebindsLiveDecoderAndResumesFrames() = runBlocking<Unit> {
        val original = onMain { checkNotNull(engine()) }
        val created = onMain { original.snapshot().playersCreated }
        repeat(3) {
            val before = onMain { original.snapshot().renderer.contextId }
            val previous = onMain {
                original.preserveContextOnPause(false)
                checkNotNull(original.output())
            }
            hide()
            await("old context retired") { !original.snapshot().renderer.hasContext && !previous.snapshot().valid }
            onMain { assertFalse(previous.snapshot().surfaceReleased) }
            home()
            await("new output and resumed playback") { original.snapshot().let {
                it.renderer.contextId > before && it.playerState == "playing" && it.output?.valid == true
            } }
            onMain {
                assertSame(original, engine())
                assertEquals(created, original.snapshot().playersCreated)
                assertTrue(previous.snapshot().surfaceReleased)
            }
            awaitColor(Color.RED)
            assertMoving("context-$it")
        }
        evidence("egl-recreated")
    }

    @Test fun environmentRestoresPreviouslyActiveVideoAndSelection() = runBlocking<Unit> {
        select("blue")
        awaitColor(Color.BLUE)
        val saved = WallpaperTestEnvironment.capture(context, manager, ::setComponent)
        try {
            select("green")
            awaitColor(Color.GREEN)
        } finally { saved.restore() }
        home()
        assertEquals(component, manager.wallpaperInfo?.component)
        assertEquals(File(directory, "blue.mp4").path, context.pagerDataStore.data.first()[selected])
        await("restored original video") { engine()?.snapshot()?.playerState == "playing" }
        awaitColor(Color.BLUE)
        assertMoving("restored-original-video")
    }

    @Test fun environmentRestoresPreviouslyStaticWallpaper() = runBlocking<Unit> {
        WallpaperTestEnvironment.stopVideo(::setComponent)
        val originalComponent = manager.wallpaperInfo?.component
        val saved = WallpaperTestEnvironment.capture(context, manager, ::setComponent)
        try {
            setComponent(component)
            home()
            awaitColor(Color.RED)
        } finally { saved.restore() }
        assertEquals(originalComponent, manager.wallpaperInfo?.component)
        onMain { assertNull(engine()) }
    }

    private suspend fun assertClosed(engine: VideoWallpaperDebug.Engine) = onMain {
        val state = engine.snapshot()
        assertTrue(state.destroyed)
        assertTrue(state.glStopped)
        assertFalse(state.observing)
        assertEquals(state.playersCreated, state.playersReleased)
        assertEquals("stopped", state.playerState)
        assertNull(state.output)
        assertEquals(0, state.renderer.retiredOutputs)
    }

    private suspend fun select(name: String) {
        context.pagerDataStore.edit { it[selected] = File(directory, "$name.mp4").path; it.remove(preview) }
    }

    // 唯一的 framework 反射边界：系统壁纸激活 API。内部观察不读取 framework 私有字段。
    private fun setComponent(component: ComponentName) {
        ui.adoptShellPermissionIdentity()
        try {
            assertEquals(true, manager.javaClass.getMethod("setWallpaperComponent", ComponentName::class.java).invoke(manager, component))
        } finally { ui.dropShellPermissionIdentity() }
    }
    private fun home() = context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun hide() = context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun engine() = VideoWallpaperDebug.engines.singleOrNull { !it.snapshot().isPreview }
    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }
    private suspend fun await(description: String, condition: () -> Boolean) {
        try { withTimeout(15000) { while (!onMain(condition)) delay(30) } }
        catch (failure: Exception) { Log.e("VideoWallpaperE2E", "Failed waiting for: $description", failure); throw failure }
    }
    private fun pixelDifferences(first: Bitmap, next: Bitmap) = (10..90 step 2).sumOf { x -> (30..70 step 2).count { y ->
        first.getPixel(first.width * x / 100, first.height * y / 100) != next.getPixel(next.width * x / 100, next.height * y / 100)
    } }
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
                Log.e("VideoWallpaperE2E", "Color timeout: visible=${current?.snapshot()?.visible} state=${current?.snapshot()?.playerState} position=${current?.snapshot()?.positionMs}")
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
                    val differences = pixelDifferences(first, next)
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
