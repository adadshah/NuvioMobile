package com.nuvio.app.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProfileSettingsCredentialPolicyTest {
    @Test
    fun `credential fields are removed from profile settings payloads`() {
        val payload = buildJsonObject {
            put("debrid_enabled", JsonPrimitive(true))
            put("debrid_torbox_api_key", JsonPrimitive("secret"))
            put("debrid_premiumize_api_key", JsonPrimitive("secret"))
        }

        val sanitized = withoutProfileCredentials(PROFILE_DEBRID_SETTINGS_FEATURE, payload)

        assertFalse("debrid_torbox_api_key" in sanitized)
        assertFalse("debrid_premiumize_api_key" in sanitized)
        assertEquals(JsonPrimitive(true), sanitized["debrid_enabled"])
    }

    @Test
    fun `mdblist api key syncs from remote like any other setting`() {
        // TMDB/MDBList keys are meant to reach every device on the profile now
        // (the TV side already worked this way) - only debrid and the player
        // feature's own third-party credentials stay device-local.
        val remote = buildJsonObject {
            put("mdblist_enabled", JsonPrimitive(true))
            put("mdblist_api_key", JsonPrimitive("remote"))
        }
        val local = buildJsonObject {
            put("mdblist_api_key", JsonPrimitive("local"))
        }

        val merged = preservingLocalProfileCredentials(PROFILE_MDBLIST_SETTINGS_FEATURE, remote, local)

        assertEquals(JsonPrimitive("remote"), merged["mdblist_api_key"])
        assertEquals(JsonPrimitive(true), merged["mdblist_enabled"])
    }

    @Test
    fun `tmdb api key syncs from remote like any other setting`() {
        val remote = buildJsonObject {
            put("tmdb_enabled", JsonPrimitive(true))
            put("tmdb_api_key", JsonPrimitive("remote"))
        }
        val local = buildJsonObject {
            put("tmdb_api_key", JsonPrimitive("local"))
        }

        val merged = preservingLocalProfileCredentials(PROFILE_TMDB_SETTINGS_FEATURE, remote, local)
        val sanitized = withoutProfileCredentials(PROFILE_TMDB_SETTINGS_FEATURE, local)

        assertEquals(JsonPrimitive("remote"), merged["tmdb_api_key"])
        assertEquals(JsonPrimitive("local"), sanitized["tmdb_api_key"])
        assertEquals(JsonPrimitive(true), merged["tmdb_enabled"])
    }
}
