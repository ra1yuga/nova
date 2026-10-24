// Nova Modern: Movies / TV shows grids with quick filters
package com.archos.mediacenter.video.modern.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archos.mediacenter.video.modern.data.MediaItem

private enum class Filter(val label: String) {
    ALL("All"), UNWATCHED("Unwatched"), STARTED("In progress"), MY_LIST("My list"), UHD("4K"), DV("Dolby Vision"), HDR("HDR")
}

private enum class Sort(val label: String) { NAME("A–Z"), NEWEST("Newest first"), OLDEST("Oldest first") }

@Composable
fun LibraryScreen(
    title: String,
    items: List<MediaItem>?,
    isMovies: Boolean,
    gridState: LazyGridState,
    bottomPad: Dp,
    onOpen: (MediaItem) -> Unit,
) {
    var filter by rememberSaveable(title) { mutableStateOf(Filter.ALL) }
    var sort by rememberSaveable(title) { mutableStateOf(Sort.NAME) }
    val filters = if (isMovies) Filter.entries.toList() else listOf(Filter.ALL, Filter.UNWATCHED, Filter.MY_LIST)

    val shown = remember(items, filter, sort) {
        val base = items.orEmpty().filter { item ->
            when (filter) {
                Filter.ALL -> true
                Filter.UNWATCHED -> !item.watched
                Filter.STARTED -> item.progress != null
                Filter.MY_LIST -> item.pinned
                Filter.UHD -> "4K" in item.badges
                Filter.DV -> "DOLBY VISION" in item.badges
                Filter.HDR -> item.badges.any { it.startsWith("HDR") || it == "HLG" || it == "DOLBY VISION" }
            }
        }
        when (sort) {
            Sort.NAME -> base
            Sort.NEWEST -> base.sortedByDescending { it.year }
            Sort.OLDEST -> base.sortedBy { if (it.year > 0) it.year else Int.MAX_VALUE }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(Modifier.statusBarsPadding().padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = NovaType.Title, color = Nova.Text)
                            Text(
                                when {
                                    items == null -> "Loading…"
                                    shown.size == 1 -> "1 title"
                                    else -> "${shown.size} titles"
                                },
                                style = NovaType.Meta,
                                color = Nova.Muted,
                            )
                        }
                        RoundIconButton(NovaIcons.Sort, "Sort: ${sort.label}", onClick = { sort = Sort.entries[(sort.ordinal + 1) % Sort.entries.size] })
                    }
                    if (sort != Sort.NAME) {
                        Text("Sorted: ${sort.label}", style = NovaType.Small, color = Nova.Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                ) {
                    items(filters, key = { it.name }) { f ->
                        Pill(f.label, selected = f == filter, onClick = { filter = f })
                    }
                }
            }
            if (items != null && shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState(
                        title = if (items.isEmpty()) "No ${title.lowercase()} yet" else "Nothing matches",
                        body = if (items.isEmpty()) "Nova adds them here once it finds and identifies your files." else "Try another filter.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(shown, key = { it.key }) { item ->
                PosterCard(item, width = Dp.Unspecified, onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (items == null) LoadingIndicator()
    }
}
