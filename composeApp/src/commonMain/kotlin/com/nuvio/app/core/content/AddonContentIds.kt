package com.nuvio.app.core.content

// YouTube rows and movie-collection "shows" come from addons (KidSafe/WatchFilter/AnimeFilter
// and similar) as a fake "series" whose episodes are really just videos or films, with ids that
// contain "yt:" or "col:" after the addon's own prefix. Season and episode numbers still drive
// sorting and the auto-play queue, but they mean nothing to a viewer, so UI that shows a season
// or episode code (badges, Play/Resume/Next Up labels, season tabs) should check this first and
// fall back to plain wording instead. Mirrors NuvioTV's js/core/youtube/youtubeItem.js.
private val YOUTUBE_ID_REGEX = Regex("""(^|:)yt:""")
private val COLLECTION_ID_REGEX = Regex("""(^|:)col:""")

object AddonContentIds {
    fun isYoutubeId(value: String?): Boolean = value != null && YOUTUBE_ID_REGEX.containsMatchIn(value)

    fun isCollectionId(value: String?): Boolean = value != null && COLLECTION_ID_REGEX.containsMatchIn(value)

    /** True if any of the given ids (a meta id, a video id, whichever is on hand) is YouTube or Collection content. */
    fun hidesEpisodeCode(vararg ids: String?): Boolean =
        ids.any { isYoutubeId(it) || isCollectionId(it) }

    /** A plain word for what this is, instead of "Series" - "YouTube" or "Collection", else null. */
    fun contentKindLabel(id: String?): String? = when {
        isYoutubeId(id) -> "YouTube"
        isCollectionId(id) -> "Collection"
        else -> null
    }
}
