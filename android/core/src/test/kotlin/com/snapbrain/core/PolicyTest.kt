package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyTest {
    private val ok = ExtractOutcome.Success(ExtractResponse(ExtractData("task", "x"), 0, Quota(1, 15)))

    @Test
    fun mapsFunctionsErrorCodes() {
        assertEquals(ExtractOutcome.QuotaExhausted, outcomeOfCode("RESOURCE_EXHAUSTED"))
        assertEquals(ExtractOutcome.Invalid, outcomeOfCode("INVALID_ARGUMENT"))
        assertEquals(ExtractOutcome.Retryable, outcomeOfCode("UNAVAILABLE"))
        assertEquals(ExtractOutcome.Retryable, outcomeOfCode("INTERNAL"))
    }

    @Test
    fun decidesStatusAfterAnAttempt() {
        assertEquals(ItemStatus.DONE, statusAfter(ok, 1))
        assertEquals(ItemStatus.QUOTA_BLOCKED, statusAfter(ExtractOutcome.QuotaExhausted, 1))
        assertEquals(ItemStatus.FAILED, statusAfter(ExtractOutcome.Invalid, 1))
        assertEquals(ItemStatus.UNPROCESSED, statusAfter(ExtractOutcome.Retryable, MAX_ATTEMPTS - 1))
        assertEquals(ItemStatus.FAILED, statusAfter(ExtractOutcome.Retryable, MAX_ATTEMPTS))
    }

    @Test
    fun needsAiFalseBelow10Chars() {
        assertFalse(needsAi("  hai  "))
        assertTrue(needsAi("Transfer Rp 50.000"))
    }

    @Test
    fun truncatesTo20000Chars() {
        assertEquals(20_000, truncateForApi("a".repeat(25_000)).length)
        assertEquals("abc", truncateForApi("  abc  "))
    }

    @Test
    fun hashesAndroidIdToLowercaseHex() {
        val id = deviceIdOf("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", id)
    }

    @Test
    fun deletesOriginalOnlyFromMediaStoreOnAndroid11Plus() {
        assertTrue(canDeleteOriginal(30, "media"))
        assertFalse(canDeleteOriginal(29, "media"))
        assertFalse(canDeleteOriginal(34, "com.whatsapp.provider.media"))
        assertFalse(canDeleteOriginal(34, null))
    }
}
