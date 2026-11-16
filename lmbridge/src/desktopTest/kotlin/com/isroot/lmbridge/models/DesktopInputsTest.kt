package com.isroot.lmbridge.models

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopInputsTest {

    @Test
    fun document_honorsNonUtf8Charset() {
        val file = File.createTempFile("doc", ".txt").apply {
            writeBytes("가나다".toByteArray(charset("EUC-KR")))
            deleteOnExit()
        }
        assertEquals("가나다", DocumentReader.read(file.absolutePath, charset = "EUC-KR"))
    }

    @Test
    fun compactJpeg_downscalesLongSideAndDropsAlpha() {
        val image = BufferedImage(2048, 1024, BufferedImage.TYPE_INT_ARGB)
        val input = MultimodalInput.Builder().image(image, ImageEncoding.COMPACT_JPEG).build()
        val bytes = (input.parts.single() as MultimodalContent.ImageBytes).bytes
        val decoded = ImageIO.read(bytes.inputStream())
        assertEquals(1024, decoded.width)
        assertEquals(512, decoded.height)
        // JPEG SOI 마커
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
    }
}
