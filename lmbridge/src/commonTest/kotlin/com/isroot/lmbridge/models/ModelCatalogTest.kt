package com.isroot.lmbridge.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelCatalogTest {

    @Test
    fun everyEntry_isPinnedToFullRevisionWithIntegrity() {
        ModelCatalog.ALL.forEach { info ->
            assertTrue(info.commitHash.matches(Regex("[0-9a-f]{40}")), "${info.modelId}: commitHash must be a full 40-hex sha")
            assertTrue(info.sizeInBytes > 0, "${info.modelId}: sizeInBytes must be positive")
            assertTrue(info.sha256?.matches(Regex("[0-9a-f]{64}")) == true, "${info.modelId}: sha256 must be 64-hex")
            assertTrue(info.modelFile.endsWith(".litertlm"), "${info.modelId}: modelFile must be .litertlm")
        }
    }

    @Test
    fun modelIds_areUnique() {
        val ids = ModelCatalog.ALL.map { it.modelId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun dirNames_areUnique() {
        val dirs = ModelCatalog.ALL.map { it.toDirName() }
        assertEquals(dirs.size, dirs.toSet().size)
    }
}
