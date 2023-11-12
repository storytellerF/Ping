package com.storyteller_f.ping

import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismFrameworkConfig
import com.storyteller_f.ping.cubism.Delegate
import com.storyteller_f.ping.cubism.PrintLogFunction
import java.lang.ref.WeakReference

object DelegatePool {
    private var currentDelegate = WeakReference<Delegate>(null)
    private val cache = mutableListOf<WeakReference<Delegate>>()
    private val cubismOption = CubismFramework.Option().apply {
        logFunction = PrintLogFunction()
        loggingLevel = CubismFrameworkConfig.LogLevel.VERBOSE
    }

    fun addDelegate(delegate: Delegate) {
        synchronized(this) {
            cache.add(WeakReference(delegate))
            currentDelegate = WeakReference(delegate)
            initEnv()
        }
    }


    fun removeDelegate(delegate: Delegate) {
        synchronized(this) {
            val index = cache.indexOfFirst {
                it.get() == delegate
            }
            cache.removeAt(index)
            if (currentDelegate.get() == delegate) {
                currentDelegate = if (cache.isNotEmpty()) {
                    initEnv()
                    WeakReference(cache.firstOrNull()?.get())
                } else {
                    WeakReference(null)
                }
            }
        }
    }

    private fun initEnv() {
        if (CubismFramework.isInitialized()) {
            CubismFramework.dispose()
            CubismFramework.cleanUp()
        }
        if (!CubismFramework.isStarted()) {
            CubismFramework.startUp(cubismOption)
        }
    }

    fun judge(delegate: Delegate, block: () -> Unit) {
        synchronized(this) {
            if (currentDelegate.get() == delegate) {
                block()
            }
        }
    }

}