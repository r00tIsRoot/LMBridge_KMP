package com.isroot.lmbridge

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random

/** 테스트용 임시 파일을 만든다. */
internal fun tempFile(bytes: ByteArray, suffix: String = ".txt"): String {
    val path = Path(SystemTemporaryDirectory, "lmbridge-test-${Random.nextLong().toULong()}$suffix")
    SystemFileSystem.sink(path).buffered().use { it.write(bytes) }
    return path.toString()
}

/** 테스트용 빈 임시 디렉터리를 만든다. */
internal fun tempDir(): String {
    val path = Path(SystemTemporaryDirectory, "lmbridge-test-${Random.nextLong().toULong()}")
    SystemFileSystem.createDirectories(path)
    return path.toString()
}
