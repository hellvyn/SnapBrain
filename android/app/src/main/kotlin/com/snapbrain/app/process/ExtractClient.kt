package com.snapbrain.app.process

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ExtractOutcome
import com.snapbrain.core.outcomeOfCode
import com.snapbrain.core.truncateForApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class ExtractClient(private val deviceId: String) {
    private val functions = Firebase.functions("asia-southeast2")

    suspend fun extract(itemId: String, ocrText: String): ExtractOutcome = try {
        if (Firebase.auth.currentUser == null) Firebase.auth.signInAnonymously().await()
        val payload = mapOf("ocr_text" to truncateForApi(ocrText), "device_id" to deviceId, "item_id" to itemId)
        val result = functions.getHttpsCallable("extract").call(payload).await()
        val json = JSONObject(result.data as Map<*, *>).toString()
        ExtractOutcome.Success(ExtractJson.parse(json))
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFunctionsException) {
        outcomeOfCode(e.code.name)
    } catch (e: Exception) {
        // Network, auth or unexpected payload: worth retrying with backoff; never log OCR text here.
        ExtractOutcome.Retryable
    }
}
