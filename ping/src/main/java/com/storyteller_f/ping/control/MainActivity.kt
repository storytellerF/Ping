package com.storyteller_f.ping.control

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract.Document
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.storyteller_f.common_ui.scope
import com.storyteller_f.file_system.ensureDirs
import com.storyteller_f.file_system.ensureFile
import com.storyteller_f.ping.R
import com.storyteller_f.ping.database.Wallpaper
import com.storyteller_f.ping.database.requireMainDatabase
import com.storyteller_f.ping.databinding.ActivityMainBinding
import com.storyteller_f.ping.shader.firstFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment).navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            scope.launch {
                saveResult(uri)
            }
        }
        binding.fab.setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }
    }

    private suspend fun saveResult(uri: Uri) {
        val dir = "wallpaper-${System.currentTimeMillis()}"
        val name =
            contentResolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
                        if (columnIndex == -1) null else it.getString(columnIndex)
                    } else null
                } ?: dir

        try {
            saveVideoResult(dir, uri, name)
        } catch (e: Exception) {
            Log.e(TAG, "saveResult: ", e)
        }
    }

    private suspend fun extract(archive: InputStream, dest: String) {
        ZipInputStream(archive).use { stream ->
            while (true) {
                stream.nextEntry?.let {
                    processEntry(dest, it, stream)
                } ?: break
            }
        }
    }

    private suspend fun processEntry(dest: String, nextEntry: ZipEntry, stream: ZipInputStream) {
        val child = File(dest, nextEntry.name)
        Log.i(TAG, "processEntry: ${child.absolutePath}")
        if (nextEntry.isDirectory) {
            child.ensureDirs()!!
        } else {
            child.ensureFile()!!
            write(withContext(Dispatchers.IO) {
                FileOutputStream(child)
            }, stream)
        }
    }

    private suspend fun write(file: FileOutputStream, stream: ZipInputStream) {
        val buffer = ByteArray(1024)
        file.buffered().use {
            while (true) {
                yield()
                val offset = stream.read(buffer)
                if (offset != -1) {
                    it.write(buffer, 0, offset)
                } else break
            }
        }
    }

    private suspend fun saveVideoResult(dir: String, uri: Uri, uriName: String) {
        val dest = File(cacheDir, dir)
        val element = if (uriName.endsWith("book")) {
            extract(contentResolver.openInputStream(uri)!!, dest.absolutePath)
            val jsonFile = dest.list()?.first {
                it.endsWith(".model3.json")
            }!!
            Wallpaper(
                File(dest, jsonFile).absolutePath,
                uriName,
                Calendar.getInstance().time,
                ""
            )
        } else if (uriName.endsWith("world")) {
            extract(contentResolver.openInputStream(uri)!!, dest.absolutePath)
            val jsonFile = dest.list()?.first {
                it.endsWith(".gltf")
            }!!
            Wallpaper(
                File(dest, jsonFile).absolutePath,
                uriName,
                Calendar.getInstance().time,
                ""
            )
        } else {
            val file = File(dest, "video.mp4").ensureFile() ?: return
            withContext(Dispatchers.IO) {
                file.outputStream().sink().buffer().use { writer ->
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.source().buffer().use {
                            writer.writeAll(it)
                        }
                    }
                }
            }
            val iconFile = File(dest, "thumbnail.jpg").ensureFile() ?: return
            createVideoThumbnailFromUri(this@MainActivity, file.toUri())?.let { thumbnail ->
                withContext(Dispatchers.IO) {
                    FileOutputStream(iconFile).use {
                        thumbnail.compress(Bitmap.CompressFormat.JPEG, 60, it)
                    }
                }
            }
            Wallpaper(
                file.absolutePath,
                uriName,
                Calendar.getInstance().time,
                iconFile.absolutePath
            )
        }
        requireMainDatabase.dao().insertAll(listOf(element))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

/**
 * createVideoThumbnailFromUri
 * @param context Activity context or application context.
 * @param uri Video uri.
 * @return Bitmap thumbnail
 *
 * Hacked from ThumbnailUtils.createVideoThumbnail()'s code.
 */
fun createVideoThumbnailFromUri(
    context: Context, uri: Uri
): Bitmap? {
    val bitmap = context.firstFrame(uri) ?: return null
    // Scale down the bitmap if it's too large.
    val width = bitmap.width
    val height = bitmap.height
    val max = width.coerceAtLeast(height)
    return if (max > 512) {
        val scale = 512f / max
        val w = (scale * width).roundToInt()
        val h = (scale * height).roundToInt()
        Bitmap.createScaledBitmap(bitmap, w, h, true)
    } else bitmap
}