package com.isroot.lmbridge.download

import com.isroot.lmbridge.Logger
import com.isroot.lmbridge.PlatformContext
import com.isroot.lmbridge.environment
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * 모델 파일 저장소. **경로 규칙을 단일하게 소유**하여 다운로드·초기화·삭제가
 * 서로 다른 위치를 참조하지 않게 한다.
 *
 * 저장 루트는 플랫폼이 정한다(Android: 외부 앱 저장소 우선, 없으면 내부 / 데스크톱: 앱 데이터 디렉터리).
 */
class ModelStore internal constructor(private val rootDir: String) {

    constructor(context: PlatformContext) : this(context.environment().modelRootDir)

    private companion object {
        const val TAG = "ModelStore"
        const val PART_SUFFIX = ".part"
    }

    /** 모델 전용 디렉터리 경로(없으면 만든다). */
    fun dirFor(model: ModelDownloadManager.ModelInfo): String {
        val dir = Path(rootDir, model.toDirName())
        SystemFileSystem.createDirectories(dir)
        return dir.toString()
    }

    /** 최종 모델 파일 경로. */
    fun fileFor(model: ModelDownloadManager.ModelInfo): String = Path(dirFor(model), model.modelFile).toString()

    /** 다운로드 임시 파일(.part) 경로 — 검증 성공 후 원자적으로 [fileFor]로 옮긴다. */
    fun tempFileFor(model: ModelDownloadManager.ModelInfo): String =
        Path(dirFor(model), model.modelFile + PART_SUFFIX).toString()

    fun isPresent(model: ModelDownloadManager.ModelInfo): Boolean = SystemFileSystem.exists(Path(fileFor(model)))

    fun pathIfPresent(model: ModelDownloadManager.ModelInfo): String? = fileFor(model).takeIf { isPresent(model) }

    fun availableSpace(): Long = freeSpaceOf(rootDir)

    fun delete(model: ModelDownloadManager.ModelInfo): Boolean = try {
        deleteRecursively(Path(dirFor(model)))
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to delete model ${model.modelId}", e)
        false
    }

    private fun deleteRecursively(path: Path) {
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { deleteRecursively(it) }
        }
        SystemFileSystem.delete(path, mustExist = false)
    }
}

/** [path]가 속한 볼륨의 남은 공간(바이트). */
internal expect fun freeSpaceOf(path: String): Long

/** 파일의 SHA-256을 스트리밍 계산하여 소문자 hex로 반환한다. */
internal expect fun sha256Of(path: String): String
