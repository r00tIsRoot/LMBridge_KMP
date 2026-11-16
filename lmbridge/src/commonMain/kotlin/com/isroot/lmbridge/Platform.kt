package com.isroot.lmbridge

import com.isroot.lmbridge.inference.InferenceEngine
import kotlinx.coroutines.CoroutineDispatcher

/**
 * 플랫폼별 실행 환경의 진입 핸들.
 *
 * - Android: `android.content.Context`의 별칭이라 기존처럼 `LMBridgeClient.Builder(context)`로 쓴다.
 * - 데스크톱: `DesktopContext`(앱 데이터 디렉터리를 지정)를 넘긴다.
 */
expect abstract class PlatformContext

/** 초기화 시점에 플랫폼에서 읽어 오는 값. */
internal class PlatformEnvironment(
    /** litertlm 캐시 디렉터리(컴파일된 커널 등). null이면 엔진 기본값. */
    val cacheDir: String?,
    /** 다운로드한 모델을 둘 루트 디렉터리. */
    val modelRootDir: String,
    /** 기기 총 메모리(바이트). 인메모리 입력 예산 산출에 쓴다. */
    val totalMemoryBytes: Long,
    /** 저사양 기기 여부(Android `isLowRamDevice`). 예산을 한 단계 낮춘다. */
    val isLowRamDevice: Boolean,
    /** NPU 벤더 런타임을 찾을 디렉터리(Android `nativeLibraryDir`). 없으면 null. */
    val nativeLibraryDir: String?,
)

internal expect fun PlatformContext.environment(): PlatformEnvironment

/**
 * 앱에 번들된 기본 모델을 쓸 수 있는 경로로 꺼낸다. 번들 개념이 없는 플랫폼은 null.
 * (Android는 assets에서 내부 저장소로 한 번 복사한다.)
 */
internal expect fun PlatformContext.extractBundledModel(fileName: String): String?

/** 현재 플랫폼의 추론 엔진 구현을 만든다. */
internal expect fun createInferenceEngine(): InferenceEngine

/** 파일·네트워크 IO용 디스패처. */
internal expect val ioDispatcher: CoroutineDispatcher
