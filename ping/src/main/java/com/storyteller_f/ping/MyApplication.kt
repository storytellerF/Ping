package com.storyteller_f.ping

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.material.color.DynamicColors
import com.live2d.sdk.cubism.framework.CubismFramework

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        CubismFramework.startUp(CubismFramework.Option().apply {
            loadFileFunction = com.live2d.sdk.cubism.framework.ICubismLoadFileFunction { path ->
                assets.open(path).use { it.readBytes() }
            }
        })
        CubismFramework.initialize()
    }
}


// At the top level of your kotlin file:
val Context.pagerDataStore by preferencesDataStore(name = "pager-settings")
val Context.worldDataStore by preferencesDataStore(name = "world-settings")
val Context.bookDataStore by preferencesDataStore(name = "book-settings")

/**
 * 预览引擎可优先读取 preview；桌面引擎只读取 selected。
 * 系统确认应用后才提交 selected，确认或取消返回时均清除 preview。
 */
val preview = stringPreferencesKey("preview")
val selected = stringPreferencesKey("selected")

fun Preferences.selectedWallPaper(isPreview: Boolean = false) =
    (if (isPreview) this[preview]?.takeIf { it.isNotEmpty() } else null)
        ?: this[selected]?.takeIf { it.isNotEmpty() }
