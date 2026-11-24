package com.nuvio.app.features.youtube

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The hold menu's actions, the same addon endpoints NuvioTV uses: Not interested
 * (/ytnotinterested), Block video (/ytblock) and Forget I searched this (/ytforgetsearch).
 * Each writes to the profile's addon account; the addon then leaves the video (or the search)
 * out everywhere, on every device.
 */
object YoutubeVideoActions {
    private val log = Logger.withTag("YoutubeVideoActions")
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data object Ok : Result
        data class Failed(val message: String) : Result
    }

    suspend fun notInterested(base: String, videoId: String, title: String?): Result =
        call(base, "ytnotinterested", "v=${encode(videoId)}&n=${encode(title.orEmpty())}")

    suspend fun blockVideo(base: String, videoId: String, title: String?): Result =
        call(base, "ytblock", "v=${encode(videoId)}&n=${encode(title.orEmpty())}")

    suspend fun forgetSearch(base: String, query: String): Result =
        call(base, "ytforgetsearch", "q=${encode(query)}")

    private suspend fun call(base: String, resource: String, query: String): Result = withContext(Dispatchers.Default) {
        try {
            val body = httpGetText("${base.trimEnd('/')}/$resource?$query")
            val parsed = json.parseToJsonElement(body) as? JsonObject
            if (parsed?.get("ok")?.jsonPrimitive?.booleanOrNull == true) {
                Result.Ok
            } else {
                Result.Failed(parsed?.get("err")?.jsonPrimitive?.contentOrNull ?: "Couldn't save that.")
            }
        } catch (t: Throwable) {
            log.w(t) { "YouTube $resource call failed" }
            Result.Failed(t.message ?: "Couldn't save that.")
        }
    }

    private fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if ((ch.isLetterOrDigit() && ch.code < 128) || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                append(ch)
            } else {
                append('%')
                append(c.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
