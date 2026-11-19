package app.xnkfit.data.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.xnkfit.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Drive is set up only when the build carries an OAuth client id (`-Pxnkfit.driveClientId=…`, empty by default;
 * Google-OAuth.md: the Android client is the owner's and is matched by package name and SHA-1). Without it every
 * Drive feature shows "belum tersedia" and nothing calls Google.
 */
object DriveConfig {
    val configured: Boolean get() = BuildConfig.DRIVE_OAUTH_CLIENT_ID.isNotBlank()
}

/** Outcome of asking Google for the `drive.appdata` token. */
sealed interface DriveAuthResult {
    data class Token(val value: String) : DriveAuthResult
    /** The user must agree once on Google's own screen; the Data screen launches [intent]. */
    data class NeedsConsent(val intent: PendingIntent) : DriveAuthResult
}

/**
 * Google's AuthorizationClient (play-services-auth, Design §12) asks for `drive.appdata` and nothing else
 * (Google-OAuth.md §1). Tokens stay in the system account store (Design §14); the app keeps no copy.
 */
class PlayDriveAuth(private val context: Context) {
    private val request: AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveClient.SCOPE))).build()

    /** Blocking; call off the main thread. */
    fun authorize(): DriveAuthResult {
        val r = Tasks.await(Identity.getAuthorizationClient(context).authorize(request), TIMEOUT_S, TimeUnit.SECONDS)
        return toResult(r)
    }

    /** For the background run: a token, or [DriveAuthException] when the user must sign in on the Data screen. */
    fun tokenOrThrow(): String = when (val r = authorize()) {
        is DriveAuthResult.Token -> r.value
        is DriveAuthResult.NeedsConsent -> throw DriveAuthException()
    }

    /** After Google's consent screen returned [data]. */
    fun fromConsent(data: Intent?): DriveAuthResult =
        toResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))

    private fun toResult(r: AuthorizationResult): DriveAuthResult {
        val pending = r.pendingIntent
        val token = r.accessToken
        return when {
            r.hasResolution() && pending != null -> DriveAuthResult.NeedsConsent(pending)
            token != null -> DriveAuthResult.Token(token)
            else -> throw DriveAuthException()
        }
    }

    private companion object {
        const val TIMEOUT_S = 30L
    }
}
