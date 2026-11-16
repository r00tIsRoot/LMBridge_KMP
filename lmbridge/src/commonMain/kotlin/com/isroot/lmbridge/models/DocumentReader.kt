package com.isroot.lmbridge.models

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * 로컬 텍스트 문서를 읽어 컨텍스트 주입용 문자열로 변환한다.
 *
 * 실패를 조용히 삼키지 않고 [LMBridgeError.InvalidInput]으로 표면화한다.
 *
 * 텍스트 문서만 지원한다. PDF·오피스 등 바이너리 문서는 litertlm에 문서 `Content`
 * 타입이 없어 범위 밖이며, 선두 NUL 바이트 감지로 바이너리를 걸러 오류로 알린다.
 */
internal object DocumentReader {
    /** 문서 텍스트 크기 상한(초과 시 오류). */
    const val MAX_DOCUMENT_BYTES: Long = 1L * 1024 * 1024

    /** 바이너리 판별을 위해 선두에서 검사할 최대 바이트 수. */
    private const val BINARY_SNIFF_BYTES = 8000

    /**
     * @throws LMBridgeError.InvalidInput 파일 없음/읽기 불가/크기 초과/바이너리/미지원 문자셋인 경우.
     */
    fun read(
        path: String,
        charset: String = "UTF-8",
        maxBytes: Long = MAX_DOCUMENT_BYTES,
    ): String {
        val file = Path(path)
        val metadata = SystemFileSystem.metadataOrNull(file)
        if (metadata == null || !metadata.isRegularFile) {
            throw LMBridgeError.InvalidInput("Document not found or unreadable: $path")
        }
        if (metadata.size > maxBytes) {
            throw LMBridgeError.InvalidInput("Document too large: ${metadata.size} bytes (max $maxBytes): $path")
        }
        val bytes = try {
            SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        } catch (e: Exception) {
            throw LMBridgeError.InvalidInput("Document not found or unreadable: $path", e)
        }
        if (looksBinary(bytes)) {
            throw LMBridgeError.InvalidInput("Document appears to be binary (unsupported): $path")
        }
        return try {
            bytes.decodeWith(charset)
        } catch (e: Exception) {
            throw LMBridgeError.InvalidInput("Unsupported charset '$charset': $path", e)
        }
    }

    /** 선두 일부에 NUL 바이트가 있으면 바이너리로 간주한다(PDF/이미지/실행파일 등). */
    private fun looksBinary(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, BINARY_SNIFF_BYTES)
        for (i in 0 until n) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }
}

/** [charset] 이름으로 바이트를 디코드한다. 지원하지 않는 문자셋이면 예외를 던진다. */
internal expect fun ByteArray.decodeWith(charset: String): String
