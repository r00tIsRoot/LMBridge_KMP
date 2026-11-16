package com.isroot.lmbridge

import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.LMBridgeError
import com.isroot.lmbridge.models.Sampler
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 실제 모델로 데스크톱(litertlm-jvm) 엔진을 돌리는 통합 테스트.
 *
 * `LMBRIDGE_TEST_MODEL`(작은 `.litertlm` 경로, 예: SmolLM2-135M)이 없으면 건너뛴다.
 */
class DesktopEngineTest {

    private val modelPath: String? = System.getenv("LMBRIDGE_TEST_MODEL")?.takeIf { File(it).isFile }
    private val context = DesktopContext(Files.createTempDirectory("lmbridge-desktop").toFile())

    private fun client(backend: LMBridge.Backend = LMBridge.Backend.CPU) = LMBridgeClient.Builder(context)
        .setModelPath(modelPath!!)
        .setBackend(backend)
        .setMaxNumTokens(512)
        .build()

    // 작은 모델이라 답의 정확도는 보지 않는다. 결정적 샘플링으로 "응답이 나온다"만 확인한다.
    private val config = ChatConfig(sampler = Sampler(topK = 1, temperature = 0.0), maxOutputTokens = 32)

    @Test
    fun generateText_streamsAnswerOnCpu() = runBlocking {
        assumeTrue("LMBRIDGE_TEST_MODEL not set", modelPath != null)
        val client = client()
        try {
            client.initialize()
            val chunks = client.generate("Say hello.", config).toList()
            println("chunks=${chunks.size} text=" + chunks.filterIsInstance<GenerationChunk.Token>().joinToString("") { it.text })
            assertIs<GenerationChunk.Done>(chunks.last())
            assertTrue(chunks.any { it is GenerationChunk.Token && it.text.isNotBlank() })
        } finally {
            client.release()
        }
    }

    @Test
    fun chat_keepsContextAcrossTurns_andReportsStats() = runBlocking {
        assumeTrue("LMBRIDGE_TEST_MODEL not set", modelPath != null)
        val client = client()
        try {
            client.initialize()
            val a = client.newChat(config)
            val b = client.newChat(config)
            a.send("My name is Minsu.").toList()
            val firstTokens = a.stats().contextTokens
            b.send("Hi.").toList()
            a.send("What is my name?").toList()
            val stats = a.stats()
            println("A context: $firstTokens -> ${stats.contextTokens}, ttft=${stats.timeToFirstTokenSeconds}s")
            assertTrue(stats.contextTokens > firstTokens, "second turn must grow the same conversation")
            assertTrue((stats.timeToFirstTokenSeconds ?: 0.0) > 0.0)
            a.close()
            b.close()
        } finally {
            client.release()
        }
    }

    @Test
    fun gpuBackend_eitherWorksOrIsBackendUnavailable() = runBlocking {
        assumeTrue("LMBRIDGE_TEST_MODEL not set", modelPath != null)
        val client = client(LMBridge.Backend.GPU)
        try {
            val result = runCatching { client.initialize() }
            println("GPU init: ${result.exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message}" } ?: "OK"}")
            result.exceptionOrNull()?.let { assertIs<LMBridgeError.BackendUnavailable>(it) }
            if (result.isSuccess) {
                val text = client.generateText("Say hello.", config)
                println("GPU text=$text")
                assertTrue(text.isNotBlank())
            }
        } finally {
            client.release()
        }
    }

    @Test
    fun garbageModel_isInvalidModel_withoutCrash() = runBlocking {
        val garbage = File.createTempFile("garbage", ".litertlm").apply {
            writeBytes(ByteArray(1024) { (it * 31).toByte() })
            deleteOnExit()
        }
        val client = LMBridgeClient.Builder(context).setModelPath(garbage.absolutePath).build()
        assertFailsWith<LMBridgeError.InvalidModel> { client.initialize() }
        client.release()
    }
}
