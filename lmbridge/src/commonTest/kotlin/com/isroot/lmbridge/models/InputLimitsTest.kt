package com.isroot.lmbridge.models

import kotlin.test.Test
import kotlin.test.assertEquals

/** InputLimits의 기기-적응형 예산 티어 로직 단위 테스트. */
class InputLimitsTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun lowTier_upTo4GB() {
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(4 * gb, isLowRamDevice = false))
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(3 * gb, isLowRamDevice = false))
    }

    @Test
    fun midTier_upTo6GB() {
        assertEquals(InputLimits.TIER_MID, InputLimits.budgetForDevice(4 * gb + 1, isLowRamDevice = false))
        assertEquals(InputLimits.TIER_MID, InputLimits.budgetForDevice(6 * gb, isLowRamDevice = false))
    }

    @Test
    fun highTier_above6GB() {
        assertEquals(InputLimits.TIER_HIGH, InputLimits.budgetForDevice(8 * gb, isLowRamDevice = false))
    }

    @Test
    fun lowRamFlag_demotesOneTier_butNotBelowLowest() {
        assertEquals(InputLimits.TIER_MID, InputLimits.budgetForDevice(8 * gb, isLowRamDevice = true))
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(6 * gb, isLowRamDevice = true))
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(3 * gb, isLowRamDevice = true))
    }
}
