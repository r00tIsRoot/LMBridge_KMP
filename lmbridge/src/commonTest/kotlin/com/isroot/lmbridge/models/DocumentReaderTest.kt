package com.isroot.lmbridge.models

import com.isroot.lmbridge.tempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** DocumentReader(존재·크기·바이너리) 단위 테스트. */
class DocumentReaderTest {

    @Test
    fun readsUtf8TextContent() {
        val file = tempFile("안녕하세요 hello".encodeToByteArray())
        assertEquals("안녕하세요 hello", DocumentReader.read(file))
    }

    @Test
    fun missingFile_throwsInvalidInput() {
        val ex = assertFailsWith<LMBridgeError.InvalidInput> { DocumentReader.read("/no/such/path/does-not-exist.txt") }
        assertTrue(ex.message!!.contains("not found or unreadable"))
    }

    @Test
    fun oversizeFile_throwsInvalidInput() {
        val file = tempFile(ByteArray(64) { 'a'.code.toByte() })
        val ex = assertFailsWith<LMBridgeError.InvalidInput> { DocumentReader.read(file, maxBytes = 32) }
        assertTrue(ex.message!!.contains("too large"))
    }

    @Test
    fun binaryFileWithNulByte_throwsInvalidInput() {
        val file = tempFile(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0, 3, 4), suffix = ".bin")
        val ex = assertFailsWith<LMBridgeError.InvalidInput> { DocumentReader.read(file) }
        assertTrue(ex.message!!.contains("binary"))
    }

    @Test
    fun unknownCharset_throwsInvalidInput() {
        val file = tempFile("abc".encodeToByteArray())
        val ex = assertFailsWith<LMBridgeError.InvalidInput> { DocumentReader.read(file, charset = "NO-SUCH-CHARSET") }
        assertTrue(ex.message!!.contains("charset"))
    }
}
