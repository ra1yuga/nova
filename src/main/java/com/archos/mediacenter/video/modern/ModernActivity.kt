// Nova Modern: entry point of the Compose UI. Everything below the UI (library, scrapers,
// network access, the player and its decoders) is Nova's own code, reused as-is.
package com.archos.mediacenter.video.modern

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    private var showingCrash = false

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashCatcher.install(this)
        (application as CustomApplication).loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        permissionChecker = PermissionChecker(FileUtils.hasManageExternalStoragePermission(applicationContext))
        repo = LibraryRepository(applicationContext)
        repo.onProblem = { where, e -> CrashCatcher.recordProblem(applicationContext, where, e) }

        val crash = CrashCatcher.pendingCrash(this)
        if (crash != null) {
            showCrashReport(crash)
            return
        }
        startApp()
    }

    private fun startApp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // same start-up bookkeeping as Nova's phone and TV interfaces
        prefs.edit().putBoolean("user_paused_video", false).apply()
        LoaderUtils.mMustHideWatchedVideo = prefs.getBoolean("hide_watched", false)
        LoaderUtils.mSmartRecentlyRows = prefs.getBoolean("smart_recently_rows", false)

        AutoScrapeService.registerObserver(this)
        repo.start()

        setContent {
            NovaTheme {
                NovaApp(repo = repo, actions = this)
            }
        }
    }

    /** Plain Android views on purpose: this must work even if the Compose UI is what crashed. */
    private fun showCrashReport(report: String) {
        showingCrash = true
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            setPadding(px(16), px(16), px(16), px(16))
        }
        root.addView(TextView(this).apply {
            text = "Nova Modern closed unexpectedly last time"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Copy or share this report and send it to Claude so the problem can be fixed."
            setTextColor(Color.rgb(0xA3, 0xA0, 0x9A))
            textSize = 14f
            setPadding(0, px(6), 0, px(12))
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            buttons.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8) })
        }
        button("Copy") { copyText("Nova Modern crash report", report) }
        button("Share") { shareText(report) }
        button("Continue") {
            CrashCatcher.clearCrash(this)
            recreate()
        }
        root.addView(buttons)
        val scroll = ScrollView(this).apply { setPadding(0, px(12), 0, 0) }
        scroll.addView(TextView(this).apply {
            text = report
            setTextColor(Color.rgb(0xF4, 0xF1, 0xEA))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun copyText(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            startActivity(Intent.createChooser(send, "Share report"))
        } catch (e: ActivityNotFoundException) {
            copyText("Nova Modern report", text)
        }
    }

    override fun onResume() {
        super.onResume()
        if (showingCrash) return
        (application as CustomApplication).loadLocale()
        permissionChecker.checkAndRequestPermission(this)
        // back from the player or another screen: pick up new resume points right away
        if (resumedOnce) repo.refreshNow()
        resumedOnce = true
    }

    override fun onDestroy() {
        if (::repo.isInitialized) repo.stop()
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

    override fun hasProblemReport(): Boolean = CrashCatcher.problems(this) != null

    override fun shareProblemReport() {
        val text = CrashCatcher.problems(this) ?: return
        shareText(text)
        CrashCatcher.clearProblems(this)
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
