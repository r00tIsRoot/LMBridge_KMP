package com.isroot.lmbridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.ImageEncoding
import com.isroot.lmbridge.models.LMBridgeError
import com.isroot.lmbridge.models.MultimodalContent
import com.isroot.lmbridge.models.MultimodalInput
import com.isroot.lmbridge.models.Sampler
import com.isroot.lmbridge.models.image
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Android(litertlm-android)에서 실제 모델로 엔진을 돌리는 계측 테스트.
 *
 * 인자: `modelPath`(기본 /data/local/tmp/lmbridge/smol135.litertlm), `backend`(기본 GPU).
 * 모델 파일이 없으면 모델이 필요한 테스트는 건너뛴다.
 */
@RunWith(AndroidJUnit4::class)
class AndroidEngineTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val context = instrumentation.targetContext
    private val modelPath = args.getString("modelPath") ?: "/data/local/tmp/lmbridge/smol135.litertlm"
    private val backend = LMBridge.Backend.valueOf(args.getString("backend") ?: "GPU")

    private val config = ChatConfig(sampler = Sampler(topK = 1, temperature = 0.0), maxOutputTokens = 32)

    private fun client() = LMBridgeClient.Builder(context)
        .setModelPath(modelPath)
        .setBackend(backend)
        .setMaxNumTokens(512)
        .build()

    @Test
    fun generateAndChat_onDevice(): Unit = runBlocking {
        assumeTrue("model not found: $modelPath", File(modelPath).isFile)
        val client = client()
        try {
            client.initialize()
            val chunks = client.generate("Say hello.", config).toList()
            val text = chunks.filterIsInstance<GenerationChunk.Token>().joinToString("") { it.text }
            println("[$backend] text=$text")
            assertIs<GenerationChunk.Done>(chunks.last())
            assertTrue(text.isNotBlank())

            val chat = client.newChat(config)
            chat.send("My name is Minsu.").toList()
            val first = chat.stats().contextTokens
            chat.send("What is my name?").toList()
            val stats = chat.stats()
            println("[$backend] context $first -> ${stats.contextTokens}, ttft=${stats.timeToFirstTokenSeconds}s")
            assertTrue(stats.contextTokens > first)
            chat.close()
        } finally {
            client.release()
        }
    }

    @Test
    fun garbageModel_isInvalidModel_withoutCrash(): Unit = runBlocking {
        val garbage = File(context.cacheDir, "garbage.litertlm").apply { writeBytes(ByteArray(1024) { (it * 31).toByte() }) }
        val client = LMBridgeClient.Builder(context).setModelPath(garbage.absolutePath).build()
        assertFailsWith<LMBridgeError.InvalidModel> { client.initialize() }
        client.release()
        garbage.delete()
    }

    @Test
    fun bitmapImage_isEncodedWithPolicy() {
        val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        val input = MultimodalInput.Builder().image(bitmap, ImageEncoding.COMPACT_JPEG).build()
        val bytes = (input.parts.single() as MultimodalContent.ImageBytes).bytes
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1024, decoded.width)
        assertEquals(512, decoded.height)
    }

    @Test
    fun modelStore_usesAppExternalFilesDir() {
        val models = LMBridgeClient.Builder(context).build().models
        val root = (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath
        val info = com.isroot.lmbridge.models.ModelCatalog.ALL.first()
        assertTrue(ModelStoreProbe.fileFor(context, info).startsWith(root))
        assertTrue(models.getAvailableSpace() > 0)
    }
}

private object ModelStoreProbe {
    fun fileFor(context: PlatformContext, info: com.isroot.lmbridge.download.ModelDownloadManager.ModelInfo): String =
        com.isroot.lmbridge.download.ModelStore(context).fileFor(info)
}
