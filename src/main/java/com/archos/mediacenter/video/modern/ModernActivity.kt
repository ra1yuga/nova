// Nova Modern: entry point of the Compose UI. Everything below the UI (library, scrapers,
// network access, the player and its decoders) is Nova's own code, reused as-is.
package com.archos.mediacenter.video.modern

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.archos.filecorelibrary.FileUtils
import com.archos.filecorelibrary.MetaFile2
import com.archos.mediacenter.video.CustomApplication
import com.archos.mediacenter.video.browser.MainActivity
import com.archos.mediacenter.video.browser.PermissionChecker
import com.archos.mediacenter.video.browser.adapters.`object`.Base
import com.archos.mediacenter.video.browser.adapters.`object`.NonIndexedVideo
import com.archos.mediacenter.video.browser.adapters.`object`.Season
import com.archos.mediacenter.video.browser.adapters.`object`.Video
import com.archos.mediacenter.video.info.VideoInfoActivity
import com.archos.mediacenter.video.modern.data.LibraryRepository
import com.archos.mediacenter.video.modern.ui.NovaActions
import com.archos.mediacenter.video.modern.ui.NovaApp
import com.archos.mediacenter.video.modern.ui.NovaTheme
import com.archos.mediacenter.video.player.PlayerActivity
import com.archos.mediacenter.video.utils.DbUtils
import com.archos.mediacenter.video.utils.PlayUtils
import com.archos.mediacenter.video.utils.VideoPreferencesActivity
import com.archos.mediacenter.utils.trakt.Trakt
import com.archos.mediaprovider.video.LoaderUtils
import com.archos.mediaprovider.video.VideoStore
import com.archos.mediascraper.AutoScrapeService

class ModernActivity : AppCompatActivity(), NovaActions {

    private lateinit var permissionChecker: PermissionChecker
    private lateinit var repo: LibraryRepository
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as CustomApplication).loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // same start-up bookkeeping as Nova's phone and TV interfaces
        prefs.edit().putBoolean("user_paused_video", false).apply()
        LoaderUtils.mMustHideWatchedVideo = prefs.getBoolean("hide_watched", false)
        LoaderUtils.mSmartRecentlyRows = prefs.getBoolean("smart_recently_rows", false)

        permissionChecker = PermissionChecker(FileUtils.hasManageExternalStoragePermission(applicationContext))
        AutoScrapeService.registerObserver(this)

        repo = LibraryRepository(applicationContext)
        repo.start()

        setContent {
            NovaTheme {
                NovaApp(repo = repo, actions = this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as CustomApplication).loadLocale()
        permissionChecker.checkAndRequestPermission(this)
        // back from the player or another screen: pick up new resume points right away
        if (resumedOnce) repo.refreshNow()
        resumedOnce = true
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }

    @Deprecated("Forwarded to Nova's PermissionChecker, which still uses the callback API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
        repo.refreshNow()
    }

    // ------------------------------------------------------------------ NovaActions

    override fun play(video: Video, fromStart: Boolean) {
        PlayUtils.startVideo(
            this,
            video,
            if (fromStart) PlayerActivity.RESUME_NO else PlayerActivity.RESUME_FROM_LAST_POS,
            false,
            -1,
            null,
            -1L,
        )
    }

    override fun openImdb(imdbId: String?, title: String, year: Int) {
        val url = if (!imdbId.isNullOrBlank()) {
            "https://www.imdb.com/title/${imdbId.trim()}/"
        } else {
            val q = if (year > 0) "$title $year" else title
            "https://www.imdb.com/find/?q=" + Uri.encode(q) + "&s=tt"
        }
        openUrl(url)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No browser found to open IMDb", Toast.LENGTH_SHORT).show()
        }
    }

    override fun openInfo(video: Video) {
        try {
            VideoInfoActivity.startInstance(this, video, video.fileUri, video.id)
        } catch (e: Exception) {
            Log.w(TAG, "openInfo failed", e)
        }
    }

    override fun openSettings() {
        startActivity(Intent(this, VideoPreferencesActivity::class.java))
    }

    override fun openClassic() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    override fun openNetworkSetup() {
        Toast.makeText(this, "Open the side menu and pick Network to add or sign in to a share", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java))
    }

    override fun markWatched(video: Video, watched: Boolean) {
        try {
            if (watched) {
                DbUtils.markAsRead(this, video)
            } else {
                // same columns Nova resets when marking a season as not watched
                val values = ContentValues().apply {
                    put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_UNKNOWN)
                    put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_UNMARK)
                    put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, 0)
                }
                contentResolver.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, "_id=?", arrayOf(video.id.toString()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "markWatched failed", e)
        }
        repo.refreshNow()
    }

    override fun markSeasonWatched(season: Season, watched: Boolean) {
        try {
            if (watched) DbUtils.markAsRead(this, season) else DbUtils.markAsNotRead(this, season)
        } catch (e: Exception) {
            Log.w(TAG, "markSeasonWatched failed", e)
        }
        repo.refreshNow()
    }

    override fun setPinned(item: Base, pinned: Boolean) {
        try {
            if (pinned) DbUtils.markAsPinned(this, item) else DbUtils.markAsNotPinned(this, item)
        } catch (e: Exception) {
            Log.w(TAG, "setPinned failed", e)
        }
        repo.refreshNow()
    }

    override fun playFile(file: Any) {
        val f = file as? MetaFile2 ?: return
        val video = NonIndexedVideo(f.streamingUri, f.uri, f.name, null)
        PlayUtils.startVideo(this, video, PlayerActivity.RESUME_FROM_LAST_POS, false, -1, null, -1L)
    }

    override fun rememberSearch(query: String) {
        if (query.isBlank()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val list = recentSearches().filterNot { it.equals(query, ignoreCase = true) }
        val updated = (listOf(query) + list).take(8)
        prefs.edit().putString(PREF_RECENT_SEARCHES, updated.joinToString("\n")).apply()
    }

    override fun recentSearches(): List<String> {
        val raw = PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_RECENT_SEARCHES, null) ?: return emptyList()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "NovaModern"
        const val PREF_MODERN_UI = "nova_modern_ui_enabled"
        private const val PREF_RECENT_SEARCHES = "nova_modern_recent_searches"

        /** Phone/tablet launches go to the new interface unless it has been turned off. */
        @JvmStatic
        fun isEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_MODERN_UI, true)
    }
}
