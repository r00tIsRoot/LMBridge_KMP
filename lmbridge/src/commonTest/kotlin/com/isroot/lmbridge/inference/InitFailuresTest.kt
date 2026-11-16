package com.isroot.lmbridge.inference

import com.isroot.lmbridge.LMBridge
import com.isroot.lmbridge.models.LMBridgeError
import com.isroot.lmbridge.tempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 초기화 실패 분류·헤더 검사 단위 테스트. 메시지는 litertlm 0.18 실기기 출력. */
class InitFailuresTest {

    @Test
    fun truncatedModel_isInvalidModel() {
        val e = RuntimeException(
            "Failed to create engine: NOT_FOUND: ERROR: [llm_litert_compiled_model_executor_factory.cc:91]\n" +
                "└ tf_lite_prefill_decode not found in the model.",
        )
        val result = InitFailures.classify(e, LMBridge.Backend.GPU, "/m.litertlm")
        assertIs<LMBridgeError.InvalidModel>(result)
        assertEquals("/m.litertlm", result.path)
    }

    @Test
    fun modelMarkerInCauseChain_isDetected() {
        val e = IllegalStateException("wrapper", RuntimeException("INVALID_ARGUMENT: Invalid magic number or failed to read"))
        assertIs<LMBridgeError.InvalidModel>(InitFailures.classify(e, LMBridge.Backend.CPU, "/m"))
    }

    @Test
    fun unknownFailure_isBackendUnavailable_withBackendKept() {
        val e = RuntimeException("Failed to create engine: INTERNAL: OpenCL not available")
        val result = InitFailures.classify(e, LMBridge.Backend.GPU, "/m")
        assertIs<LMBridgeError.BackendUnavailable>(result)
        assertEquals(LMBridge.Backend.GPU, result.backend)
    }

    @Test
    fun validHeader_passes() {
        assertNull(InitFailures.checkHeader(tempFile("LITERTLM".encodeToByteArray() + ByteArray(24), ".litertlm")))
    }

    @Test
    fun garbageHeader_isRejected() {
        assertNotNull(InitFailures.checkHeader(tempFile(byteArrayOf(0x8a.toByte(), 1, 2, 3, 4, 5, 6, 7, 8), ".litertlm")))
    }

    @Test
    fun fileShorterThanHeader_isRejected() {
        assertNotNull(InitFailures.checkHeader(tempFile("LITE".encodeToByteArray(), ".litertlm")))
    }

    @Test
    fun missingFile_isRejected() {
        assertNotNull(InitFailures.checkHeader("/no/such/model.litertlm"))
    }

    @Test
    fun otherExtensions_areNotChecked() {
        assertNull(InitFailures.checkHeader(tempFile(byteArrayOf(1, 2, 3), ".task")))
    }
}
