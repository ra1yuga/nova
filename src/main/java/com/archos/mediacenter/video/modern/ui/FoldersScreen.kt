// Nova Modern: browse phone storage, SD/USB and the network shares saved in Nova
package com.archos.mediacenter.video.modern.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archos.filecorelibrary.ExtStorageManager
import com.archos.filecorelibrary.ListingEngine
import com.archos.filecorelibrary.ListingEngineFactory
import com.archos.filecorelibrary.MetaFile2
import com.archos.mediacenter.utils.ShortcutDbAdapter
import com.archos.mediacenter.video.browser.ShortcutDb
import com.archos.mediacenter.video.modern.data.FolderEntry
import com.archos.mediacenter.video.modern.data.Source
import com.archos.mediacenter.video.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val TAG = "NovaModernFolders"

private suspend fun loadSources(context: Context): List<Source> = withContext(Dispatchers.IO) {
    val out = ArrayList<Source>()
    val seen = HashSet<String>()
    fun add(s: Source) {
        if (seen.add(s.uri.trimEnd('/'))) out.add(s)
    }
    try {
        val internal = Environment.getExternalStorageDirectory()
        add(Source("This phone", "Internal storage", Uri.fromFile(internal).toString(), false))
    } catch (e: Exception) {
        Log.w(TAG, "internal storage", e)
    }
    try {
        val esm = ExtStorageManager.getExtStorageManager()
        for (p in esm.extSdcards.orEmpty()) add(Source(esm.getVolumeDesc(p) ?: "SD card", p, Uri.fromFile(File(p)).toString(), false))
        for (p in esm.extUsbStorages.orEmpty()) add(Source(esm.getVolumeDesc(p) ?: "USB storage", p, Uri.fromFile(File(p)).toString(), false))
        for (p in esm.extOtherStorages.orEmpty()) add(Source(esm.getVolumeDesc(p) ?: "Storage", p, Uri.fromFile(File(p)).toString(), false))
    } catch (e: Exception) {
        Log.w(TAG, "external storages", e)
    }
    // shares the library indexes (added via "Add to library" in Nova's network browser)
    try {
        ShortcutDbAdapter.VIDEO.queryAllShortcuts(context)?.use { c ->
            val pathCol = c.getColumnIndex(ShortcutDbAdapter.KEY_PATH)
            val nameCol = c.getColumnIndex(ShortcutDbAdapter.KEY_NAME)
            if (pathCol >= 0 && c.moveToFirst()) {
                do {
                    val path = c.getString(pathCol) ?: continue
                    val name = if (nameCol >= 0) c.getString(nameCol) else null
                    val network = !path.startsWith("/") && !path.startsWith("file:")
                    add(Source(name?.takeIf { it.isNotBlank() } ?: Uri.parse(path).lastPathSegment ?: path, prettyUri(path), path, network))
                } while (c.moveToNext())
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "indexed shortcuts", e)
    }
    // server shortcuts saved in Nova's network browser
    try {
        ShortcutDb.STATIC.getCursorAllShortcuts(context)?.use { c ->
            val uriCol = c.getColumnIndex(ShortcutDb.KEY_URI)
            val nameCol = c.getColumnIndex(ShortcutDb.KEY_SHORTCUT_NAME)
            if (uriCol >= 0 && c.moveToFirst()) {
                do {
                    val path = c.getString(uriCol) ?: continue
                    val name = if (nameCol >= 0) c.getString(nameCol) else null
                    add(Source(name?.takeIf { it.isNotBlank() } ?: path, prettyUri(path), path, true))
                } while (c.moveToNext())
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "network shortcuts", e)
    }
    out
}

private fun prettyUri(uri: String): String = try {
    val u = Uri.parse(uri)
    if (u.scheme == null || u.scheme == "file") (u.path ?: uri) else "${u.scheme}://${u.host ?: ""}${u.path ?: ""}"
} catch (_: Exception) {
    uri
}

@Composable
fun FoldersScreen(bottomPad: Dp, onOpenFolder: (String, String) -> Unit, actions: NovaActions) {
    val context = LocalContext.current
    val sources by produceState<List<Source>?>(initialValue = null) { value = loadSources(context) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Column(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 10.dp)) {
                    Text("Folders", style = NovaType.Title, color = Nova.Text)
                    Text("Browse files directly, including ones Nova hasn't identified.", style = NovaType.Meta, color = Nova.Muted)
                }
            }
            val list = sources
            if (list != null) {
                val local = list.filter { !it.isNetwork }
                val network = list.filter { it.isNetwork }
                item(key = "local-title") { GroupLabel("On this phone") }
                items(local, key = { "l:" + it.uri }) { s ->
                    SourceRow(NovaIcons.Phone, s.title, s.subtitle) { onOpenFolder(s.title, s.uri) }
                }
                item(key = "net-title") { GroupLabel("Network") }
                items(network, key = { "n:" + it.uri }) { s ->
                    SourceRow(NovaIcons.Network, s.title, s.subtitle) { onOpenFolder(s.title, s.uri) }
                }
                item(key = "net-add") {
                    SourceRow(
                        NovaIcons.Plus,
                        if (network.isEmpty()) "Add a network share" else "Add or manage network shares",
                        "Opens Nova's network browser (SMB, UPnP/DLNA, FTP, SFTP, WebDAV)",
                    ) { actions.openNetworkSetup() }
                }
            }
        }
        if (sources == null) LoadingIndicator()
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(Locale.ROOT), style = NovaType.Label, color = Nova.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Nova.Surface)
            .combinedClickableCompat(onClick = onClick, role = Role.Button, label = title)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Nova.Text, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NovaType.BodyStrong.copy(fontSize = 15.sp), color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = NovaType.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Icon(NovaIcons.Chevron, contentDescription = null, tint = Nova.Muted, modifier = Modifier.size(18.dp))
    }
}

private sealed interface Listing {
    data object Loading : Listing
    data class Ready(val entries: List<FolderEntry>, val done: Boolean) : Listing
    data class Failed(val message: String, val needsLogin: Boolean) : Listing
}

@Composable
fun FolderScreen(
    title: String,
    uri: String,
    actions: NovaActions,
    onOpenFolder: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var listing by remember(uri) { mutableStateOf<Listing>(Listing.Loading) }

    DisposableEffect(uri) {
        var engine: ListingEngine? = null
        try {
            val e = ListingEngineFactory.getListingEngineForUrl(context, Uri.parse(uri))
            e.setFilter(VideoUtils.getVideoFilterMimeTypes(), null)
            e.setListener(object : ListingEngine.Listener {
                override fun onListingStart() {}

                override fun onListingUpdate(files: MutableList<out MetaFile2>?) {
                    val entries = files.orEmpty()
                        .map { f ->
                            FolderEntry(
                                name = f.name ?: "",
                                isDirectory = f.isDirectory,
                                uri = f.uri.toString(),
                                sizeLabel = if (!f.isDirectory && f.length() > 0) Formatter.formatShortFileSize(context, f.length()) else null,
                                file = f,
                            )
                        }
                        .sortedWith(compareBy<FolderEntry>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                    listing = Listing.Ready(entries, done = false)
                }

                override fun onListingEnd() {
                    val cur = listing
                    listing = if (cur is Listing.Ready) cur.copy(done = true) else Listing.Ready(emptyList(), true)
                }

                override fun onListingTimeOut() {
                    listing = Listing.Failed("The server took too long to answer. Check that it's on and reachable.", false)
                }

                override fun onCredentialRequired(e: Exception?) {
                    listing = Listing.Failed(
                        "This share needs a username and password. Open it once in Nova's network browser to save them, then come back.",
                        true,
                    )
                }

                override fun onListingFatalError(e: Exception?, errorCode: ListingEngine.ErrorEnum?) {
                    val msg = try {
                        if (errorCode != null) context.getString(ListingEngine.getErrorStringResId(errorCode)) else null
                    } catch (_: Exception) {
                        null
                    }
                    listing = Listing.Failed(msg ?: "Couldn't open this folder.", false)
                }

                override fun onListingFileInfoUpdate(uri: Uri?, metaFile2: MetaFile2?) {}
            })
            e.start()
            engine = e
        } catch (ex: Exception) {
            Log.w(TAG, "listing failed for $uri", ex)
            listing = Listing.Failed("Couldn't open this folder.", false)
        }
        onDispose {
            try { engine?.abort() } catch (_: Exception) { }
        }
    }

    Box(Modifier.fillMaxSize().background(Nova.Black)) {
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            contentPadding = PaddingValues(bottom = navBottom + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "head") {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIconButton(NovaIcons.Back, "Back", onClick = onBack)
                    Column(Modifier.padding(start = 4.dp).weight(1f)) {
                        Text(title, style = NovaType.Section, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(prettyUri(uri), style = NovaType.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            when (val l = listing) {
                is Listing.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp)) { LoadingIndicator() }
                }
                is Listing.Failed -> item(key = "failed") {
                    EmptyState(title = "Can't open this folder", body = l.message, modifier = Modifier.fillMaxWidth()) {
                        if (l.needsLogin) SecondaryButton("Open network browser", NovaIcons.Network, onClick = { actions.openNetworkSetup() })
                    }
                }
                is Listing.Ready -> {
                    if (l.entries.isEmpty() && l.done) {
                        item(key = "empty") {
                            EmptyState(title = "No videos here", body = "This folder has no video files or subfolders.", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    items(l.entries, key = { it.uri }) { entry ->
                        EntryRow(entry) {
                            if (entry.isDirectory) onOpenFolder(entry.name, entry.uri)
                            else entry.file?.let { actions.playFile(it) }
                        }
                    }
                    if (!l.done) item(key = "more") { Box(Modifier.fillMaxWidth().height(64.dp)) { LoadingIndicator() } }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: FolderEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickableCompat(onClick = onClick, role = Role.Button, label = entry.name)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Nova.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (entry.isDirectory) NovaIcons.Folder else NovaIcons.Play, contentDescription = null, tint = if (entry.isDirectory) Nova.Text else Nova.Accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.name, style = NovaType.BodyStrong, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            entry.sizeLabel?.let { Text(it, style = NovaType.Small, color = Nova.Muted) }
        }
        if (entry.isDirectory) Icon(NovaIcons.Chevron, contentDescription = null, tint = Nova.Muted, modifier = Modifier.size(18.dp).width(18.dp))
    }
}
