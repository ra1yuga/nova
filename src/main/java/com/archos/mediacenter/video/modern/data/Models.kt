// Nova Modern: UI-side models built from Nova's own library objects
package com.archos.mediacenter.video.modern.data

import androidx.compose.runtime.Immutable
import com.archos.mediacenter.video.browser.adapters.`object`.Episode
import com.archos.mediacenter.video.browser.adapters.`object`.Movie
import com.archos.mediacenter.video.browser.adapters.`object`.Season
import com.archos.mediacenter.video.browser.adapters.`object`.Tvshow
import com.archos.mediacenter.video.browser.adapters.`object`.Video

enum class Kind { MOVIE, EPISODE, SHOW, FILE }

/** One tile anywhere in the UI: a movie, an episode, a show or an unscraped file. */
@Immutable
data class MediaItem(
    val key: String,
    val kind: Kind,
    val title: String,
    val subtitle: String?,
    /** Portrait poster (file path or URL). */
    val poster: Any?,
    /** Landscape art: episode still or backdrop (file path or URL). */
    val wide: Any?,
    /** 0..1 when partially watched, null otherwise. */
    val progress: Float?,
    val watched: Boolean,
    val year: Int,
    val badges: List<String>,
    val pinned: Boolean,
    val showId: Long,
    val video: Video?,
    val tvshow: Tvshow?,
)

@Immutable
data class HomeData(
    val hero: Hero?,
    val rows: List<Shelf>,
    val isEmpty: Boolean,
)

@Immutable
data class Shelf(val title: String, val items: List<MediaItem>, val style: RowStyle)

enum class RowStyle { LANDSCAPE, POSTER }

/** The big banner on top of Home. */
@Immutable
data class Hero(
    val item: MediaItem,
    val title: String,
    val meta: String,
    val art: Any?,
    val badges: List<String>,
    /** What the main button plays, and its label. */
    val playTarget: Video?,
    val playLabel: String,
)

@Immutable
data class ShowDetails(
    val tvshow: Tvshow,
    val title: String,
    val year: Int,
    val contentRating: String?,
    val rating: Float,
    val plot: String?,
    val genres: List<String>,
    val backdrop: Any?,
    val poster: Any?,
    val imdbId: String?,
    val seasons: List<SeasonInfo>,
    val resume: Episode?,
    val resumeLabel: String,
    val resumeProgress: Float?,
    val badges: List<String>,
    val pinned: Boolean,
)

@Immutable
data class SeasonInfo(
    val season: Season,
    val number: Int,
    val label: String,
    val allWatched: Boolean,
)

@Immutable
data class EpisodeRow(
    val episode: Episode,
    val number: Int,
    val title: String,
    val meta: String,
    val plot: String?,
    val still: Any?,
    val progress: Float?,
    val watched: Boolean,
)

@Immutable
data class MovieDetails(
    val movie: Movie,
    val title: String,
    val year: Int,
    val contentRating: String?,
    val rating: Float,
    val runtime: String?,
    val plot: String?,
    val genres: List<String>,
    val directors: String?,
    val cast: String?,
    val backdrop: Any?,
    val poster: Any?,
    val imdbId: String?,
    val badges: List<String>,
    val progress: Float?,
    val remaining: String?,
    val watched: Boolean,
    val pinned: Boolean,
)

@Immutable
data class SearchResults(
    val query: String,
    val shows: List<MediaItem>,
    val movies: List<MediaItem>,
    val episodes: List<MediaItem>,
    val files: List<MediaItem>,
) {
    val isEmpty get() = shows.isEmpty() && movies.isEmpty() && episodes.isEmpty() && files.isEmpty()
}

@Immutable
data class Source(val title: String, val subtitle: String, val uri: String, val isNetwork: Boolean)

@Immutable
data class FolderEntry(
    val name: String,
    val isDirectory: Boolean,
    val uri: String,
    val sizeLabel: String?,
    val file: Any?,
)

internal fun Episode.code(): String = "S${seasonNumber}:E${episodeNumber}"

internal fun Video.progressFraction(): Float? {
    val d = durationMs
    val r = resumeMs
    if (isWatched || d <= 0 || r <= 0) return null
    return (r.toFloat() / d).coerceIn(0.02f, 0.98f)
}

internal fun formatMinutes(ms: Long): String {
    val totalMin = (ms / 60000L).toInt()
    if (totalMin <= 0) return "1m"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

internal fun Video.remainingLabel(): String? {
    val d = durationMs
    val r = resumeMs
    if (d <= 0 || r <= 0 || isWatched) return null
    return formatMinutes((d - r).toLong()) + " left"
}
