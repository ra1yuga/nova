// Nova Modern: app shell — bottom tabs, a simple back stack for detail screens, shared caches
package com.archos.mediacenter.video.modern.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archos.mediacenter.video.browser.adapters.`object`.Base
import com.archos.mediacenter.video.browser.adapters.`object`.Movie
import com.archos.mediacenter.video.browser.adapters.`object`.Season
import com.archos.mediacenter.video.browser.adapters.`object`.Video
import com.archos.mediacenter.video.modern.data.HomeData
import com.archos.mediacenter.video.modern.data.Kind
import com.archos.mediacenter.video.modern.data.LibraryRepository
import com.archos.mediacenter.video.modern.data.MediaItem

/** Everything the UI asks the hosting activity to do (play, open IMDb, mark watched...). */
interface NovaActions {
    fun play(video: Video, fromStart: Boolean = false)
    fun openImdb(imdbId: String?, title: String, year: Int)
    fun openInfo(video: Video)
    fun openSettings()
    fun openClassic()
    fun openNetworkSetup()
    fun markWatched(video: Video, watched: Boolean)
    fun markSeasonWatched(season: Season, watched: Boolean)
    fun setPinned(item: Base, pinned: Boolean)
    fun playFile(file: Any)
    fun rememberSearch(query: String)
    fun hasProblemReport(): Boolean
    fun shareProblemReport()
    fun recentSearches(): List<String>
}

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", NovaIcons.Home),
    MOVIES("Movies", NovaIcons.Movies),
    SHOWS("Shows", NovaIcons.Shows),
    FOLDERS("Folders", NovaIcons.Folder),
    SEARCH("Search", NovaIcons.Search),
}

sealed interface Route {
    data class Show(val showId: Long) : Route
    data class MovieDetail(val movie: Movie) : Route
    data class Folder(val title: String, val uri: String) : Route
}

/** Data kept across tab switches so coming back is instant. */
class UiCache {
    var home by mutableStateOf<HomeData?>(null)
    var movies by mutableStateOf<List<MediaItem>?>(null)
    var shows by mutableStateOf<List<MediaItem>?>(null)
    var loadedVersion by mutableStateOf(-1)
}

val BottomBarHeight = 64.dp

@Composable
fun NovaApp(repo: LibraryRepository, actions: NovaActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Route>() }
    val cache = remember { UiCache() }
    val version by repo.version.collectAsStateWithLifecycle()

    val homeList = rememberLazyListState()
    val moviesGrid = rememberLazyGridState()
    val showsGrid = rememberLazyGridState()

    LaunchedEffect(version) {
        cache.home = repo.home()
        if (cache.movies != null) cache.movies = repo.allMovies()
        if (cache.shows != null) cache.shows = repo.allShows()
        cache.loadedVersion = version
    }

    val openItem: (MediaItem) -> Unit = { item ->
        when (item.kind) {
            Kind.SHOW, Kind.EPISODE -> if (item.showId > 0) stack.add(Route.Show(item.showId)) else item.video?.let { actions.play(it) }
            Kind.MOVIE -> (item.video as? Movie)?.let { stack.add(Route.MovieDetail(it)) }
            Kind.FILE -> item.video?.let { actions.play(it) }
        }
    }
    val playItem: (MediaItem) -> Unit = { item ->
        val v = item.video
        if (v != null) actions.play(v) else openItem(item)
    }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.HOME) { tab = Tab.HOME }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad: Dp = BottomBarHeight + navBottom

    Box(Modifier.fillMaxSize().background(Nova.Black)) {
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    data = cache.home,
                    listState = homeList,
                    bottomPad = bottomPad,
                    onOpen = openItem,
                    onPlayItem = playItem,
                    onPlay = { actions.play(it) },
                    onTab = { tab = it },
                    actions = actions,
                )
                Tab.MOVIES -> {
                    LaunchedEffect(Unit) { if (cache.movies == null) cache.movies = repo.allMovies() }
                    LibraryScreen(
                        title = "Movies",
                        items = cache.movies,
                        isMovies = true,
                        gridState = moviesGrid,
                        bottomPad = bottomPad,
                        onOpen = openItem,
                    )
                }
                Tab.SHOWS -> {
                    LaunchedEffect(Unit) { if (cache.shows == null) cache.shows = repo.allShows() }
                    LibraryScreen(
                        title = "TV shows",
                        items = cache.shows,
                        isMovies = false,
                        gridState = showsGrid,
                        bottomPad = bottomPad,
                        onOpen = openItem,
                    )
                }
                Tab.FOLDERS -> FoldersScreen(
                    bottomPad = bottomPad,
                    onOpenFolder = { title, uri -> stack.add(Route.Folder(title, uri)) },
                    actions = actions,
                )
                Tab.SEARCH -> SearchScreen(
                    repo = repo,
                    bottomPad = bottomPad,
                    onOpen = openItem,
                    onPlayItem = playItem,
                    actions = actions,
                )
            }
            BottomBar(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        AnimatedContent(
            targetState = stack.lastOrNull(),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "detail",
        ) { route ->
            when (route) {
                null -> Box(Modifier)
                is Route.Show -> ShowDetailScreen(
                    showId = route.showId,
                    repo = repo,
                    version = version,
                    actions = actions,
                    onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                )
                is Route.MovieDetail -> MovieDetailScreen(
                    movie = route.movie,
                    repo = repo,
                    version = version,
                    actions = actions,
                    onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                )
                is Route.Folder -> FolderScreen(
                    title = route.title,
                    uri = route.uri,
                    actions = actions,
                    onOpenFolder = { title, uri -> stack.add(Route.Folder(title, uri)) },
                    onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xF0000000))
    ) {
        HorizontalDivider(thickness = 1.dp, color = Nova.Line)
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .height(BottomBarHeight)
        ) {
            Tab.entries.forEach { t ->
                val active = t == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .combinedClickableCompat(onClick = { onSelect(t) }, role = Role.Tab, label = t.label),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(t.icon, contentDescription = null, tint = if (active) Nova.Accent else Nova.Muted, modifier = Modifier.size(24.dp))
                    Text(
                        t.label,
                        style = if (active) NovaType.Tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) else NovaType.Tiny,
                        color = if (active) Nova.Text else Nova.Muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}
