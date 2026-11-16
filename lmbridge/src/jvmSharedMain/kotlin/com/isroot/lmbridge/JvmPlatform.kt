package com.isroot.lmbridge

import com.isroot.lmbridge.inference.InferenceEngine
import com.isroot.lmbridge.inference.LiteRtEngineAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Android와 데스크톱(JVM)이 공유하는 플랫폼 구현.

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

internal actual fun createInferenceEngine(): InferenceEngine = LiteRtEngineAdapter()
