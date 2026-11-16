package com.isroot.lmbridge.download

import com.isroot.lmbridge.tempDir
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelStoreTest {

    private val model = ModelDownloadManager.ModelInfo(
        modelId = "org/Model",
        modelFile = "model.litertlm",
        commitHash = "0123456789abcdef0123456789abcdef01234567",
    )

    @Test
    fun paths_followDirNameRule_andPresenceTracksFinalFileOnly() {
        val root = tempDir()
        val store = ModelStore(root)

        assertEquals(Path(root, "org_Model_01234567", "model.litertlm").toString(), store.fileFor(model))
        assertEquals(store.fileFor(model) + ".part", store.tempFileFor(model))
        assertNull(store.pathIfPresent(model))

        SystemFileSystem.sink(Path(store.tempFileFor(model))).buffered().use { it.write(byteArrayOf(1)) }
        assertFalse(store.isPresent(model), ".part alone must not count as downloaded")

        SystemFileSystem.atomicMove(Path(store.tempFileFor(model)), Path(store.fileFor(model)))
        assertEquals(store.fileFor(model), store.pathIfPresent(model))

        assertTrue(store.delete(model))
        assertFalse(SystemFileSystem.exists(Path(root, "org_Model_01234567")))
    }

    @Test
    fun sha256_matchesKnownDigest() {
        val root = tempDir()
        val file = Path(root, "abc.txt")
        SystemFileSystem.sink(file).buffered().use { it.write("abc".encodeToByteArray()) }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Of(file.toString()))
    }
}
