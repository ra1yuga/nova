// Nova Modern: reads Nova's existing media library (the same database, scrapers and loaders the
// classic phone and TV interfaces use) and turns it into simple models for the Compose UI.
package com.archos.mediacenter.video.modern.data

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.loader.content.CursorLoader
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper
import com.archos.mediacenter.video.browser.adapters.`object`.Episode
import com.archos.mediacenter.video.browser.adapters.`object`.Movie
import com.archos.mediacenter.video.browser.adapters.`object`.Season
import com.archos.mediacenter.video.browser.adapters.`object`.Tvshow
import com.archos.mediacenter.video.browser.adapters.`object`.Video
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader
import com.archos.mediacenter.video.browser.loader.EpisodesLoader
import com.archos.mediacenter.video.browser.loader.LastAddedLoader
import com.archos.mediacenter.video.browser.loader.MoviesLoader
import com.archos.mediacenter.video.browser.loader.SearchVideoLoader
import com.archos.mediacenter.video.browser.loader.TvshowLoader
import com.archos.mediacenter.video.browser.loader.VideoLoader
import com.archos.mediacenter.video.browser.loader.WatchingUpNextLoader
import com.archos.mediacenter.video.info.SingleVideoLoader
import com.archos.mediaprovider.video.VideoStore
import com.archos.mediascraper.BaseTags
import com.archos.mediascraper.ScraperImage
import com.archos.mediascraper.VideoTags
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Nova's loaders create Handlers (update throttle, content observer) in their constructors, so they
 * must be built on a thread that has a Looper. All library work runs on this one background thread.
 */
private object LibraryThread {
    private val thread = HandlerThread("NovaModernLibrary").apply { start() }
    val dispatcher = Handler(thread.looper).asCoroutineDispatcher("NovaModernLibrary")
}

class LibraryRepository(private val context: Context) {

    /** Called (on the library thread) when a load fails, so the problem can be reported. */
    var onProblem: (String, Throwable) -> Unit = { _, _ -> }

    private suspend fun <T> onLibrary(where: String, fallback: () -> T, block: () -> T): T =
        withContext(LibraryThread.dispatcher) {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "$where failed", e)
                try { onProblem(where, e) } catch (_: Exception) { }
                fallback()
            }
        }

    private val _version = MutableStateFlow(0)

    /** Bumps whenever Nova's library changes (new files, scraping, watched state, resume points). */
    val version: StateFlow<Int> = _version

    private val handler = Handler(Looper.getMainLooper())
    private val bump = Runnable { _version.value = _version.value + 1 }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // coalesce bursts (scanning/scraping writes many rows)
            handler.removeCallbacks(bump)
            handler.postDelayed(bump, 1500)
        }
    }

    fun start() {
        try {
            context.contentResolver.registerContentObserver(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer
            )
        } catch (e: Exception) {
            Log.w(TAG, "start: cannot observe library", e)
        }
    }

    fun stop() {
        handler.removeCallbacks(bump)
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
    }

    fun refreshNow() {
        _version.value = _version.value + 1
    }

    // ---------------------------------------------------------------- queries

    private fun query(loader: VideoLoader, detailed: Boolean): Cursor? {
        return try {
            val cl = loader.getV4CursorLoader(detailed, false) as CursorLoader
            context.contentResolver.query(cl.uri, cl.projection, cl.selection, cl.selectionArgs, cl.sortOrder)
        } catch (e: Exception) {
            Log.w(TAG, "query failed for ${loader.javaClass.simpleName}", e)
            null
        }
    }

    private class Extra(val backdropFile: String?, val backdropUrl: String?, val showId: Long, val pinned: Boolean)

    private fun readVideos(loader: VideoLoader, detailed: Boolean = true, limit: Int = Int.MAX_VALUE): List<Pair<Video, Extra>> {
        val out = ArrayList<Pair<Video, Extra>>()
        val c = query(loader, detailed) ?: return out
        c.use { cursor ->
            if (!cursor.moveToFirst()) return out
            val mapper = VideoCursorMapper()
            mapper.bindColumns(cursor)
            val bdFile = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_BACKDROP_LARGE_FILE)
            val bdUrl = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_BACKDROP_LARGE_URL)
            val showIdCol = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID)
            val pinnedCol = cursor.getColumnIndex(VideoStore.Video.VideoColumns.NOVA_PINNED)
            do {
                try {
                    val v = mapper.bind(cursor) as? Video ?: continue
                    val extra = Extra(
                        backdropFile = if (bdFile >= 0) cursor.getString(bdFile) else null,
                        backdropUrl = if (bdUrl >= 0) cursor.getString(bdUrl) else null,
                        showId = if (showIdCol >= 0 && !cursor.isNull(showIdCol)) cursor.getLong(showIdCol) else -1L,
                        pinned = pinnedCol >= 0 && !cursor.isNull(pinnedCol) && cursor.getLong(pinnedCol) > 0,
                    )
                    out.add(v to extra)
                } catch (e: Exception) {
                    Log.w(TAG, "readVideos: skipping row", e)
                }
            } while (out.size < limit && cursor.moveToNext())
        }
        return out
    }

    private fun readShows(loader: VideoLoader): List<Tvshow> {
        val out = ArrayList<Tvshow>()
        val c = query(loader, false) ?: return out
        c.use { cursor ->
            if (!cursor.moveToFirst()) return out
            val mapper = TvshowCursorMapper()
            mapper.bindColumns(cursor)
            do {
                try {
                    (mapper.bind(cursor) as? Tvshow)?.let { out.add(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "readShows: skipping row", e)
                }
            } while (cursor.moveToNext())
        }
        return out
    }

    private fun loadShow(showId: Long): Tvshow? = readShows(TvshowLoader(context, showId)).firstOrNull()

    // ---------------------------------------------------------------- mapping

    private fun art(file: String?, url: String?): Any? {
        if (!file.isNullOrBlank()) {
            val f = File(file)
            if (f.exists()) return f
        }
        return url?.takeIf { it.isNotBlank() }
    }

    private fun image(img: ScraperImage?): Any? = img?.let { art(it.largeFile, it.largeUrl) }

    private fun posterOf(uri: Uri?): Any? = uri

    private fun tagsOf(base: com.archos.mediacenter.video.browser.adapters.`object`.Base): BaseTags? = try {
        base.getFullScraperTags(context)
    } catch (e: Exception) {
        Log.w(TAG, "tagsOf: no scraper tags", e)
        null
    }

    fun badgesOf(v: Video?): List<String> {
        if (v == null) return emptyList()
        val out = ArrayList<String>(3)
        when (v.normalizedDefinition) {
            VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_4K -> out.add("4K")
            VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_1080P -> out.add("HD")
            VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_720P -> out.add("HD")
        }
        val vf = (v.calculatedVideoFormat?.takeIf { it.isNotBlank() } ?: v.guessedVideoFormat ?: "").lowercase(Locale.ROOT)
        when {
            vf.contains("dolby vision") || vf.contains("dovi") || Regex("\\bdv\\b").containsMatchIn(vf) -> out.add("DOLBY VISION")
            vf.contains("hdr10+") -> out.add("HDR10+")
            vf.contains("hdr") -> out.add("HDR")
            vf.contains("hlg") -> out.add("HLG")
        }
        val af = (v.calculatedBestAudioFormat?.takeIf { it.isNotBlank() } ?: v.guessedAudioFormat ?: "").lowercase(Locale.ROOT)
        when {
            af.contains("atmos") -> out.add("ATMOS")
            af.contains("truehd") -> out.add("TRUEHD")
            af.contains("dts") -> out.add("DTS")
            af.contains("7.1") -> out.add("7.1")
            af.contains("5.1") || af.contains("e-ac3") || af.contains("eac3") || af.contains("ac3") -> out.add("5.1")
        }
        return out
    }

    private fun itemOf(v: Video, extra: Extra): MediaItem {
        val wideArt = art(extra.backdropFile, extra.backdropUrl)
        return when (v) {
            is Episode -> {
                val still = v.pictureUri ?: wideArt
                val remaining = v.remainingLabel()
                MediaItem(
                    key = "e:${v.id}",
                    kind = Kind.EPISODE,
                    title = v.showName ?: v.name ?: "",
                    subtitle = buildString {
                        append(v.code())
                        if (remaining != null) append(" · ").append(remaining)
                        else if (!v.episodeName.isNullOrBlank()) append(" · ").append(v.episodeName)
                    },
                    poster = posterOf(v.posterUri),
                    wide = still,
                    progress = v.progressFraction(),
                    watched = v.isWatched,
                    year = 0,
                    badges = badgesOf(v),
                    pinned = false,
                    showId = extra.showId,
                    video = v,
                    tvshow = null,
                )
            }
            is Movie -> MediaItem(
                key = "m:${v.id}",
                kind = Kind.MOVIE,
                title = v.name ?: "",
                subtitle = v.remainingLabel() ?: v.year.takeIf { it > 0 }?.toString(),
                poster = posterOf(v.posterUri),
                wide = wideArt,
                progress = v.progressFraction(),
                watched = v.isWatched,
                year = v.year,
                badges = badgesOf(v),
                pinned = v.isPinned,
                showId = -1,
                video = v,
                tvshow = null,
            )
            else -> MediaItem(
                key = "v:${v.id}",
                kind = Kind.FILE,
                title = v.name ?: v.fileNameWithExtension ?: "",
                subtitle = v.remainingLabel() ?: v.friendlyPath ?: v.filePath,
                poster = posterOf(v.posterUri),
                wide = posterOf(v.posterUri),
                progress = v.progressFraction(),
                watched = v.isWatched,
                year = 0,
                badges = badgesOf(v),
                pinned = false,
                showId = -1,
                video = v,
                tvshow = null,
            )
        }
    }

    private fun itemOf(show: Tvshow): MediaItem = MediaItem(
        key = "s:${show.tvshowId}",
        kind = Kind.SHOW,
        title = show.name ?: "",
        subtitle = seasonsLabel(show.seasonCount),
        poster = posterOf(show.posterUri),
        wide = null,
        progress = null,
        watched = show.isWatched,
        year = show.year,
        badges = emptyList(),
        pinned = show.isPinned,
        showId = show.tvshowId,
        video = null,
        tvshow = show,
    )

    private fun seasonsLabel(n: Int) = if (n == 1) "1 season" else "$n seasons"

    // ---------------------------------------------------------------- home

    suspend fun home(): HomeData = onLibrary("Loading Home", { HomeData(null, emptyList(), true) }) {
        val continueWatching = readVideos(WatchingUpNextLoader(context), detailed = true, limit = 20)
            .map { (v, e) -> itemOf(v, e) }
            .map { item ->
                val v = item.video
                if (item.progress == null && v is Episode) item.copy(subtitle = "Up next · ${v.code()}") else item
            }

        val shows = readShows(AllTvshowsLoader(context))
        val movies = readVideos(MoviesLoader(context, true), detailed = true)
        val showItems = shows.map { itemOf(it) }
        val movieItems = movies.map { (v, e) -> itemOf(v, e) }

        // recently added: one tile per show, newest first
        val recent = ArrayList<MediaItem>()
        val seenShows = HashSet<Long>()
        val showById = shows.associateBy { it.tvshowId }
        for ((v, e) in readVideos(LastAddedLoader(context), detailed = true, limit = 120)) {
            if (recent.size >= 20) break
            if (v is Episode) {
                if (e.showId <= 0 || !seenShows.add(e.showId)) continue
                val show = showById[e.showId]
                if (show != null) recent.add(itemOf(show)) else recent.add(itemOf(v, e).copy(kind = Kind.SHOW, key = "s:${e.showId}"))
            } else if (v is Movie) {
                recent.add(itemOf(v, e))
            }
        }

        val pinned = (showItems.filter { it.pinned } + movieItems.filter { it.pinned })

        val rows = buildList {
            if (continueWatching.isNotEmpty()) add(Shelf("Continue watching", continueWatching, RowStyle.LANDSCAPE))
            if (pinned.isNotEmpty()) add(Shelf("My list", pinned, RowStyle.POSTER))
            if (recent.isNotEmpty()) add(Shelf("Recently added", recent, RowStyle.POSTER))
            if (showItems.isNotEmpty()) add(Shelf("TV shows", showItems.take(40), RowStyle.POSTER))
            if (movieItems.isNotEmpty()) add(Shelf("Movies", movieItems.take(40), RowStyle.POSTER))
            val unwatchedMovies = movieItems.filter { !it.watched && it.progress == null }
            if (unwatchedMovies.size >= 6) add(Shelf("Movies you haven't watched", unwatchedMovies.shuffled(java.util.Random(System.currentTimeMillis() / 86_400_000L)).take(30), RowStyle.POSTER))
        }

        HomeData(
            hero = pickHero(continueWatching, recent, showById),
            rows = rows,
            isEmpty = rows.isEmpty(),
        )
    }

    private fun pickHero(continueWatching: List<MediaItem>, recent: List<MediaItem>, showById: Map<Long, Tvshow>): Hero? {
        val candidate = continueWatching.firstOrNull() ?: recent.firstOrNull() ?: return null
        return try {
            when (candidate.kind) {
                Kind.EPISODE -> {
                    val ep = candidate.video as Episode
                    val show = showById[candidate.showId] ?: loadShow(candidate.showId)
                    val tags = show?.let { tagsOf(it) }
                    val genres = (tags as? VideoTags)?.genres.orEmpty().take(2)
                    Hero(
                        item = show?.let { itemOf(it) } ?: candidate,
                        title = show?.name ?: candidate.title,
                        meta = (genres + listOfNotNull(show?.seasonCount?.takeIf { it > 0 }?.let { seasonsLabel(it) })).joinToString(" · "),
                        art = image(tags?.defaultBackdrop) ?: candidate.wide ?: candidate.poster,
                        badges = candidate.badges,
                        playTarget = ep,
                        playLabel = if (candidate.progress != null) "Resume ${ep.code()}" else "Play ${ep.code()}",
                    )
                }
                Kind.SHOW -> {
                    val show = candidate.tvshow ?: loadShow(candidate.showId)
                    val tags = show?.let { tagsOf(it) }
                    val genres = (tags as? VideoTags)?.genres.orEmpty().take(2)
                    val first = show?.let { firstEpisodeToPlay(it.tvshowId) }
                    Hero(
                        item = candidate,
                        title = candidate.title,
                        meta = (genres + listOfNotNull(show?.seasonCount?.takeIf { it > 0 }?.let { seasonsLabel(it) })).joinToString(" · "),
                        art = image(tags?.defaultBackdrop) ?: candidate.poster,
                        badges = badgesOf(first?.first),
                        playTarget = first?.first,
                        playLabel = first?.second ?: "Play",
                    )
                }
                else -> {
                    val v = candidate.video
                    val tags = v?.let { tagsOf(it) }
                    val genres = (tags as? VideoTags)?.genres.orEmpty().take(2)
                    Hero(
                        item = candidate,
                        title = candidate.title,
                        meta = (listOfNotNull(candidate.year.takeIf { it > 0 }?.toString()) + genres).joinToString(" · "),
                        art = candidate.wide ?: image(tags?.defaultBackdrop) ?: candidate.poster,
                        badges = candidate.badges,
                        playTarget = v,
                        playLabel = if (candidate.progress != null) "Resume" else "Play",
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "pickHero failed", e)
            null
        }
    }

    // ---------------------------------------------------------------- shows

    private fun allEpisodes(showId: Long): List<Episode> =
        readVideos(EpisodesLoader(context, showId, -1, true), detailed = true).mapNotNull { it.first as? Episode }

    /** Same rule as Nova's TV interface: a started episode wins, otherwise the first unwatched one (specials last). */
    private fun pickResume(episodes: List<Episode>): Episode? {
        val regular = episodes.filter { it.seasonNumber > 0 }.ifEmpty { episodes }
        val started = episodes.filter { it.progressFraction() != null }.maxByOrNull { it.lastPlayed }
        if (started != null) return started
        return regular.firstOrNull { !it.isWatched } ?: regular.firstOrNull()
    }

    private fun resumeLabel(ep: Episode?): String = when {
        ep == null -> "Play"
        ep.progressFraction() != null -> "Resume ${ep.code()}"
        else -> "Play ${ep.code()}"
    }

    private fun firstEpisodeToPlay(showId: Long): Pair<Episode, String>? {
        val ep = pickResume(allEpisodes(showId)) ?: return null
        return ep to resumeLabel(ep)
    }

    suspend fun showDetails(showId: Long): Pair<ShowDetails, Map<Int, List<EpisodeRow>>>? = onLibrary("Loading show $showId", { null }) {
        val show = loadShow(showId) ?: return@onLibrary null
        val tags = tagsOf(show)
        val episodes = allEpisodes(showId)
        val bySeason = episodes.groupBy { it.seasonNumber }.toSortedMap(compareBy<Int> { if (it == 0) Int.MAX_VALUE else it })
        val seasons = bySeason.map { (n, eps) ->
            SeasonInfo(
                season = Season(showId, show.name, show.posterUri, n, eps.size, eps.count { it.isWatched }),
                number = n,
                label = if (n == 0) "Specials" else "Season $n",
                allWatched = eps.all { it.isWatched },
            )
        }
        val rows = bySeason.mapValues { (_, eps) ->
            eps.map { ep ->
                val remaining = ep.remainingLabel()
                EpisodeRow(
                    episode = ep,
                    number = ep.episodeNumber,
                    title = "${ep.episodeNumber}. ${ep.episodeName ?: "Episode ${ep.episodeNumber}"}",
                    meta = listOfNotNull(
                        remaining ?: ep.durationMs.takeIf { it > 0 }?.let { formatMinutes(it.toLong()) },
                        badgesOf(ep).take(2).joinToString(" · ").takeIf { it.isNotEmpty() },
                    ).joinToString(" · "),
                    plot = ep.descriptionBody?.takeIf { it.isNotBlank() },
                    still = ep.pictureUri ?: ep.posterUri,
                    progress = ep.progressFraction(),
                    watched = ep.isWatched,
                )
            }
        }
        val resume = pickResume(episodes)
        val details = ShowDetails(
            tvshow = show,
            title = show.name ?: "",
            year = show.year,
            contentRating = show.contentRating?.takeIf { it.isNotBlank() },
            rating = show.rating,
            plot = (show.plot ?: tags?.plot)?.takeIf { it.isNotBlank() },
            genres = (tags as? VideoTags)?.genres.orEmpty(),
            backdrop = image(tags?.defaultBackdrop),
            poster = show.posterUri,
            imdbId = tags?.imdbId?.takeIf { it.isNotBlank() },
            seasons = seasons,
            resume = resume,
            resumeLabel = resumeLabel(resume),
            resumeProgress = resume?.progressFraction(),
            badges = badgesOf(resume ?: episodes.firstOrNull()),
            pinned = show.isPinned,
        )
        details to rows
    }

    // ---------------------------------------------------------------- movies

    suspend fun movieDetails(original: Movie): MovieDetails =
        onLibrary("Loading movie ${original.id}", { movieDetailsOf(original, null) }) {
            // re-read so resume point / watched state are current after coming back from the player
            val movie = (readVideos(SingleVideoLoader(context, original.id), detailed = true).firstOrNull()?.first as? Movie) ?: original
            movieDetailsOf(movie, tagsOf(movie))
        }

    private fun movieDetailsOf(movie: Movie, tags: BaseTags?): MovieDetails {
        val genres = (tags as? VideoTags)?.genres.orEmpty()
        return MovieDetails(
            movie = movie,
            title = movie.name ?: "",
            year = movie.year,
            contentRating = movie.contentRating?.takeIf { it.isNotBlank() },
            rating = movie.rating,
            runtime = movie.durationMs.takeIf { it > 0 }?.let { formatMinutes(it.toLong()) },
            plot = (movie.descriptionBody ?: tags?.plot)?.takeIf { it.isNotBlank() },
            genres = genres,
            directors = tags?.directorsFormatted?.takeIf { it.isNotBlank() },
            cast = tags?.actorsFormatted?.takeIf { it.isNotBlank() },
            backdrop = image(tags?.defaultBackdrop) ?: movie.posterUri,
            poster = movie.posterUri,
            imdbId = tags?.imdbId?.takeIf { it.isNotBlank() },
            badges = badgesOf(movie),
            progress = movie.progressFraction(),
            remaining = movie.remainingLabel(),
            watched = movie.isWatched,
            pinned = movie.isPinned,
        )
    }

    // ---------------------------------------------------------------- library grids

    suspend fun allMovies(): List<MediaItem> = onLibrary("Loading movies", { emptyList() }) {
        readVideos(MoviesLoader(context, true), detailed = true).map { (v, e) -> itemOf(v, e) }
    }

    suspend fun allShows(): List<MediaItem> = onLibrary("Loading TV shows", { emptyList() }) {
        readShows(AllTvshowsLoader(context)).map { itemOf(it) }
    }

    // ---------------------------------------------------------------- search

    suspend fun search(query: String): SearchResults = onLibrary("Searching", { SearchResults(query.trim(), emptyList(), emptyList(), emptyList(), emptyList()) }) {
        val q = query.trim()
        if (q.isEmpty()) return@onLibrary SearchResults(q, emptyList(), emptyList(), emptyList(), emptyList())
        val loader = SearchVideoLoader(context)
        loader.setQuery(q)
        val hits = readVideos(loader, detailed = false, limit = 300)
        val needle = q.lowercase(Locale.ROOT)
        val shows = LinkedHashMap<Long, MediaItem>()
        val episodes = ArrayList<MediaItem>()
        val movies = ArrayList<MediaItem>()
        val files = ArrayList<MediaItem>()
        for ((v, e) in hits) {
            when (v) {
                is Episode -> {
                    if ((v.showName ?: "").lowercase(Locale.ROOT).contains(needle) && e.showId > 0 && !shows.containsKey(e.showId)) {
                        shows[e.showId] = MediaItem(
                            key = "s:${e.showId}", kind = Kind.SHOW, title = v.showName ?: "",
                            subtitle = "TV show", poster = v.posterUri, wide = null, progress = null,
                            watched = false, year = 0, badges = emptyList(), pinned = false,
                            showId = e.showId, video = null, tvshow = null,
                        )
                    }
                    if ((v.episodeName ?: "").lowercase(Locale.ROOT).contains(needle) && episodes.size < 30) {
                        episodes.add(itemOf(v, e).copy(
                            title = v.episodeName ?: "",
                            subtitle = "${v.showName ?: ""} · ${v.code()}",
                        ))
                    }
                }
                is Movie -> if (movies.size < 30) movies.add(itemOf(v, e))
                else -> if (files.size < 30) files.add(itemOf(v, e))
            }
        }
        SearchResults(q, shows.values.take(12), movies, episodes, files)
    }

    /** The TV show a video belongs to (internal show id), or -1. */
    suspend fun showIdForVideo(videoId: Long): Long = onLibrary("Finding show for video $videoId", { -1L }) {
        context.contentResolver.query(
            VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID),
            "_id=?",
            arrayOf(videoId.toString()),
            null,
        )?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }

    companion object {
        private const val TAG = "NovaModernRepo"
    }
}
