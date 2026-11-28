package com.nuvio.app.features.trakt

import com.nuvio.app.features.simkl.SimklPkceCrypto
import com.nuvio.app.features.simkl.base64UrlWithoutPadding

/**
 * PKCE (RFC 7636) material for Trakt's browser Authorization Code flow.
 *
 * Trakt moved all new app registrations to PKCE-only: new apps no longer
 * receive a `client_secret`, so the authorization-code flow (used here on
 * mobile) must send a `code_challenge` when starting the flow and the
 * matching `code_verifier` when exchanging the code for a token, instead of
 * a secret. Reuses the same crypto primitives as the existing Simkl PKCE
 * implementation rather than duplicating platform-specific crypto.
 */
internal data class TraktPkceMaterial(
    val verifier: String,
    val challenge: String,
    val state: String,
)

internal fun generateTraktPkceMaterial(): TraktPkceMaterial =
    createTraktPkceMaterial(
        verifierEntropy = SimklPkceCrypto.secureRandomBytes(32),
        stateEntropy = SimklPkceCrypto.secureRandomBytes(32),
    )

internal fun createTraktPkceMaterial(
    verifierEntropy: ByteArray,
    stateEntropy: ByteArray,
): TraktPkceMaterial {
    require(verifierEntropy.size >= 32) { "PKCE verifier entropy must be at least 32 bytes" }
    require(stateEntropy.size >= 16) { "OAuth state entropy must be at least 16 bytes" }
    val verifier = verifierEntropy.base64UrlWithoutPadding()
    require(verifier.length in 43..128) { "PKCE verifier must be 43-128 characters" }
    return TraktPkceMaterial(
        verifier = verifier,
        challenge = traktChallengeFromVerifier(verifier),
        state = stateEntropy.base64UrlWithoutPadding(),
    )
}

internal fun traktChallengeFromVerifier(verifier: String): String =
    SimklPkceCrypto.sha256(verifier.encodeToByteArray()).base64UrlWithoutPadding()
