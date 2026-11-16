package com.isroot.lmbridge

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

actual typealias PlatformContext = Context

internal actual fun PlatformContext.environment(): PlatformEnvironment {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    return PlatformEnvironment(
        cacheDir = cacheDir.absolutePath,
        // 외부 앱 저장소 우선(대용량 모델), 없으면 내부 저장소.
        modelRootDir = (getExternalFilesDir(null) ?: filesDir).absolutePath,
        totalMemoryBytes = memInfo.totalMem,
        isLowRamDevice = am.isLowRamDevice,
        // NPU 벤더 런타임을 앱 번들 라이브러리에서 찾도록 한다(litertlm Android 가이드 권장).
        nativeLibraryDir = applicationInfo.nativeLibraryDir,
    )
}

internal actual fun PlatformContext.extractBundledModel(fileName: String): String? {
    val outFile = File(filesDir, fileName)
    if (!outFile.exists()) {
        try {
            assets.open(fileName).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            outFile.delete()
            return null
        }
    }
    return outFile.absolutePath
}

internal actual fun platformLog(level: LMBridge.LogLevel, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        LMBridge.LogLevel.VERBOSE -> Log.v(tag, message, throwable)
        LMBridge.LogLevel.DEBUG -> Log.d(tag, message, throwable)
        LMBridge.LogLevel.WARN -> Log.w(tag, message, throwable)
        LMBridge.LogLevel.ERROR -> Log.e(tag, message, throwable)
        LMBridge.LogLevel.OFF -> Unit
    }
}
