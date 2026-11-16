package com.isroot.lmbridge

import com.isroot.lmbridge.download.ModelDownloadManager
import com.isroot.lmbridge.download.ModelStore
import com.isroot.lmbridge.inference.EngineInit
import com.isroot.lmbridge.inference.EngineSessionChat
import com.isroot.lmbridge.inference.InferenceEngine
import com.isroot.lmbridge.models.Chat
import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.InputLimits
import com.isroot.lmbridge.models.LMBridgeError
import com.isroot.lmbridge.models.MultimodalInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * LMBridge 진입점(Facade).
 *
 * 생명주기: `build() → initialize() → generate()/newChat() → release()`.
 * 모든 공개 시그니처는 litertlm 타입을 노출하지 않는다(어댑터 경계).
 */
@OptIn(ExperimentalAtomicApi::class)
class LMBridgeClient private constructor(
    private val context: PlatformContext,
    private val modelPath: String?,
    private val modelInfo: ModelDownloadManager.ModelInfo?,
    private val backend: LMBridge.Backend,
    private val maxNumTokens: Int?,
    private val enableSpeculativeDecoding: Boolean,
    private val maxNumImages: Int?,
    private val visionBackend: LMBridge.Backend?,
    private val audioBackend: LMBridge.Backend?,
    private val maxInputBytesOverride: Long?,
    private val enableBenchmark: Boolean,
    private val cpuThreadCount: Int?,
) {
    private enum class State { IDLE, READY, RELEASED }

    private val engine: InferenceEngine = createInferenceEngine()
    private val store = ModelStore(context)
    private val initMutex = Mutex()

    @Volatile
    private var state: State = State.IDLE

    /** 진행 중인 단발 생성 세션들. 여러 단발 생성이 동시에 돌 수 있어 집합으로 추적한다. */
    private val oneShotChats = AtomicReference<Set<EngineSessionChat>>(emptySet())

    /** 모델 다운로드 관리자. */
    val models: ModelDownloadManager = ModelDownloadManager(context, store)

    /**
     * 엔진을 초기화한다(off-main, 멱등).
     * 이미 초기화된 경우 아무 것도 하지 않는다.
     *
     * @throws LMBridgeError.Released release() 이후 호출 시
     * @throws LMBridgeError.ModelNotFound 모델을 찾을 수 없을 때
     * @throws LMBridgeError.InvalidModel 모델 파일이 손상되었거나 읽을 수 없을 때(백엔드를 바꿔도 실패)
     * @throws LMBridgeError.BackendUnavailable 요청한 백엔드로 엔진을 만들 수 없을 때. 다른 백엔드로
     *   새 클라이언트를 만들어 재시도할 수 있다(예: GPU 실패 → CPU).
     * @throws LMBridgeError.OutOfMemory 엔진 생성 중 메모리가 부족할 때
     */
    suspend fun initialize() = initMutex.withLock {
        when (state) {
            State.READY -> return@withLock
            State.RELEASED -> throw LMBridgeError.Released()
            State.IDLE -> Unit
        }
        val env = context.environment()
        engine.initialize(
            EngineInit(
                modelPath = resolveModelPath(),
                backend = backend,
                maxTokens = maxNumTokens,
                cacheDir = env.cacheDir,
                enableSpeculativeDecoding = enableSpeculativeDecoding,
                maxNumImages = maxNumImages,
                visionBackend = visionBackend,
                audioBackend = audioBackend,
                // 큰 미디어 바이트가 저사양 기기에서 OOM/네이티브 크래시로 이어지기 전에 InvalidInput으로 막는다.
                maxInputBytes = maxInputBytesOverride
                    ?: InputLimits.budgetForDevice(env.totalMemoryBytes, env.isLowRamDevice),
                nativeLibraryDir = env.nativeLibraryDir,
                enableBenchmark = enableBenchmark,
                cpuThreadCount = cpuThreadCount,
            ),
        )
        state = State.READY
    }

    /** 텍스트 프롬프트로 단발 생성(상태 비유지). */
    fun generate(prompt: String, config: ChatConfig = ChatConfig()): Flow<GenerationChunk> =
        generate(MultimodalInput.text(prompt), config)

    /**
     * 멀티모달 입력으로 단발 생성(상태 비유지).
     *
     * 매 호출마다 **독립된 임시 세션**을 열고 끝나면 닫는다. 열려 있는 [Chat]들의 문맥(KV 캐시)은
     * 건드리지 않으므로, 대화 도중 사실 추출·요약·판단 같은 부수 작업을 대화에 끼워 넣지 말고
     * 이 메서드로 실행한다(같은 대화에 큰 지시를 섞으면 이후 응답이 오염된다).
     * 엔진은 생성을 직렬로 처리하므로, 진행 중인 대화 응답이 있으면 끝난 뒤 실행된다.
     *
     * @param config 임시 세션 설정(시스템 지시·샘플러·출력 상한 등). [ChatConfig.history]로 퓨샷 예시를 줄 수 있다.
     */
    fun generate(input: MultimodalInput, config: ChatConfig = ChatConfig()): Flow<GenerationChunk> = flow {
        ensureReady()
        val chat = EngineSessionChat(engine.newSession(config))
        updateOneShots { it + chat }
        try {
            emitAll(chat.send(input))
        } finally {
            updateOneShots { it - chat }
            chat.close()
        }
    }

    /**
     * [generate]의 결과를 답변 텍스트로 모아 반환한다(추론·도구 호출 청크는 제외).
     *
     * @throws LMBridgeError 생성 중 오류가 나면 그 오류
     */
    suspend fun generateText(prompt: String, config: ChatConfig = ChatConfig()): String =
        generateText(MultimodalInput.text(prompt), config)

    /** 멀티모달 입력 버전의 [generateText]. */
    suspend fun generateText(input: MultimodalInput, config: ChatConfig = ChatConfig()): String =
        buildString {
            generate(input, config).collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Token -> append(chunk.text)
                    is GenerationChunk.Error -> throw chunk.error
                    else -> Unit
                }
            }
        }

    /** 상태를 유지하는 새 대화 세션을 만든다. 사용 후 [Chat.close]로 해제한다. */
    fun newChat(config: ChatConfig = ChatConfig()): Chat {
        ensureReady()
        return EngineSessionChat(engine.newSession(config))
    }

    /** [generate]로 진행 중인 단발 생성을 모두 취소한다(대화 세션은 각 [Chat.stop] 사용). */
    fun stop() {
        oneShotChats.load().forEach { it.stop() }
    }

    /** 엔진과 모든 자원을 해제한다. 이후 재사용할 수 없다. */
    fun release() {
        oneShotChats.exchange(emptySet()).forEach { it.close() }
        engine.close()
        state = State.RELEASED
    }

    private inline fun updateOneShots(transform: (Set<EngineSessionChat>) -> Set<EngineSessionChat>) {
        while (true) {
            val current = oneShotChats.load()
            if (oneShotChats.compareAndSet(current, transform(current))) return
        }
    }

    private fun ensureReady() {
        when (state) {
            State.READY -> Unit
            State.RELEASED -> throw LMBridgeError.Released()
            State.IDLE -> throw LMBridgeError.NotInitialized()
        }
    }

    /**
     * 모델 경로 확정 순서:
     * 1) 명시적 [modelPath]가 존재하면 사용
     * 2) [modelInfo]가 로컬에 존재하면 그 경로
     * 3) 앱 번들 기본 모델([DEFAULT_MODEL_FILE], 번들을 지원하는 플랫폼만)
     */
    private fun resolveModelPath(): String {
        if (!modelPath.isNullOrEmpty() && SystemFileSystem.exists(Path(modelPath))) return modelPath

        modelInfo?.let { info ->
            store.pathIfPresent(info)?.let { return it }
            throw LMBridgeError.ModelNotFound(
                "Model ${info.modelId} is not downloaded. Use client.models.downloadModel(...) first.",
            )
        }

        return context.extractBundledModel(DEFAULT_MODEL_FILE)
            ?: throw LMBridgeError.ModelNotFound(modelPath ?: DEFAULT_MODEL_FILE)
    }

    class Builder(private val context: PlatformContext) {
        private var modelPath: String? = null
        private var modelInfo: ModelDownloadManager.ModelInfo? = null
        private var backend: LMBridge.Backend = LMBridge.Backend.CPU
        private var maxNumTokens: Int? = null
        private var enableSpeculativeDecoding: Boolean = false
        private var maxNumImages: Int? = null
        private var visionBackend: LMBridge.Backend? = null
        private var audioBackend: LMBridge.Backend? = null
        private var maxInputBytesOverride: Long? = null
        private var enableBenchmark: Boolean = false
        private var cpuThreadCount: Int? = null

        /** 로컬 모델 파일 경로를 직접 지정. */
        fun setModelPath(path: String): Builder = apply { modelPath = path }

        /** 카탈로그(또는 커스텀) 모델을 지정. initialize()가 로컬 존재를 확인하여 로드. */
        fun setModel(info: ModelDownloadManager.ModelInfo): Builder = apply { modelInfo = info }

        fun setBackend(backend: LMBridge.Backend): Builder = apply { this.backend = backend }

        /**
         * 총 컨텍스트(입력+출력 합산) 토큰 상한 = KV 캐시 길이.
         *
         * **설정하지 않으면(기본 null) 각 모델 번들에 내장된 최대값(파일명 `ekvNNNN`,
         * 예: ekv4096)을 그대로 사용한다.** 값을 지정하면 그 값으로 상한을 낮춰 KV 캐시
         * 메모리를 줄일 수 있다(단, 큰 문서/오디오는 잘림). 양수만 유효하다.
         */
        fun setMaxNumTokens(maxNumTokens: Int): Builder = apply { this.maxNumTokens = maxNumTokens }

        /**
         * MTP(Multi-Token Prediction, speculative decoding) 사용 여부. 기본값 false.
         *
         * MTP 드래프터를 포함한 모델(예: Gemma 4 E2B/E4B)에서만 켜야 한다. 일반
         * litert-community `.litertlm`에서 켜면 초기화가 `embedding_lookup == nullptr`로 실패한다.
         */
        fun setEnableSpeculativeDecoding(enabled: Boolean): Builder =
            apply { this.enableSpeculativeDecoding = enabled }

        /**
         * 비전(이미지) 입력 활성화 — 엔진이 미리 확보할 이미지 슬롯 수.
         *
         * **멀티모달(비전) 모델에 이미지를 넣으려면 반드시 1 이상으로 설정해야 한다.**
         * 설정하지 않으면 엔진이 `max_num_images: 0`으로 초기화되어, 이미지를 주입하는 순간
         * 네이티브에서 크래시한다. 텍스트 전용 모델에서는 설정하지 않는다.
         */
        fun setMaxNumImages(maxNumImages: Int): Builder =
            apply { this.maxNumImages = maxNumImages }

        /** 비전 인코더 백엔드를 메인 백엔드와 다르게 지정(선택). 기본은 메인 백엔드를 따른다. */
        fun setVisionBackend(backend: LMBridge.Backend): Builder =
            apply { this.visionBackend = backend }

        /** 오디오 입력 활성화 — 오디오 인코더 백엔드 지정. 설정하지 않으면 오디오가 비활성이다. */
        fun setAudioBackend(backend: LMBridge.Backend): Builder =
            apply { this.audioBackend = backend }

        /**
         * 인메모리 입력(오디오/이미지 바이트·문서 텍스트) 1건당 최대 바이트를 직접 지정.
         *
         * 설정하지 않으면 초기화 시점의 기기 총 메모리로 자동 산출한다
         * (≤4GB→4MB, ≤6GB→16MB, >6GB→64MB, 저사양 기기는 한 단계 낮춤).
         * 대용량 미디어는 파일 경로 소스(`imageFile`/`audioFile`)를 쓰면 이 제한을 우회한다.
         */
        fun setMaxInputBytes(bytes: Long): Builder =
            apply { this.maxInputBytesOverride = bytes }

        /**
         * 처리 속도 통계 수집 여부. 기본값 false.
         *
         * 켜면 [Chat.stats]가 prefill/decode 토큰 수와 tokens/s를 함께 반환한다.
         * (첫 토큰 지연(TTFT)은 이 설정과 무관하게 항상 실측된다.)
         */
        fun setEnableBenchmark(enabled: Boolean): Builder =
            apply { this.enableBenchmark = enabled }

        /**
         * CPU 백엔드 추론 스레드 수. 설정하지 않으면 litertlm 기본값을 쓴다.
         *
         * 기본값은 코어 수보다 적게 잡힐 수 있다. 8코어 Android 기기(Gemma 4 E2B CPU)에서는
         * 4~6 스레드가 기본값 대비 prefill 약 1.6배였다. 최적값은 기기마다 다르므로 측정해 정한다.
         */
        fun setCpuThreadCount(threadCount: Int): Builder = apply {
            require(threadCount > 0) { "threadCount must be positive: $threadCount" }
            this.cpuThreadCount = threadCount
        }

        fun build(): LMBridgeClient =
            LMBridgeClient(
                context,
                modelPath,
                modelInfo,
                backend,
                maxNumTokens,
                enableSpeculativeDecoding,
                maxNumImages,
                visionBackend,
                audioBackend,
                maxInputBytesOverride,
                enableBenchmark,
                cpuThreadCount,
            )
    }

    companion object {
        private const val DEFAULT_MODEL_FILE = "gemma-4-E2B-it.litertlm"
    }
}
