// Nova Modern: building blocks shared by every screen
package com.archos.mediacenter.video.modern.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.archos.mediacenter.video.modern.data.MediaItem

object NovaImages {
    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
            .also { loader = it }
    }
}

/** Poster/backdrop art with a quiet fallback that shows the title when there is no image. */
@Composable
fun Artwork(
    model: Any?,
    modifier: Modifier = Modifier,
    fallbackTitle: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    Box(modifier.background(Nova.Surface2)) {
        if (fallbackTitle != null) {
            Text(
                text = fallbackTitle,
                style = NovaType.Small,
                color = Nova.Muted,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
            )
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                imageLoader = NovaImages.get(context),
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).background(Color(0x40F4F1EA))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Nova.Accent)
        )
    }
}

@Composable
fun Badge(text: String, filled: Boolean = false) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = text,
        style = NovaType.Badge,
        color = Nova.Text,
        modifier = Modifier
            .clip(shape)
            .then(
                if (filled) Modifier.background(Color(0x1FF4F1EA))
                else Modifier.border(1.dp, Color(0x80F4F1EA), shape)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun BadgeRow(badges: List<String>, filled: Boolean = false, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        badges.forEach { Badge(it, filled) }
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nova.Accent)
            .combinedClickableCompat(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Nova.OnAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = NovaType.Button, color = Nova.OnAccent, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x29F4F1EA))
            .combinedClickableCompat(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Nova.Text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = NovaType.Button.copy(fontWeight = FontWeight.Bold), color = Nova.Text, maxLines = 1)
    }
}

@Composable
fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Nova.Text) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickableCompat(onClick = onClick, role = Role.Button)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = NovaType.Small, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, background: Color = Color.Transparent) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .combinedClickableCompat(onClick = onClick, role = Role.Button, label = description),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Nova.Text, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, solidWhite: Boolean = false) {
    val shape = RoundedCornerShape(18.dp)
    val bg = when {
        selected && solidWhite -> Nova.Text
        selected -> Nova.Accent
        else -> Nova.Surface
    }
    val fg = when {
        selected && solidWhite -> Color(0xFF0B0B0B)
        selected -> Nova.OnAccent
        else -> Nova.Text
    }
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, Color(0x47F4F1EA), shape))
            .combinedClickableCompat(onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NovaType.Meta.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = NovaType.Section, color = Nova.Text, modifier = modifier)
}

@Composable
fun PosterCard(
    item: MediaItem,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickableCompat(onClick = onClick, onLongClick = onLongClick, role = Role.Button, label = item.title)
    ) {
        Artwork(item.poster, Modifier.fillMaxSize(), fallbackTitle = item.title)
        if ("DOLBY VISION" in item.badges) {
            CornerTag("DV", Modifier.align(Alignment.TopEnd).padding(6.dp))
        } else if ("4K" in item.badges) {
            CornerTag("4K", Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        if (item.watched) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xB8000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(NovaIcons.Check, contentDescription = "Watched", tint = Nova.Accent, modifier = Modifier.size(14.dp))
            }
        }
        item.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
    }
}

@Composable
private fun CornerTag(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = NovaType.Badge.copy(fontSize = 9.sp),
        color = Nova.Text,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xB8000000))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
fun LandscapeCard(
    item: MediaItem,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickableCompat(onClick = onClick, onLongClick = onLongClick, role = Role.Button, label = item.title),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            Artwork(item.wide ?: item.poster, Modifier.fillMaxSize(), fallbackTitle = item.title)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 10.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x8C000000))
                    .border(1.5.dp, Color(0xD9F4F1EA), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(NovaIcons.Play, contentDescription = null, tint = Nova.Text, modifier = Modifier.size(13.dp))
            }
            item.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = NovaType.Meta.copy(fontWeight = FontWeight.Bold), color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let { Text(it, style = NovaType.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    landscape: Boolean,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionTitle(title, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.key }) { item ->
                if (landscape) {
                    LandscapeCard(item, 176.dp, onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
                } else {
                    PosterCard(item, 116.dp, onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
                }
            }
        }
    }
}

@Composable
fun BoxScope.LoadingIndicator() {
    CircularProgressIndicator(color = Nova.Accent, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(36.dp))
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = NovaType.Section, color = Nova.Text, textAlign = TextAlign.Center)
        Text(body, style = NovaType.Body, color = Nova.Muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            action()
        }
    }
}

/** Click + optional long-press with a ripple, as one modifier. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    role: Role? = null,
    label: String? = null,
): Modifier = this.combinedClickable(
    onClickLabel = label,
    role = role,
    onLongClick = onLongClick,
    onClick = onClick,
)
