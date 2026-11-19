package app.xnkfit.data.content

/** Constants of the content update (Content-Update.md §1, §3). */
object ContentConfig {
    /** Base URL of the content host (GitHub Pages of the content repo). Ends with a slash. Change here if the host moves. */
    const val BASE_URL = "https://xnkzzxd.github.io/xnk-fit-content/"

    /**
     * ⚠ GANTI DENGAN KUNCI PEMILIK. Placeholder: X.509 SubjectPublicKeyInfo, base64, ECDSA P-256.
     * Nobody holds the private half of this one (it was thrown away), so no update can pass until the owner
     * pastes the public key made from their own offline private key (see docs/Content-Update.md §7)
     * and sets [PUBLIC_KEY_IS_PLACEHOLDER] to false.
     */
    const val PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEiaZ8WSglLefcqT9N9z8nGedL/zyAkO4z/3w+01JwKFTEusxL5w3D21uomazPoGHXTygumtI3AOjSpqeRipdxxg=="

    /** While true the update check is off: the app runs on bundled content only. */
    const val PUBLIC_KEY_IS_PLACEHOLDER = true

    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000 // at most one completed check per 24 h
    const val RETRY_INTERVAL_MS = 6L * 60 * 60 * 1000 // at least 6 h between attempts
    const val TIMEOUT_MS = 8_000 // per request, connect and read; also a hard deadline per request
}
