package com.nuvio.app.features.youtube

import com.nuvio.app.core.content.AddonContentIds
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What belongs on the YouTube tab, and the small id helpers it needs. Mirrors NuvioTV's
 * js/core/youtube (youtubeItem.js, youtubeHoldMenu.js): YouTube rows, videos and channels come
 * from the KidSafe / Watch Filter / Anime Filter addons with ids containing "yt:".
 */
object YoutubeContent {
    private val channelId = Regex("""(?:^|:)yt:UC[\w-]{22}$""")
    private val videoIdInId = Regex("""(?:^|:)yt:(?:v|mix|srch):([\w-]{11})(?::|$)""")
    private val searchInId = Regex("""(?:^|:)yt:srch:[\w-]{11}:([A-Za-z0-9_-]+)$""")
    private val accountPath = Regex("""/p/[A-Za-z0-9_-]{16,40}(/r\d{1,4})?$""")
    private val avatarSize = Regex("""=s\d+""")

    fun isYoutube(id: String?): Boolean = AddonContentIds.isYoutubeId(id)

    /** A channel card ("…yt:UC…"), shown as a circle. */
    fun isChannel(id: String?): Boolean = id != null && channelId.containsMatchIn(id)

    /** The YouTube video id inside a card id ("yt:v:ID", "yt:mix:ID", "yt:srch:ID:…"), or null. */
    fun videoIdOf(id: String?): String? = id?.let { videoIdInId.find(it)?.groupValues?.get(1) }

    /** The search a search card came from ("yt:srch:ID:<base64url of the search>"), or null. */
    fun searchQueryOf(id: String?): String? =
        id?.let { searchInId.find(it)?.groupValues?.get(1) }
            ?.let { decodeBase64Url(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** A row of YouTube content: most of its cards are YouTube videos or channels. */
    fun isYoutubeSection(section: HomeCatalogSection): Boolean {
        val items = section.items
        if (items.isEmpty()) return false
        return items.count { isYoutube(it.id) } * 2 >= items.size
    }

    /** A row made only of channels: shown at the top of the YouTube tab as circles. */
    fun isChannelSection(section: HomeCatalogSection): Boolean =
        section.items.isNotEmpty() && section.items.all { isChannel(it.id) }

    fun isYoutubeContinueWatching(item: ContinueWatchingItem): Boolean =
        isYoutube(item.parentMetaId) || isYoutube(item.videoId)

    /** The addon's account link ("https://…/p/<token>"): Not interested, Block and Forget need it. */
    fun accountBase(target: CatalogTarget): String? =
        (target as? CatalogTarget.Addon)?.manifestUrl?.let { accountBaseFromManifest(it) }

    fun accountBaseFromManifest(manifestUrl: String): String? {
        val base = manifestUrl.trim().substringBefore('?').removeSuffix("/manifest.json").trimEnd('/')
        return base.takeIf { accountPath.containsMatchIn(it) }
    }

    /** YouTube hands out channel pictures at 88px by default: ask for a sharp one. */
    fun sharpAvatar(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val isGoogleImage = url.contains("ggpht.com") || url.contains("googleusercontent.com")
        return if (isGoogleImage && avatarSize.containsMatchIn(url)) avatarSize.replace(url, "=s800") else url
    }

    private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    internal fun decodeBase64Url(text: String): String? {
        val out = ArrayList<Byte>(text.length)
        var buffer = 0
        var bits = 0
        for (c in text) {
            val value = BASE64_URL.indexOf(c)
            if (value < 0) return null
            buffer = ((buffer shl 6) or value) and 0xFFFFFF
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return runCatching { out.toByteArray().decodeToString() }.getOrNull()
    }
}

/**
 * Videos just marked Not interested or blocked here. The addon keeps the real list and leaves them
 * out of every row it builds; this only hides them at once from rows already loaded (a row just
 * shows a card fewer), so nothing has to be fetched or searched again.
 */
object YoutubeHiddenVideos {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun hide(videoId: String?) {
        if (videoId.isNullOrBlank()) return
        _ids.value = _ids.value + videoId
    }
}
