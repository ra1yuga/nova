// Nova Modern: Home — hero banner plus shelves (continue watching, my list, recently added...)
package com.archos.mediacenter.video.modern.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archos.mediacenter.video.browser.adapters.`object`.Video
import com.archos.mediacenter.video.modern.data.Hero
import com.archos.mediacenter.video.modern.data.HomeData
import com.archos.mediacenter.video.modern.data.MediaItem
import com.archos.mediacenter.video.modern.data.RowStyle

@Composable
fun HomeScreen(
    data: HomeData?,
    listState: LazyListState,
    bottomPad: Dp,
    onOpen: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    onPlay: (Video) -> Unit,
    onTab: (Tab) -> Unit,
    actions: NovaActions,
) {
    val screenH = LocalConfiguration.current.screenHeightDp
    val heroHeight = (screenH * 0.62f).coerceIn(420f, 620f).dp
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240 } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPad + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                val hero = data?.hero
                if (hero != null) {
                    HeroBanner(hero, heroHeight, onPlay = { hero.playTarget?.let(onPlay) ?: onOpen(hero.item) }, onDetails = { onOpen(hero.item) })
                } else {
                    Box(Modifier.fillMaxWidth().height(if (data == null) heroHeight else 140.dp)) {
                        if (data == null) LoadingIndicator()
                    }
                }
            }
            if (data != null && data.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing here yet",
                        body = "Nova is looking for videos on your phone and fetching their posters and details. " +
                            "To add a NAS or computer, open Folders and add a network share.",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SecondaryButton("Open Folders", NovaIcons.Folder, onClick = { onTab(Tab.FOLDERS) })
                    }
                }
            }
            if (data != null) {
                items(data.rows, key = { it.title }) { row ->
                    MediaRow(
                        title = row.title,
                        items = row.items,
                        landscape = row.style == RowStyle.LANDSCAPE,
                        onClick = if (row.style == RowStyle.LANDSCAPE) onPlayItem else onOpen,
                        onLongClick = onOpen,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }

        TopBar(scrolled = scrolled, onTab = onTab, actions = actions)
    }
}

@Composable
private fun TopBar(scrolled: Boolean, onTab: (Tab) -> Unit, actions: NovaActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (scrolled) Brush.verticalGradient(listOf(Color(0xF2000000), Color(0xE6000000)))
                else Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
            )
            .statusBarsPadding()
            .padding(bottom = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append("nova")
                    withStyle(SpanStyle(color = Nova.Accent)) { append(".") }
                },
                style = NovaType.Title.copy(fontSize = 24.sp, letterSpacing = (-0.8).sp),
                color = Nova.Text,
                modifier = Modifier.weight(1f),
            )
            RoundIconButton(NovaIcons.Server, "Folders and network shares", onClick = { onTab(Tab.FOLDERS) })
            Box {
                RoundIconButton(NovaIcons.Settings, "Menu", onClick = { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(Nova.Surface2),
                ) {
                    DropdownMenuItem(text = { Text("Settings", style = NovaType.BodyStrong, color = Nova.Text) }, onClick = { menuOpen = false; actions.openSettings() })
                    DropdownMenuItem(text = { Text("Classic Nova screens", style = NovaType.BodyStrong, color = Nova.Text) }, onClick = { menuOpen = false; actions.openClassic() })
                    if (actions.hasProblemReport()) {
                        DropdownMenuItem(text = { Text("Share problem report", style = NovaType.BodyStrong, color = Nova.Text) }, onClick = { menuOpen = false; actions.shareProblemReport() })
                    }
                }
            }
        }
        Row(
            Modifier.padding(start = 16.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip("TV Shows") { onTab(Tab.SHOWS) }
            CategoryChip("Movies") { onTab(Tab.MOVIES) }
            CategoryChip("Folders") { onTab(Tab.FOLDERS) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .border(1.dp, Color(0x66F4F1EA), shape)
            .combinedClickableCompat(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NovaType.BodyStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Nova.Text)
    }
}

@Composable
private fun HeroBanner(hero: Hero, height: Dp, onPlay: () -> Unit, onDetails: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Artwork(hero.art, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x99000000),
                        0.22f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.78f to Color(0xD1000000),
                        1f to Color.Black,
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BadgeRow(hero.badges)
            Text(
                hero.title,
                style = NovaType.Hero,
                color = Nova.Text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hero.meta.isNotBlank()) {
                Text(hero.meta, style = NovaType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = Nova.TextSoft, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(hero.playLabel, NovaIcons.Play, onClick = onPlay, modifier = Modifier.weight(1f))
                SecondaryButton("Details", NovaIcons.Info, onClick = onDetails, modifier = Modifier.weight(1f))
            }
        }
    }
}
