package com.isroot.lmbridge.models

import java.io.File
import java.nio.charset.Charset

internal actual fun Throwable.isOutOfMemory(): Boolean = this is OutOfMemoryError

internal actual fun ByteArray.decodeWith(charset: String): String = String(this, Charset.forName(charset))

/** 이미지 파일을 추가한다(메모리 적재 없음). */
fun MultimodalInput.Builder.imageFile(file: File) = imageFile(file.absolutePath)

/** 오디오 파일을 추가한다(메모리 적재 없음). */
fun MultimodalInput.Builder.audioFile(file: File) = audioFile(file.absolutePath)

/** 텍스트 문서 파일을 [charset]으로 디코드하여 주입한다. */
fun MultimodalInput.Builder.document(path: String, charset: Charset) = document(path, charset.name())
