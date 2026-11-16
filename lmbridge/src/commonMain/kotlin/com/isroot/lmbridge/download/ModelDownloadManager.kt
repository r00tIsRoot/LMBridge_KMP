package com.isroot.lmbridge.download

import com.isroot.lmbridge.Logger
import com.isroot.lmbridge.PlatformContext
import com.isroot.lmbridge.ioDispatcher
import com.isroot.lmbridge.models.LMBridgeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * HuggingFace에서 `.litertlm` 모델을 내려받는 다운로드 관리자.
 *
 * 이어받기(resume), 원자적 쓰기(.part → 최종), SHA-256 무결성 검증을 지원한다.
 * 저장 경로는 [ModelStore]가 소유한다.
 */
class ModelDownloadManager(
    context: PlatformContext,
    private val store: ModelStore = ModelStore(context),
) {

    private companion object {
        const val TAG = "ModelDownloadManager"
    }

    /** 다운로드 진행 상태. */
    sealed class DownloadStatus {
        data object NotStarted : DownloadStatus()
        data class Downloading(
            val totalBytes: Long,
            val receivedBytes: Long,
            val progressPercent: Int,
        ) : DownloadStatus()

        /** 무결성 검증(SHA-256) 진행 중. */
        data object Verifying : DownloadStatus()
        data class Failed(val message: String, val error: LMBridgeError? = null) : DownloadStatus()
        data class Completed(val filePath: String) : DownloadStatus()
    }

    /**
     * 다운로드 대상 모델 정보.
     *
     * @property modelId HuggingFace 모델 ID
     * @property modelFile 내려받을 파일명
     * @property commitHash 특정 버전 커밋 해시
     * @property sizeInBytes 전체 크기(진행률/검증용, 0이면 헤더에서 추정)
     * @property sha256 무결성 검증용 SHA-256(소문자 hex). null이면 크기 검증으로 폴백.
     * @property displayName 화면에 노출할 이름. 미지정 시 [modelId]의 마지막 경로 조각을 쓴다.
     * @property gated HuggingFace 게이트(제한) 모델이면 true. 다운로드에 HF Access Token이 필요하다.
     */
    data class ModelInfo(
        val modelId: String,
        val modelFile: String,
        val commitHash: String,
        val sizeInBytes: Long = 0L,
        val sha256: String? = null,
        val displayName: String = modelId.substringAfterLast('/'),
        val gated: Boolean = false,
    ) {
        fun toDownloadUrl(): String =
            "https://huggingface.co/$modelId/resolve/$commitHash/$modelFile?download=true"

        fun toDirName(): String = "${modelId.replace("/", "_")}_${commitHash.take(8)}"
    }

    /**
     * 모델을 진행률과 함께 내려받는다.
     *
     * 중단된 다운로드가 있으면 `.part`에서 이어받는다. 완료 후 SHA-256(또는 크기)을
     * 검증한 뒤에만 최종 파일로 승격한다. 취소하면 `.part`를 남겨 다음에 이어받는다.
     */
    fun downloadModel(
        modelInfo: ModelInfo,
        accessToken: String? = null,
    ): Flow<DownloadStatus> = flow {
        emit(DownloadStatus.NotStarted)

        val finalFile = store.fileFor(modelInfo)
        if (SystemFileSystem.exists(Path(finalFile))) {
            emit(DownloadStatus.Completed(finalFile))
            return@flow
        }

        val tempFile = store.tempFileFor(modelInfo)
        try {
            val downloaded = downloadToPart(
                url = modelInfo.toDownloadUrl(),
                accessToken = accessToken,
                partPath = tempFile,
                expectedSize = modelInfo.sizeInBytes,
            ) { total, received ->
                val percent = if (total > 0) ((received * 100) / total).toInt() else 0
                emit(DownloadStatus.Downloading(total, received, percent))
            }

            // 크기 검증
            if (modelInfo.sizeInBytes > 0 && downloaded != modelInfo.sizeInBytes) {
                SystemFileSystem.delete(Path(tempFile), mustExist = false)
                emit(
                    DownloadStatus.Failed(
                        "Downloaded size mismatch: expected ${modelInfo.sizeInBytes}, got $downloaded",
                        LMBridgeError.IntegrityCheckFailed(modelInfo.sizeInBytes.toString(), downloaded.toString()),
                    ),
                )
                return@flow
            }

            // 무결성 검증(SHA-256)
            if (modelInfo.sha256 != null) {
                emit(DownloadStatus.Verifying)
                val actual = sha256Of(tempFile)
                if (!actual.equals(modelInfo.sha256, ignoreCase = true)) {
                    SystemFileSystem.delete(Path(tempFile), mustExist = false)
                    emit(
                        DownloadStatus.Failed(
                            "SHA-256 mismatch",
                            LMBridgeError.IntegrityCheckFailed(modelInfo.sha256, actual),
                        ),
                    )
                    return@flow
                }
            } else {
                Logger.w(TAG, "No sha256 for ${modelInfo.modelId}; verified by size only")
            }

            // 원자적 승격: .part → 최종
            SystemFileSystem.atomicMove(Path(tempFile), Path(finalFile))
            emit(DownloadStatus.Completed(finalFile))
        } catch (e: CancellationException) {
            throw e // 취소는 실패가 아님 — .part는 보존하여 다음에 이어받음
        } catch (e: Exception) {
            Logger.e(TAG, "Download failed", e)
            emit(DownloadStatus.Failed(e.message ?: "Unknown error", LMBridgeError.from(e)))
        }
    }.flowOn(ioDispatcher)

    fun isModelDownloaded(modelInfo: ModelInfo): Boolean = store.isPresent(modelInfo)

    fun getModelPath(modelInfo: ModelInfo): String? = store.pathIfPresent(modelInfo)

    fun deleteModel(modelInfo: ModelInfo): Boolean = store.delete(modelInfo)

    fun getAvailableSpace(): Long = store.availableSpace()
}

/**
 * [url]을 [partPath]에 (이어)받고, 누적 바이트 수를 반환한다.
 *
 * - `.part`가 있으면 그 길이부터 Range 요청으로 이어받는다. 서버가 416을 주면 `.part`를 지우고 처음부터 받는다.
 * - 200/206 이외의 응답은 [LMBridgeError.DownloadFailed]로 던진다.
 * - 수신할 때마다 [onProgress]`(전체, 누적)`을 호출한다. 전체를 모르면 0이다.
 * - 코루틴이 취소되면 즉시 멈춘다.
 */
internal expect suspend fun downloadToPart(
    url: String,
    accessToken: String?,
    partPath: String,
    expectedSize: Long,
    onProgress: suspend (total: Long, received: Long) -> Unit,
): Long
