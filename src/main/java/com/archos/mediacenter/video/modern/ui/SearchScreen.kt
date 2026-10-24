// Nova Modern: search across shows, movies, episodes and unidentified files in Nova's library
package com.archos.mediacenter.video.modern.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archos.mediacenter.video.modern.data.Kind
import com.archos.mediacenter.video.modern.data.LibraryRepository
import com.archos.mediacenter.video.modern.data.MediaItem
import com.archos.mediacenter.video.modern.data.SearchResults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    repo: LibraryRepository,
    bottomPad: Dp,
    onOpen: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    actions: NovaActions,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var recents by remember { mutableStateOf(actions.recentSearches()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val version by repo.version.collectAsStateWithLifecycle()

    val results by produceState<SearchResults?>(initialValue = null, query, version) {
        if (query.isBlank()) {
            value = null
        } else {
            delay(250)
            value = repo.search(query)
        }
    }

    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            try { focus.requestFocus() } catch (_: Exception) { }
        }
    }

    val commit: () -> Unit = {
        if (query.isNotBlank()) {
            actions.rememberSearch(query.trim())
            recents = actions.recentSearches()
        }
        keyboard?.hide()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Nova.Surface2)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(NovaIcons.Search, contentDescription = null, tint = Nova.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Shows, movies, episodes, files", style = NovaType.BodyStrong.copy(fontSize = 16.sp), color = Nova.Dim)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = NovaType.BodyStrong.copy(fontSize = 16.sp, color = Nova.Text),
                    cursorBrush = SolidColor(Nova.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { commit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                RoundIconButton(NovaIcons.Close, "Clear search", onClick = { query = "" })
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = bottomPad + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val r = results
            if (query.isBlank()) {
                if (recents.isNotEmpty()) {
                    item(key = "recent-title") { SectionTitle("Recent") }
                    items(recents, key = { "recent:$it" }) { text ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .combinedClickableCompat(onClick = { query = text }, role = Role.Button),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(NovaIcons.Clock, contentDescription = null, tint = Nova.Muted, modifier = Modifier.size(20.dp))
                            Text(text, style = NovaType.BodyStrong.copy(fontSize = 15.sp), color = Nova.Text)
                        }
                    }
                } else {
                    item(key = "hint") {
                        Text(
                            "Search finds titles, episode names and file names in your library.",
                            style = NovaType.Body,
                            color = Nova.Muted,
                        )
                    }
                }
            } else if (r != null && r.query == query.trim() && r.isEmpty) {
                item(key = "none") {
                    Text(
                        "Nothing in your library matches “${query.trim()}”.",
                        style = NovaType.BodyStrong.copy(fontSize = 15.sp),
                        color = Nova.Muted,
                        modifier = Modifier.padding(vertical = 40.dp),
                    )
                }
            } else if (r != null) {
                section("Shows", r.shows, onOpen, onOpen)
                section("Movies", r.movies, onOpen, onOpen)
                section("Episodes", r.episodes, onPlayItem, onOpen)
                section("Unidentified files", r.files, onPlayItem, onOpen)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<MediaItem>,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "title:$title") {
        Text(
            title.uppercase(),
            style = NovaType.Label,
            color = Nova.Muted,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
    }
    items(list, key = { "$title:${it.key}" }) { item ->
        ResultRow(item, onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
    }
}

@Composable
private fun ResultRow(item: MediaItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val portrait = item.kind == Kind.SHOW || item.kind == Kind.MOVIE
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .combinedClickableCompat(onClick = onClick, onLongClick = onLongClick, role = Role.Button, label = item.title),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(if (portrait) 56.dp else 96.dp)
                .height(if (portrait) 84.dp else 54.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            Artwork(if (portrait) item.poster else (item.wide ?: item.poster), Modifier.fillMaxSize())
            item.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, style = NovaType.BodyStrong.copy(fontSize = 15.sp), color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let { Text(it, style = NovaType.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color(0x8CF4F1EA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (item.kind == Kind.SHOW) NovaIcons.Chevron else NovaIcons.Play,
                contentDescription = null,
                tint = Nova.Text,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
