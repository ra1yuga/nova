// Nova Modern: show and movie detail screens
package com.archos.mediacenter.video.modern.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archos.mediacenter.video.browser.adapters.`object`.Movie
import com.archos.mediacenter.video.modern.data.EpisodeRow
import com.archos.mediacenter.video.modern.data.LibraryRepository
import com.archos.mediacenter.video.modern.data.MovieDetails
import com.archos.mediacenter.video.modern.data.ShowDetails
import java.util.Locale

private val HeaderHeight = 260.dp

@Composable
fun ShowDetailScreen(
    showId: Long,
    repo: LibraryRepository,
    version: Int,
    actions: NovaActions,
    onBack: () -> Unit,
) {
    val loaded by produceState<Pair<ShowDetails, Map<Int, List<EpisodeRow>>>?>(initialValue = null, showId, version) {
        value = repo.showDetails(showId)
    }
    var chosenSeason by rememberSaveable(showId) { mutableIntStateOf(Int.MIN_VALUE) }
    var pinnedOverride by remember(showId, version) { mutableStateOf<Boolean?>(null) }
    var watchedOverride by remember(showId, version) { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    var plotExpanded by remember(showId) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Nova.Black)) {
        val pair = loaded
        if (pair == null) {
            LoadingIndicator()
        } else {
            val (d, episodesBySeason) = pair
            val seasonNumbers = d.seasons.map { it.number }
            val season = when {
                chosenSeason in seasonNumbers -> chosenSeason
                d.resume != null && d.resume.seasonNumber in seasonNumbers -> d.resume.seasonNumber
                else -> seasonNumbers.firstOrNull() ?: 1
            }
            val seasonInfo = d.seasons.firstOrNull { it.number == season }
            val seasonWatched = watchedOverride[season] ?: (seasonInfo?.allWatched ?: false)
            val pinned = pinnedOverride ?: d.pinned
            val episodes = episodesBySeason[season].orEmpty()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            LazyColumn(
                contentPadding = PaddingValues(bottom = navBottom + 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    DetailHeader(art = d.backdrop ?: d.poster, title = d.title)
                }
                item(key = "meta") {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetaLine(
                            year = d.year,
                            contentRating = d.contentRating,
                            extra = when (d.seasons.count { it.number > 0 }) {
                                0 -> null
                                1 -> "1 season"
                                else -> "${d.seasons.count { it.number > 0 }} seasons"
                            },
                            rating = d.rating,
                        )
                        BadgeRow(d.badges, filled = true)
                        if (d.resume != null) {
                            PrimaryButton(d.resumeLabel, NovaIcons.Play, onClick = { actions.play(d.resume) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            d.resumeProgress?.let { p ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ProgressLine(p, Modifier.weight(1f).clip(RoundedCornerShape(2.dp)))
                                    Text(
                                        listOfNotNull(d.resume.episodeName, remainingOf(d.resume)).joinToString(" · "),
                                        style = NovaType.Small,
                                        color = Nova.Muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            IconAction(
                                if (seasonWatched) NovaIcons.Check else NovaIcons.Check,
                                if (seasonWatched) "Watched" else "Mark watched",
                                tint = if (seasonWatched) Nova.Accent else Nova.Text,
                                onClick = {
                                    seasonInfo?.let {
                                        actions.markSeasonWatched(it.season, !seasonWatched)
                                        watchedOverride = watchedOverride + (season to !seasonWatched)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            IconAction(
                                if (pinned) NovaIcons.Check else NovaIcons.Plus,
                                "My list",
                                tint = if (pinned) Nova.Accent else Nova.Text,
                                onClick = {
                                    actions.setPinned(d.tvshow, !pinned)
                                    pinnedOverride = !pinned
                                },
                                modifier = Modifier.weight(1f),
                            )
                            IconAction(
                                NovaIcons.External,
                                "IMDb",
                                onClick = { actions.openImdb(d.imdbId, d.title, d.year) },
                                modifier = Modifier.weight(1f),
                            )
                            IconAction(
                                NovaIcons.Pencil,
                                "More",
                                onClick = { (d.resume ?: episodes.firstOrNull()?.episode)?.let { actions.openInfo(it) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        d.plot?.let { plot ->
                            Text(
                                plot,
                                style = NovaType.Body,
                                color = Nova.TextSoft,
                                maxLines = if (plotExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.combinedClickableCompat(onClick = { plotExpanded = !plotExpanded }),
                            )
                        }
                        if (d.genres.isNotEmpty()) {
                            Text("Genres: " + d.genres.joinToString(", "), style = NovaType.Small, color = Nova.Muted)
                        }
                    }
                }
                item(key = "seasons") {
                    if (d.seasons.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                        ) {
                            items(d.seasons, key = { it.number }) { s ->
                                Pill(s.label, selected = s.number == season, onClick = { chosenSeason = s.number }, solidWhite = true)
                            }
                        }
                    } else {
                        SectionTitle("Episodes", Modifier.padding(start = 16.dp, top = 22.dp, bottom = 6.dp))
                    }
                }
                items(episodes, key = { "ep:" + it.episode.id }) { row ->
                    EpisodeRowView(
                        row = row,
                        onClick = { actions.play(row.episode) },
                        onLongClick = { actions.openInfo(row.episode) },
                    )
                }
            }
        }
        DetailTopButtons(onBack)
    }
}

@Composable
fun MovieDetailScreen(
    movie: Movie,
    repo: LibraryRepository,
    version: Int,
    actions: NovaActions,
    onBack: () -> Unit,
) {
    val details by produceState<MovieDetails?>(initialValue = null, movie.id, version) {
        value = repo.movieDetails(movie)
    }
    var pinnedOverride by remember(movie.id, version) { mutableStateOf<Boolean?>(null) }
    var watchedOverride by remember(movie.id, version) { mutableStateOf<Boolean?>(null) }
    var plotExpanded by remember(movie.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Nova.Black)) {
        val d = details
        if (d == null) {
            LoadingIndicator()
        } else {
            val pinned = pinnedOverride ?: d.pinned
            val watched = watchedOverride ?: d.watched
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                contentPadding = PaddingValues(bottom = navBottom + 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") { DetailHeader(art = d.backdrop ?: d.poster, title = d.title) }
                item(key = "body") {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetaLine(year = d.year, contentRating = d.contentRating, extra = d.runtime, rating = d.rating)
                        BadgeRow(d.badges, filled = true)
                        PrimaryButton(
                            if (d.progress != null) "Resume" else "Play",
                            NovaIcons.Play,
                            onClick = { actions.play(d.movie) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        if (d.progress != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ProgressLine(d.progress, Modifier.weight(1f).clip(RoundedCornerShape(2.dp)))
                                d.remaining?.let { Text(it, style = NovaType.Small, color = Nova.Muted) }
                            }
                            SecondaryButton("Play from the start", null, onClick = { actions.play(d.movie, fromStart = true) }, modifier = Modifier.fillMaxWidth())
                        }
                        Row(Modifier.fillMaxWidth()) {
                            IconAction(
                                NovaIcons.Check,
                                if (watched) "Watched" else "Mark watched",
                                tint = if (watched) Nova.Accent else Nova.Text,
                                onClick = {
                                    actions.markWatched(d.movie, !watched)
                                    watchedOverride = !watched
                                },
                                modifier = Modifier.weight(1f),
                            )
                            IconAction(
                                if (pinned) NovaIcons.Check else NovaIcons.Plus,
                                "My list",
                                tint = if (pinned) Nova.Accent else Nova.Text,
                                onClick = {
                                    actions.setPinned(d.movie, !pinned)
                                    pinnedOverride = !pinned
                                },
                                modifier = Modifier.weight(1f),
                            )
                            IconAction(NovaIcons.External, "IMDb", onClick = { actions.openImdb(d.imdbId, d.title, d.year) }, modifier = Modifier.weight(1f))
                            IconAction(NovaIcons.Pencil, "More", onClick = { actions.openInfo(d.movie) }, modifier = Modifier.weight(1f))
                        }
                        d.plot?.let { plot ->
                            Text(
                                plot,
                                style = NovaType.Body,
                                color = Nova.TextSoft,
                                maxLines = if (plotExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.combinedClickableCompat(onClick = { plotExpanded = !plotExpanded }),
                            )
                        }
                        if (d.genres.isNotEmpty()) InfoLine("Genres", d.genres.joinToString(", "))
                        d.directors?.let { InfoLine("Director", it) }
                        d.cast?.let { InfoLine("Cast", it) }
                    }
                }
            }
        }
        DetailTopButtons(onBack)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        "$label: $value",
        style = NovaType.Small,
        color = Nova.Muted,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailHeader(art: Any?, title: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
    ) {
        Artwork(art, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x80000000),
                        0.3f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black,
                    )
                )
        )
        Text(
            title,
            style = NovaType.Title,
            color = Nova.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun DetailTopButtons(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        RoundIconButton(NovaIcons.Back, "Back", onClick = onBack, background = Color(0x80000000))
    }
}

@Composable
private fun MetaLine(year: Int, contentRating: String?, extra: String?, rating: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (year > 0) Text(year.toString(), style = NovaType.Meta, color = Nova.TextSoft)
        contentRating?.let {
            Text(
                it,
                style = NovaType.Tiny,
                color = Nova.TextSoft,
                modifier = Modifier
                    .border(1.dp, Nova.Dim, RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        extra?.let { Text(it, style = NovaType.Meta, color = Nova.TextSoft) }
        if (rating > 0f) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(NovaIcons.Star, contentDescription = null, tint = Nova.Accent, modifier = Modifier.size(13.dp))
                Text(String.format(Locale.US, "%.1f TMDb", rating), style = NovaType.Meta, color = Nova.TextSoft)
            }
        }
    }
}

@Composable
private fun EpisodeRowView(row: EpisodeRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickableCompat(onClick = onClick, onLongClick = onLongClick, role = Role.Button, label = row.title)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .width(128.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Artwork(row.still, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color(0x8C000000))
                        .border(1.5.dp, Color(0xD9F4F1EA), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NovaIcons.Play, contentDescription = null, tint = Nova.Text, modifier = Modifier.size(13.dp))
                }
                row.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.title, style = NovaType.BodyStrong, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (row.meta.isNotBlank()) Text(row.meta, style = NovaType.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (row.watched) {
                Icon(NovaIcons.Check, contentDescription = "Watched", tint = Nova.Accent, modifier = Modifier.size(18.dp))
            } else {
                Spacer(Modifier.size(18.dp))
            }
        }
        row.plot?.let {
            Text(it, style = NovaType.Small.copy(fontWeight = FontWeight.Normal), color = Nova.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun remainingOf(ep: com.archos.mediacenter.video.browser.adapters.`object`.Episode): String? {
    val d = ep.durationMs
    val r = ep.resumeMs
    if (d <= 0 || r <= 0) return null
    val min = ((d - r) / 60000).coerceAtLeast(1)
    return if (min >= 60) "${min / 60}h ${min % 60}m left" else "${min}m left"
}
