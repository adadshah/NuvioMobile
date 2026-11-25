package com.nuvio.app.features.player

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.youtube.YoutubeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One video in the player's More videos panel (Up next / More like this). */
data class YoutubeMoreVideo(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val background: String?,
    val subtitle: String,
)

private data class YoutubeMoreList(
    val items: List<YoutubeMoreVideo> = emptyList(),
    val phrase: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * The addon's smart lists, the same ones NuvioTV's player info sheet uses:
 * - Up next: /ytupnext/<id>.json?fresh=1 — ranked by this profile's taste, with new finds.
 * - More like this: /ytrelated/<id>.json?t=<seconds> — from the addon's study of this video
 *   (or the chapter playing now), with the AI search phrase it used.
 */
internal object YoutubeMoreVideosRepository {
    private val log = Logger.withTag("YoutubeMoreVideos")
    private val json = Json { ignoreUnknownKeys = true }

    /** The account link of the addon the video came from, else any installed addon with one. */
    fun accountBase(providerAddonId: String?): String? {
        val addons = AddonRepository.uiState.value.addons.filter { it.isActive }
        val preferred = addons.firstOrNull { providerAddonId != null && it.manifest?.id == providerAddonId }
        return (listOfNotNull(preferred) + addons).firstNotNullOfOrNull {
            YoutubeContent.accountBaseFromManifest(it.manifestUrl)
        }
    }

    suspend fun upNext(base: String, videoId: String): Pair<List<YoutubeMoreVideo>, String?>? =
        fetch("${base.trimEnd('/')}/ytupnext/$videoId.json?ids=&fresh=1")?.let { root ->
            val items = root["items"] as? JsonObject ?: JsonObject(emptyMap())
            val order = (root["order"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val ordered = order.mapNotNull { items[it] as? JsonObject } +
                items.filterKeys { it !in order }.values.mapNotNull { it as? JsonObject }
            ordered.mapNotNull { it.toVideo() }.distinctBy { it.id } to null
        }

    suspend fun related(base: String, videoId: String, seconds: Long): Pair<List<YoutubeMoreVideo>, String?>? {
        val query = if (seconds > 0) "?t=$seconds" else ""
        return fetch("${base.trimEnd('/')}/ytrelated/$videoId.json$query")?.let { root ->
            val items = (root["items"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toVideo() }.orEmpty()
            val phrase = root.str("query") ?: root.str("label")
            items to phrase
        }
    }

    private suspend fun fetch(url: String): JsonObject? = withContext(Dispatchers.Default) {
        runCatching { json.parseToJsonElement(httpGetText(url)) as? JsonObject }
            .onFailure { log.w(it) { "More videos request failed" } }
            .getOrNull()
            ?.takeIf { it["err"] == null }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.toVideo(): YoutubeMoreVideo? {
        val id = str("id") ?: return null
        if (YoutubeContent.videoIdOf(id) == null) return null
        val why = str("why")
        return YoutubeMoreVideo(
            id = id,
            title = str("name") ?: str("title") ?: return null,
            thumbnail = str("landscapePoster") ?: str("poster"),
            background = str("background"),
            subtitle = listOfNotNull(why, str("releaseInfo")).joinToString(" · "),
        )
    }
}

private enum class MoreVideosTab { UpNext, MoreLikeThis }

/**
 * The player's More videos panel for YouTube (in place of Episodes): two tabs, Up next and
 * More like this, and tapping a video plays it straight away (no sources list).
 */
@Composable
internal fun PlayerYoutubeMoreVideosPanel(
    visible: Boolean,
    currentVideoId: String?,
    providerAddonId: String?,
    positionMs: Long,
    onPlay: (YoutubeMoreVideo) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var tab by remember { mutableStateOf(MoreVideosTab.UpNext) }
    var upNext by remember(currentVideoId) { mutableStateOf(YoutubeMoreList()) }
    var related by remember(currentVideoId) { mutableStateOf(YoutubeMoreList()) }
    val ytId = remember(currentVideoId) { YoutubeContent.videoIdOf(currentVideoId) }

    LaunchedEffect(visible, tab, ytId) {
        if (!visible || ytId == null) return@LaunchedEffect
        val base = YoutubeMoreVideosRepository.accountBase(providerAddonId)
        val current = if (tab == MoreVideosTab.UpNext) upNext else related
        if (current.loading || current.items.isNotEmpty()) return@LaunchedEffect
        val set: (YoutubeMoreList) -> Unit = { if (tab == MoreVideosTab.UpNext) upNext = it else related = it }
        if (base == null) {
            set(YoutubeMoreList(failed = true))
            return@LaunchedEffect
        }
        set(YoutubeMoreList(loading = true))
        val result = if (tab == MoreVideosTab.UpNext) {
            YoutubeMoreVideosRepository.upNext(base, ytId)
        } else {
            YoutubeMoreVideosRepository.related(base, ytId, positionMs / 1000)
        }
        val items = result?.first.orEmpty().filter { YoutubeContent.videoIdOf(it.id) != ytId }
        set(YoutubeMoreList(items = items, phrase = result?.second, failed = items.isEmpty()))
    }

    PlayerSidePanel(visible = visible, onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            PlayerPanelHeader(title = "More videos") {
                PlayerDialogButton(label = "Close", onClick = onDismiss)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoreVideosTabChip("Up next", tab == MoreVideosTab.UpNext) { tab = MoreVideosTab.UpNext }
                MoreVideosTabChip("More like this", tab == MoreVideosTab.MoreLikeThis) { tab = MoreVideosTab.MoreLikeThis }
            }
            val list = if (tab == MoreVideosTab.UpNext) upNext else related
            if (tab == MoreVideosTab.MoreLikeThis && !list.phrase.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                // The smart phrase, rolling by like a news ticker when it doesn't fit.
                Text(
                    text = "🔎  ${list.phrase}",
                    color = tokens.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                            repeatDelayMillis = 1500,
                        ),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    list.loading -> CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    list.items.isEmpty() -> Text(
                        text = if (list.failed) "Nothing to show here right now." else "",
                        color = tokens.colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(list.items, key = { it.id }) { video ->
                            MoreVideoRow(video = video, onClick = { onPlay(video) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreVideosTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color(0xFFF5F5F5) else tokens.colors.surfaceCard)
            .border(1.dp, if (selected) Color.Transparent else tokens.colors.borderDefault, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else tokens.colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun MoreVideoRow(video: YoutubeMoreVideo, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.colors.surfaceCard)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = video.subtitle,
                    color = tokens.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
