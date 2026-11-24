package com.nuvio.app.features.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.DisintegrationRequest
import com.nuvio.app.core.ui.LocalVerticalBarPadding
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioHorizontalScrollBleed
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.canOpenCatalog
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeContinueWatchingSection
import com.nuvio.app.features.home.components.HomeContinueWatchingSectionBottomPadding
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.home.components.rememberContinueWatchingLayout
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.shouldTreatAsInProgressForContinueWatching
import com.nuvio.app.features.watchprogress.toContinueWatchingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

private const val YOUTUBE_ROW_PREVIEW_LIMIT = 18
private const val YOUTUBE_CONTINUE_WATCHING_LIMIT = 20

/** A YouTube card held down: the menu needs the card and its addon's account link. */
internal data class YoutubeMenuTarget(
    val meta: MetaPreview,
    val accountBase: String?,
)

/**
 * The YouTube tab, like NuvioTV's: channels as circles at the top, then YouTube's own Continue
 * Watching, then every YouTube row (Home no longer shows any of these). Tapping a video plays it
 * straight away; holding one opens Play / Forget I searched this / Not interested / Block video.
 */
@Composable
fun YoutubeScreen(
    modifier: Modifier = Modifier,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onCatalogClick: ((HomeCatalogSection) -> Unit)? = null,
    onChannelClick: ((MetaPreview) -> Unit)? = null,
    onVideoClick: ((MetaPreview) -> Unit)? = null,
    onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)? = null,
    onContinueWatchingLongPress: ((ContinueWatchingItem) -> Unit)? = null,
    continueWatchingDisintegrationRequest: DisintegrationRequest<String>? = null,
) {
    LaunchedEffect(Unit) {
        ContinueWatchingPreferencesRepository.ensureLoaded()
        WatchedRepository.ensureLoaded()
        WatchProgressRepository.ensureLoaded()
    }

    val homeUiState by HomeRepository.uiState.collectAsStateWithLifecycle()
    val homeSettingsUiState by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val continueWatchingPreferences by ContinueWatchingPreferencesRepository.uiState.collectAsStateWithLifecycle()
    val watchProgressUiState by WatchProgressRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val hiddenVideoIds by YoutubeHiddenVideos.ids.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val screenScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    var menuTarget by remember { mutableStateOf<YoutubeMenuTarget?>(null) }

    ScreenActivityEffect(scrollToTopRequests) { active ->
        if (!active) return@ScreenActivityEffect
        scrollToTopRequests.collect {
            listState.animateScrollToItem(0)
        }
    }

    // Every YouTube row, in the same order (and with the same on/off choices) as Home's settings.
    val youtubeSections = remember(homeUiState.sections, homeSettingsUiState.items, hiddenVideoIds) {
        val byKey = homeUiState.sections.associateBy { it.key }
        val ordered = homeSettingsUiState.items
            .filter { it.enabled && !it.isCollection }
            .mapNotNull { byKey[it.key] }
            .ifEmpty { homeUiState.sections }
        ordered
            .distinctBy { it.key }
            .filter { YoutubeContent.isYoutubeSection(it) }
            .map { section ->
                if (hiddenVideoIds.isEmpty()) {
                    section
                } else {
                    section.copy(items = section.items.filterNot { YoutubeContent.videoIdOf(it.id) in hiddenVideoIds })
                }
            }
            .filter { it.items.isNotEmpty() }
    }
    val channelSections = remember(youtubeSections) { youtubeSections.filter { YoutubeContent.isChannelSection(it) } }
    val videoSections = remember(youtubeSections) { youtubeSections.filterNot { YoutubeContent.isChannelSection(it) } }

    // YouTube's own Continue Watching: videos started and not finished, newest first.
    val continueWatchingItems = remember(watchProgressUiState.entries, watchProgressUiState.hiddenContentIds, hiddenVideoIds) {
        watchProgressUiState.entries
            .asSequence()
            .filter { YoutubeContent.isYoutube(it.parentMetaId) || YoutubeContent.isYoutube(it.videoId) }
            .filter { it.parentMetaId !in watchProgressUiState.hiddenContentIds }
            .filter { it.shouldTreatAsInProgressForContinueWatching() }
            .sortedByDescending { it.lastUpdatedEpochMs }
            .distinctBy { it.videoId }
            .take(YOUTUBE_CONTINUE_WATCHING_LIMIT)
            .map { it.toContinueWatchingItem() }
            .toList()
    }

    val openCard: (MetaPreview) -> Unit = { meta ->
        if (YoutubeContent.isChannel(meta.id)) {
            onChannelClick?.invoke(meta)
        } else {
            onVideoClick?.invoke(meta)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value)
        val posterCardStyle = rememberPosterCardStyleUiState()
        val continueWatchingLayout = rememberContinueWatchingLayout(maxWidth.value, posterCardStyle)
        val tokens = MaterialTheme.nuvio
        val nothingYet = youtubeSections.isEmpty() && continueWatchingItems.isEmpty()

        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            horizontalPadding = 0.dp,
            listState = listState,
        ) {
            item(key = "youtube_title", contentType = "title") {
                Text(
                    text = "YouTube",
                    modifier = Modifier.padding(horizontal = sectionPadding).padding(bottom = 4.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary,
                )
            }

            channelSections.forEach { section ->
                item(key = "youtube_channels_${section.key}", contentType = "channels") {
                    YoutubeChannelRow(
                        section = section,
                        sectionPadding = sectionPadding,
                        onChannelClick = { meta -> onChannelClick?.invoke(meta) },
                        onViewAllClick = if (section.canOpenCatalog(YOUTUBE_ROW_PREVIEW_LIMIT)) {
                            onCatalogClick?.let { { it(section) } }
                        } else {
                            null
                        },
                    )
                }
            }

            if (continueWatchingPreferences.isVisible && continueWatchingItems.isNotEmpty()) {
                item(key = "youtube_continue_watching", contentType = "continue_watching") {
                    HomeContinueWatchingSection(
                        items = continueWatchingItems,
                        style = continueWatchingPreferences.style,
                        dataSourceKey = watchProgressUiState.source,
                        useEpisodeThumbnails = continueWatchingPreferences.useEpisodeThumbnails,
                        blurNextUp = continueWatchingPreferences.blurNextUp,
                        modifier = Modifier.padding(bottom = HomeContinueWatchingSectionBottomPadding),
                        sectionPadding = sectionPadding,
                        layout = continueWatchingLayout,
                        onItemClick = onContinueWatchingClick,
                        onItemLongPress = onContinueWatchingLongPress,
                        disintegrationRequest = continueWatchingDisintegrationRequest,
                    )
                }
            }

            videoSections.forEach { section ->
                item(key = "youtube_row_${section.key}", contentType = "catalog") {
                    HomeCatalogRowSection(
                        section = section,
                        entries = section.items.take(YOUTUBE_ROW_PREVIEW_LIMIT),
                        modifier = Modifier.padding(bottom = 12.dp),
                        sectionPadding = sectionPadding,
                        onViewAllClick = if (section.canOpenCatalog(YOUTUBE_ROW_PREVIEW_LIMIT)) {
                            onCatalogClick?.let { { it(section) } }
                        } else {
                            null
                        },
                        watchedKeys = watchedUiState.watchedKeys,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        onPosterClick = openCard,
                        onPosterLongClick = { meta ->
                            if (YoutubeContent.isChannel(meta.id)) {
                                onChannelClick?.invoke(meta)
                            } else {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuTarget = YoutubeMenuTarget(meta, YoutubeContent.accountBase(section.target))
                            }
                        },
                    )
                }
            }

            if (nothingYet && homeUiState.isLoading) {
                items(count = 2, key = { "youtube_skeleton_$it" }, contentType = { "skeleton" }) {
                    HomeSkeletonRow(horizontalPadding = sectionPadding)
                }
            } else if (nothingYet) {
                item(key = "youtube_empty", contentType = "empty") {
                    HomeEmptyStateCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = "No YouTube yet",
                        message = "Turn on YouTube rows in your addon's setup page (KidSafe, Watch Filter or Anime Filter), then they show up here.",
                    )
                }
            }
        }
    }

    menuTarget?.let { target ->
        YoutubeVideoActionSheet(
            target = target,
            actionScope = screenScope,
            onDismiss = { menuTarget = null },
            onPlay = { openCard(target.meta) },
        )
    }
}

@Composable
private fun YoutubeChannelRow(
    section: HomeCatalogSection,
    sectionPadding: androidx.compose.ui.unit.Dp,
    onChannelClick: (MetaPreview) -> Unit,
    onViewAllClick: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = section.title,
            modifier = Modifier
                .padding(horizontal = sectionPadding)
                .then(if (onViewAllClick != null) Modifier.clickable(onClick = onViewAllClick) else Modifier),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = tokens.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().nuvioHorizontalScrollBleed(LocalVerticalBarPadding.current),
            contentPadding = PaddingValues(horizontal = sectionPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(section.items.distinctBy { it.id }.take(40), key = { it.id }) { channel ->
                Column(
                    modifier = Modifier
                        .width(82.dp)
                        .clickable(onClick = { onChannelClick(channel) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = YoutubeContent.sharpAvatar(channel.poster ?: channel.rawPosterUrl),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(tokens.colors.textDisabled.copy(alpha = 0.2f)),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YoutubeVideoActionSheet(
    target: YoutubeMenuTarget,
    actionScope: CoroutineScope,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    val base = target.accountBase
    val videoId = YoutubeContent.videoIdOf(target.meta.id)
    val searchQuery = YoutubeContent.searchQueryOf(target.meta.id)

    // The sheet closes at once; the work runs on the screen's scope, which outlives the sheet.
    fun choose(work: suspend () -> Unit) {
        actionScope.launch { work() }
        sheetScope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
    }

    NuvioModalBottomSheet(
        onDismissRequest = {
            sheetScope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = nuvioSafeBottomPadding(tokens.spacing.screenHorizontal)),
        ) {
            Text(
                text = target.meta.name,
                modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            NuvioBottomSheetDivider()
            NuvioBottomSheetActionRow(
                icon = Icons.Default.PlayArrow,
                title = "Play",
                onClick = { choose { onPlay() } },
            )
            if (base != null && searchQuery != null) {
                NuvioBottomSheetActionRow(
                    icon = Icons.Default.SearchOff,
                    title = "Forget I searched this",
                    onClick = {
                        choose {
                            when (val result = YoutubeVideoActions.forgetSearch(base, searchQuery)) {
                                YoutubeVideoActions.Result.Ok -> {
                                    NuvioToastController.show("Forgot “$searchQuery”.")
                                    // Because You Searched now follows the search before it.
                                    HomeRepository.refresh(AddonRepository.uiState.value.addons.enabledAddons(), force = true)
                                }
                                is YoutubeVideoActions.Result.Failed -> NuvioToastController.show(result.message)
                            }
                        }
                    },
                )
            }
            if (base != null && videoId != null) {
                NuvioBottomSheetActionRow(
                    icon = Icons.Default.ThumbDown,
                    title = "Not interested",
                    onClick = {
                        choose {
                            when (val result = YoutubeVideoActions.notInterested(base, videoId, target.meta.name)) {
                                YoutubeVideoActions.Result.Ok -> {
                                    YoutubeHiddenVideos.hide(videoId)
                                    NuvioToastController.show("Got it. You'll see less like this.")
                                }
                                is YoutubeVideoActions.Result.Failed -> NuvioToastController.show(result.message)
                            }
                        }
                    },
                )
                NuvioBottomSheetActionRow(
                    icon = Icons.Default.Block,
                    title = "Block video",
                    onClick = {
                        choose {
                            when (val result = YoutubeVideoActions.blockVideo(base, videoId, target.meta.name)) {
                                YoutubeVideoActions.Result.Ok -> {
                                    YoutubeHiddenVideos.hide(videoId)
                                    NuvioToastController.show("Video blocked. Undo it on the setup site.")
                                }
                                is YoutubeVideoActions.Result.Failed -> NuvioToastController.show(result.message)
                            }
                        }
                    },
                )
            }
        }
    }
}
