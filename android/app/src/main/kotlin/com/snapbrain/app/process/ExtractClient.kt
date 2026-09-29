package com.snapbrain.app.process

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.auth.auth
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ExtractOutcome
import com.snapbrain.core.outcomeOfCode
import com.snapbrain.core.truncateForApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

class ExtractClient(private val deviceId: String, private val baseUrl: String) {
    suspend fun extract(itemId: String, ocrText: String): ExtractOutcome = try {
        val auth = Firebase.auth
        if (auth.currentUser == null) auth.signInAnonymously().await()
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
        // Beta (spec S20): a phone without a registered App Check token still sends; the server decides.
        val appCheck = try {
            Firebase.appCheck.getAppCheckToken(false).await().token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Extract", "AppCheck ${e.javaClass.simpleName}")
            null
        }
        if (idToken == null) {
            ExtractOutcome.Retryable
        } else {
            val body = JSONObject(
                mapOf(
                    "ocr_text" to truncateForApi(ocrText),
                    "device_id" to deviceId,
                    "item_id" to itemId,
                    "today" to LocalDate.now().toString(),
                    "tz" to ZoneId.systemDefault().id,
                ),
            ).toString()
            val (code, text) = withContext(Dispatchers.IO) { post("$baseUrl/extract", body, idToken, appCheck) }
            if (code in 200..299) {
                ExtractOutcome.Success(ExtractJson.parse(text))
            } else {
                val error = runCatching { JSONObject(text).getString("error") }.getOrDefault("INTERNAL")
                Log.w("Extract", error)
                if (error == "UNAUTHENTICATED") auth.signOut()
                outcomeOfCode(error)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Network, auth or unexpected payload: retry with backoff; never log the message (may echo OCR text).
        Log.w("Extract", e.javaClass.simpleName)
        ExtractOutcome.Retryable
    }

    private fun post(url: String, body: String, idToken: String, appCheck: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("authorization", "Bearer $idToken")
            if (appCheck != null) conn.setRequestProperty("x-firebase-appcheck", appCheck)
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }
}
