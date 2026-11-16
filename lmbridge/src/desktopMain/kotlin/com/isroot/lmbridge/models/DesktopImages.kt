package com.isroot.lmbridge.models

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** [BufferedImage]를 [encoding] 정책으로 인코딩하여 추가한다(기본 무손실 PNG). */
fun MultimodalInput.Builder.image(image: BufferedImage, encoding: ImageEncoding = ImageEncoding.LOSSLESS_PNG) =
    imageBytes(ImageEncoder.encode(image, encoding))

/** [ImageEncoding] 정책에 따라 [BufferedImage]를 바이트로 인코딩한다. */
internal object ImageEncoder {
    fun encode(image: BufferedImage, encoding: ImageEncoding): ByteArray {
        val scaled = encoding.scaledSize(image.width, image.height)?.let { (w, h) -> resize(image, w, h) } ?: image
        val stream = ByteArrayOutputStream()
        when (encoding.format) {
            ImageEncoding.Format.PNG -> ImageIO.write(scaled, "png", stream)
            ImageEncoding.Format.JPEG -> writeJpeg(toRgb(scaled), encoding.quality, stream)
        }
        return stream.toByteArray()
    }

    private fun resize(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val type = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        return BufferedImage(width, height, type).also { out ->
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(image, 0, 0, width, height, null)
            g.dispose()
        }
    }

    /** JPEG는 알파를 지원하지 않아 RGB로 옮긴다(알파가 있으면 ImageIO가 쓰기를 거부한다). */
    private fun toRgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_RGB) return image
        return BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { out ->
            val g = out.createGraphics()
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null)
            g.dispose()
        }
    }

    private fun writeJpeg(image: BufferedImage, quality: Int, stream: ByteArrayOutputStream) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(stream).use { output ->
                writer.output = output
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality.coerceIn(0, 100) / 100f
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
    }
}
