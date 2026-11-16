package com.isroot.lmbridge

import java.io.File
import java.lang.management.ManagementFactory

/** 데스크톱(JVM)의 [PlatformContext]. 앱이 쓸 데이터 디렉터리를 정한다. */
actual abstract class PlatformContext internal constructor() {
    /** 모델·캐시를 둘 앱 데이터 디렉터리. */
    abstract val dataDir: File
}

/**
 * 데스크톱 앱용 컨텍스트.
 *
 * @param dataDir 모델(`models/`)과 엔진 캐시(`cache/`)를 둘 디렉터리. 없으면 만든다.
 */
class DesktopContext(override val dataDir: File) : PlatformContext() {

    companion object {
        /**
         * OS 관례에 맞는 앱 데이터 디렉터리를 쓰는 컨텍스트.
         * - macOS: `~/Library/Application Support/<appName>`
         * - Windows: `%LOCALAPPDATA%\<appName>`
         * - Linux 등: `$XDG_DATA_HOME/<appName>` 또는 `~/.local/share/<appName>`
         */
        fun forApp(appName: String): DesktopContext {
            val home = System.getProperty("user.home")
            val os = System.getProperty("os.name").lowercase()
            val base = when {
                "mac" in os -> File(home, "Library/Application Support")
                "win" in os -> System.getenv("LOCALAPPDATA")?.let(::File) ?: File(home, "AppData/Local")
                else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: File(home, ".local/share")
            }
            return DesktopContext(File(base, appName))
        }
    }
}

internal actual fun PlatformContext.environment(): PlatformEnvironment {
    val cache = File(dataDir, "cache").apply { mkdirs() }
    val models = File(dataDir, "models").apply { mkdirs() }
    return PlatformEnvironment(
        cacheDir = cache.absolutePath,
        modelRootDir = models.absolutePath,
        totalMemoryBytes = totalPhysicalMemory(),
        isLowRamDevice = false,
        nativeLibraryDir = null,
    )
}

/** 데스크톱에는 앱 번들 모델 개념이 없다. */
internal actual fun PlatformContext.extractBundledModel(fileName: String): String? = null

private fun totalPhysicalMemory(): Long =
    (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
        ?.totalMemorySize
        // 물리 메모리를 알 수 없는 JVM이면 최대 힙으로 보수적으로 잡는다.
        ?: Runtime.getRuntime().maxMemory()

internal actual fun platformLog(level: LMBridge.LogLevel, tag: String, message: String, throwable: Throwable?) {
    if (level == LMBridge.LogLevel.OFF) return
    System.err.println("${level.name.first()}/$tag: $message")
    throwable?.printStackTrace()
}
