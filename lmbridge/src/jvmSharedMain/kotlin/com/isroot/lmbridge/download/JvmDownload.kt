package com.isroot.lmbridge.download

import com.isroot.lmbridge.Logger
import com.isroot.lmbridge.models.LMBridgeError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

private const val TAG = "ModelDownloadManager"
private const val BUFFER_SIZE = 64 * 1024
private const val TIMEOUT_MS = 30_000
private const val HASH_BUFFER = 8 * 1024

internal actual fun freeSpaceOf(path: String): Long = File(path).apply { mkdirs() }.freeSpace

internal actual fun sha256Of(path: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    File(path).inputStream().use { input ->
        val buffer = ByteArray(HASH_BUFFER)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal actual suspend fun downloadToPart(
    url: String,
    accessToken: String?,
    partPath: String,
    expectedSize: Long,
    onProgress: suspend (total: Long, received: Long) -> Unit,
): Long {
    val tempFile = File(partPath)
    val have = if (tempFile.exists()) tempFile.length() else 0L

    var connection = openConnection(url, accessToken, rangeFrom = have)
    var responseCode = connection.responseCode

    // 416: 요청 범위 불충족 → .part 손상/무효로 보고 처음부터 재시도
    if (responseCode == 416) {
        connection.disconnect()
        tempFile.delete()
        connection = openConnection(url, accessToken, rangeFrom = 0)
        responseCode = connection.responseCode
    }

    if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
        connection.disconnect()
        throw LMBridgeError.DownloadFailed("HTTP error: $responseCode")
    }

    val resuming = responseCode == HttpURLConnection.HTTP_PARTIAL && have > 0
    val startBytes = if (resuming) have else 0L
    val remaining = connection.contentLengthLong.coerceAtLeast(0L)
    val total = when {
        expectedSize > 0 -> expectedSize
        remaining > 0 -> startBytes + remaining
        else -> 0L
    }

    Logger.d(TAG, "Downloading $url: resume=$resuming start=$startBytes total=$total")

    var downloaded = startBytes
    try {
        connection.inputStream.use { input ->
            FileOutputStream(tempFile, resuming).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    currentCoroutineContext().ensureActive() // 취소 반영
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(total, downloaded)
                }
                output.flush()
            }
        }
    } finally {
        connection.disconnect()
    }
    return downloaded
}

private fun openConnection(url: String, accessToken: String?, rangeFrom: Long): HttpURLConnection {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = TIMEOUT_MS
    connection.readTimeout = TIMEOUT_MS
    connection.setRequestProperty("Accept-Encoding", "identity")
    if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
    // 이어받기: Range 헤더는 반드시 connect() 이전에 설정해야 한다.
    if (rangeFrom > 0) connection.setRequestProperty("Range", "bytes=$rangeFrom-")
    connection.connect()
    return connection
}
