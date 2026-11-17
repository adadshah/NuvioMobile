package com.nuvio.app.features.details

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
 * The KidSafe/WatchFilter/AnimeFilter addon's channel/block/subscribe actions for YouTube items
 * (see [YoutubeBlockInfo]): Block channel, Block video, and Subscribe (adds the channel to
 * YouTube Interests) all write straight to the user's addon account over a plain GET, the same
 * endpoints NuvioTV's `youtubeBlock.js` uses.
 */
object YoutubeBlockActions {
    private val log = Logger.withTag("YoutubeBlockActions")
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data object Ok : Result
        data class Failed(val message: String) : Result
    }

    suspend fun blockChannel(info: YoutubeBlockInfo): Result {
        val ch = info.channelId?.takeIf { it.isNotBlank() } ?: return Result.Failed("Nothing to block.")
        val query = buildString {
            append("c=").append(encode(ch))
            append("&n=").append(encode(info.channelName ?: ch))
            info.channelThumb?.takeIf { it.isNotBlank() }?.let { append("&t=").append(encode(it)) }
        }
        return call(info.base, "ytblock", query)
    }

    suspend fun blockVideo(info: YoutubeBlockInfo): Result {
        val video = info.videoId?.takeIf { it.isNotBlank() } ?: return Result.Failed("Nothing to block.")
        val query = buildString {
            append("v=").append(encode(video))
            append("&n=").append(encode(info.videoTitle ?: video))
            info.channelName?.takeIf { it.isNotBlank() }?.let { append("&cn=").append(encode(it)) }
        }
        return call(info.base, "ytblock", query)
    }

    suspend fun subscribe(info: YoutubeBlockInfo): Result {
        val ch = info.channelId?.takeIf { it.isNotBlank() } ?: return Result.Failed("Nothing to subscribe to.")
        val query = buildString {
            append("c=").append(encode(ch))
            append("&n=").append(encode(info.channelName ?: ch))
            info.channelThumb?.takeIf { it.isNotBlank() }?.let { append("&t=").append(encode(it)) }
        }
        return call(info.base, "ytsubscribe", query)
    }

    private suspend fun call(base: String, resource: String, query: String): Result = withContext(Dispatchers.Default) {
        try {
            val url = "${base.trimEnd('/')}/$resource?$query"
            val body = httpGetText(url)
            val parsed = json.parseToJsonElement(body) as? JsonObject
            val ok = parsed?.get("ok")?.jsonPrimitive?.booleanOrNull == true
            if (ok) {
                Result.Ok
            } else {
                Result.Failed(parsed?.get("err")?.jsonPrimitive?.contentOrNull ?: "Couldn't do that.")
            }
        } catch (t: Throwable) {
            log.w(t) { "YouTube $resource call failed" }
            Result.Failed(t.message ?: "Couldn't do that.")
        }
    }

    // A small percent-encoder for query values (avoids pulling in a platform URL encoder).
    private fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && ch.code < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                append(ch)
            } else {
                append('%')
                append(c.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
