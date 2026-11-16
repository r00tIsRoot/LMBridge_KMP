package com.isroot.lmbridge.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ModelInfo의 순수 로직(URL/디렉터리 규칙) 단위 테스트. */
class ModelInfoTest {

    private val model = ModelDownloadManager.ModelInfo(
        modelId = "litert-community/Gemma3-1B-IT",
        modelFile = "gemma3-1b-it-int4.litertlm",
        commitHash = "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
        sizeInBytes = 584417280,
    )

    @Test
    fun downloadUrl_pointsToResolveEndpointWithCommitAndFile() {
        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/" +
                "42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
            model.toDownloadUrl(),
        )
    }

    @Test
    fun dirName_replacesSlashAndUsesShortCommit() {
        val dir = model.toDirName()
        assertEquals("litert-community_Gemma3-1B-IT_42d538a9", dir)
        assertTrue(!dir.contains("/"), "dir name must not contain path separators")
    }

    @Test
    fun sha256_defaultsToNull() {
        assertNull(model.sha256)
    }
}
