package com.snapbrain.core

import java.security.MessageDigest

enum class ItemStatus { UNPROCESSED, DONE, QUOTA_BLOCKED, FAILED }

sealed interface ExtractOutcome {
    data class Success(val response: ExtractResponse) : ExtractOutcome
    data object QuotaExhausted : ExtractOutcome
    data object Retryable : ExtractOutcome
    data object Invalid : ExtractOutcome
}

const val MAX_ATTEMPTS = 5
const val MIN_OCR_CHARS = 10
const val MAX_OCR_CHARS = 20_000

/** [code] is the Worker error code (same names as Firebase Functions codes). */
fun outcomeOfCode(code: String): ExtractOutcome = when (code) {
    "RESOURCE_EXHAUSTED" -> ExtractOutcome.QuotaExhausted
    "INVALID_ARGUMENT" -> ExtractOutcome.Invalid
    else -> ExtractOutcome.Retryable
}

/** [attempts] counts retryable failures including this one. */
fun statusAfter(outcome: ExtractOutcome, attempts: Int): ItemStatus = when (outcome) {
    is ExtractOutcome.Success -> ItemStatus.DONE
    ExtractOutcome.QuotaExhausted -> ItemStatus.QUOTA_BLOCKED
    ExtractOutcome.Invalid -> ItemStatus.FAILED
    ExtractOutcome.Retryable -> if (attempts >= MAX_ATTEMPTS) ItemStatus.FAILED else ItemStatus.UNPROCESSED
}

fun needsAi(ocrText: String): Boolean = ocrText.trim().length >= MIN_OCR_CHARS

fun truncateForApi(ocrText: String): String = ocrText.trim().take(MAX_OCR_CHARS)

/** Backend expects SHA-256(ANDROID_ID) as 64 lowercase hex chars. */
fun deviceIdOf(androidId: String): String =
    MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray()).joinToString("") { "%02x".format(it) }

/** MediaStore.createDeleteRequest needs API 30+ and a MediaStore uri (authority "media"). */
fun canDeleteOriginal(sdkInt: Int, authority: String?): Boolean = sdkInt >= 30 && authority == "media"

/** Spec S4/S17: Belanja, To-do, Bandingkan and Budget are Pro; everything stays open until Plan 3 adds billing. */
const val IS_PRO = true
