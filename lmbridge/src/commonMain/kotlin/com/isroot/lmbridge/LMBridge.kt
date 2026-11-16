package com.isroot.lmbridge

import kotlin.concurrent.Volatile

object LMBridge {
    enum class Backend {
        CPU,
        GPU,
        NPU,
    }

    enum class LogLevel {
        OFF,
        ERROR,
        WARN,
        DEBUG,
        VERBOSE,
    }

    /** 현재 로그 레벨. SDK 로그와 litertlm 네이티브 로그 하한에 함께 적용된다(엔진 초기화 시점 기준). */
    @Volatile
    var logLevel: LogLevel = LogLevel.ERROR

    /**
     * 숫자로 로그 레벨을 지정한다.
     *
     * @param level 0 = OFF, 1 = ERROR, 2 = WARN, 3 = DEBUG, 4 = VERBOSE
     */
    fun setLogLevel(level: Int) {
        logLevel = when (level) {
            0 -> LogLevel.OFF
            1 -> LogLevel.ERROR
            2 -> LogLevel.WARN
            3 -> LogLevel.DEBUG
            4 -> LogLevel.VERBOSE
            else -> LogLevel.ERROR
        }
    }

    fun isVerboseEnabled(): Boolean = logLevel >= LogLevel.VERBOSE

    fun isDebugEnabled(): Boolean = logLevel >= LogLevel.DEBUG

    fun isWarnEnabled(): Boolean = logLevel >= LogLevel.WARN

    fun isErrorEnabled(): Boolean = logLevel >= LogLevel.ERROR

    const val TAG = "LMBridge"
}

/** SDK 내부 로거. 출력 대상은 플랫폼이 정한다(Android Logcat, 데스크톱 표준 오류 등). */
object Logger {
    fun v(tag: String, message: String) {
        if (LMBridge.isVerboseEnabled()) platformLog(LMBridge.LogLevel.VERBOSE, tag, message, null)
    }

    fun d(tag: String, message: String) {
        if (LMBridge.isDebugEnabled()) platformLog(LMBridge.LogLevel.DEBUG, tag, message, null)
    }

    fun w(tag: String, message: String) {
        if (LMBridge.isWarnEnabled()) platformLog(LMBridge.LogLevel.WARN, tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (LMBridge.isErrorEnabled()) platformLog(LMBridge.LogLevel.ERROR, tag, message, throwable)
    }
}

internal expect fun platformLog(level: LMBridge.LogLevel, tag: String, message: String, throwable: Throwable?)
