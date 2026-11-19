package app.xnkfit.data.content

import android.content.Context
import app.xnkfit.data.db.XnkDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The two calls the app makes at start-up, off the main thread: first the bundled content (must finish before
 * any screen reads foods or exercises), then the update check (may fail quietly). The call site is wired by B-05.
 */
object ContentBootstrap {
    suspend fun loadBundled(context: Context, db: XnkDatabase): BundledContentLoader.Result = withContext(Dispatchers.IO) {
        BundledContentLoader(ContentStore(db), AssetContentSource(context.applicationContext.assets)).loadIfNeeded()
    }

    suspend fun checkForUpdate(db: XnkDatabase, appVersionCode: Int, force: Boolean = false): ContentUpdater.Result {
        val verifier = if (ContentConfig.PUBLIC_KEY_IS_PLACEHOLDER) null else ManifestVerifier(ContentConfig.PUBLIC_KEY_BASE64)
        return ContentUpdater(ContentStore(db), HttpsContentFetcher(), ContentConfig.BASE_URL, verifier, appVersionCode).runIfDue(force)
    }
}
