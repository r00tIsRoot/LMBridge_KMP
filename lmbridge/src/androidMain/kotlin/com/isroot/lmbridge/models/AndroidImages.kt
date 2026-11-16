package com.isroot.lmbridge.models

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** 비트맵 이미지를 [encoding] 정책으로 인코딩하여 추가한다(기본 무손실 PNG). */
fun MultimodalInput.Builder.image(bitmap: Bitmap, encoding: ImageEncoding = ImageEncoding.LOSSLESS_PNG) =
    imageBytes(ImageEncoder.encode(bitmap, encoding))

/** 이미지들 뒤에 텍스트 프롬프트를 붙인 입력. 이미지는 무손실 PNG로 인코딩한다. */
fun MultimodalInput.Companion.textAndImages(prompt: String, images: List<Bitmap>): MultimodalInput =
    MultimodalInput.Builder().apply {
        images.forEach { image(it) }
        text(prompt)
    }.build()

/** [ImageEncoding] 정책에 따라 비트맵을 바이트로 인코딩한다. */
internal object ImageEncoder {
    fun encode(bitmap: Bitmap, encoding: ImageEncoding): ByteArray {
        val scaled = encoding.scaledSize(bitmap.width, bitmap.height)
            ?.let { (w, h) -> Bitmap.createScaledBitmap(bitmap, w, h, true) }
            ?: bitmap
        val format = when (encoding.format) {
            ImageEncoding.Format.PNG -> Bitmap.CompressFormat.PNG
            ImageEncoding.Format.JPEG -> Bitmap.CompressFormat.JPEG
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(format, encoding.quality, stream)
        if (scaled !== bitmap) scaled.recycle()
        return stream.toByteArray()
    }
}
