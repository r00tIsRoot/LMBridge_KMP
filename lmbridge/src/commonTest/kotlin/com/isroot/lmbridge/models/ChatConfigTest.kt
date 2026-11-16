package com.isroot.lmbridge.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatConfigTest {

    @Test
    fun defaults_leaveModelDefaultsUntouched() {
        val config = ChatConfig()
        assertNull(config.sampler)
        assertNull(config.thinking)
        assertNull(config.maxOutputTokens)
        assertTrue(config.history.isEmpty())
        assertTrue(config.automaticToolCalling)
    }

    @Test
    fun invalidValues_areRejected() {
        assertFailsWith<IllegalArgumentException> { ChatConfig(maxOutputTokens = 0) }
        assertFailsWith<IllegalArgumentException> { Sampler(topP = 1.5) }
        assertFailsWith<IllegalArgumentException> { Sampler(topK = 0) }
        assertFailsWith<IllegalArgumentException> { Thinking(tokenBudget = -2) }
    }

    @Test
    fun thinking_acceptsUnlimitedDisabledAndPositiveBudgets() {
        assertEquals(Thinking.UNLIMITED, Thinking().tokenBudget)
        assertEquals(0, Thinking(tokenBudget = 0).tokenBudget)
        assertEquals(1024, Thinking(tokenBudget = 1024).tokenBudget)
    }

    @Test
    fun chatMessage_factoriesSetRole() {
        assertEquals(ChatMessage.Role.USER, ChatMessage.user("hi").role)
        assertEquals(ChatMessage.Role.MODEL, ChatMessage.model("hello").role)
    }

    @Test
    fun imageEncoding_scalesOnlyWhenLongestSideExceedsMax() {
        assertEquals(1024 to 512, ImageEncoding(maxDimension = 1024).scaledSize(2048, 1024))
        assertNull(ImageEncoding(maxDimension = 1024).scaledSize(800, 600))
        assertNull(ImageEncoding.LOSSLESS_PNG.scaledSize(4000, 3000))
    }
}
