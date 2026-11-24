package com.nuvio.app.features.streams

import com.nuvio.app.core.content.AddonContentIds
import com.nuvio.app.features.player.PlayerSettingsUiState

/**
 * YouTube videos (from the KidSafe / Watch Filter / Anime Filter addons) always play straight
 * away, like NuvioTV: the first source the addon gives, with no sources list in between and no
 * stop there on the way back. The profile's own auto-play choice (manual, regex) still applies to
 * everything else.
 */
internal fun PlayerSettingsUiState.forYoutubeDirectPlay(videoId: String?, parentMetaId: String?): PlayerSettingsUiState =
    if (AddonContentIds.isYoutubeId(videoId) || AddonContentIds.isYoutubeId(parentMetaId)) {
        copy(
            streamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,
            streamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,
            streamAutoPlayPreferBingeGroup = false,
        )
    } else {
        this
    }
