package com.rodrigo.drawntosurvive.game

import android.util.Log
import com.rodrigo.drawntosurvive.BuildConfig

internal object GameLog {
    fun debug(message: String) {
        if (BuildConfig.DEBUG) runCatching { Log.d("DrawnToSurvive", "$message | thread=${Thread.currentThread().name}") }
    }
}
