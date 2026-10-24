package com.glassplay.app.ui.addons

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.glassplay.app.Graph
import com.glassplay.app.data.InstalledAddon
import com.glassplay.app.data.typeLabel
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassDialog
import com.glassplay.app.ui.components.GlassTextField
import com.glassplay.app.ui.components.LargeTitle
import com.glassplay.app.ui.components.SectionHeader
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.components.noRippleClick
import com.glassplay.app.ui.theme.Danger
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AddonsScreen() {
    val addons by Graph.addons.installed.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var url by rememberSaveable { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<InstalledAddon?>(null) }
    var removeTarget by remember { mutableStateOf<InstalledAddon?>(null) }

    fun fetch() {
        if (url.isBlank() || installing) return
        focus.clearFocus()
        installing = true
        error = null
        scope.launch {
            try {
                pending = Graph.client.fetchManifest(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't reach that link"
            } finally {
                installing = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item(key = "title") { LargeTitle("Add-ons", caption = "Catalogs and streams") }

        item(key = "install") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .glass(RoundedCornerShape(26.dp))
                    .padding(18.dp)
            ) {
                Text("Install from a link", style = GlassType.headline, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste an add-on's manifest link. Add-ons built for Stremio work here too.",
                    style = GlassType.footnote,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                GlassTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    placeholder = "https://…/manifest.json",
                    leading = Icons.Rounded.Link,
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Uri,
                    onSubmit = { fetch() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (installing) {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { Spinner() }
                } else {
                    GlassButton(
                        "Install",
                        onClick = { fetch() },
                        primary = true,
                        icon = Icons.Rounded.Add,
                        enabled = url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = GlassType.footnote, color = Danger)
                }
            }
        }

        item(key = "installed-title") { SectionHeader("Installed", subtitle = "${addons.size}") }

        itemsIndexed(addons, key = { _, a -> a.transportUrl }) { index, addon ->
            AddonCard(
                addon = addon,
                canMoveUp = index > 0,
                onMoveUp = { Graph.addons.moveUp(addon.transportUrl) },
                onRemove = { removeTarget = addon },
            )
        }

        item(key = "footer") {
            Text(
                "Add-ons are run by third parties. Details come from the first add-on in this list that has them, so move your preferred one up.",
                style = GlassType.caption,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }

    pending?.let { a ->
        GlassDialog(onDismiss = { pending = null }) {
            AddonHeader(a)
            a.manifest.description?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = GlassType.subhead, color = TextSecondary, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Text(provides(a), style = GlassType.footnote, color = TextTertiary)
            Spacer(Modifier.height(20.dp))
            val alreadyInstalled = addons.any { it.manifest.id == a.manifest.id }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Cancel", onClick = { pending = null }, modifier = Modifier.weight(1f))
                GlassButton(
                    if (alreadyInstalled) "Update" else "Install",
                    onClick = {
                        Graph.addons.install(a)
                        pending = null
                        url = ""
                    },
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    removeTarget?.let { a ->
        GlassDialog(onDismiss = { removeTarget = null }) {
            Text("Remove ${a.manifest.name}?", style = GlassType.title3, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "Its catalogs and streams will disappear from GlassPlay. You can install it again any time.",
                style = GlassType.subhead,
                color = TextSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Cancel", onClick = { removeTarget = null }, modifier = Modifier.weight(1f))
                GlassButton(
                    "Remove",
                    onClick = {
                        Graph.addons.remove(a.transportUrl)
                        removeTarget = null
                    },
                    contentColor = Danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun provides(a: InstalledAddon): String {
    val parts = mutableListOf<String>()
    val cats = a.manifest.catalogs.size
    if (cats > 0) parts.add("$cats catalog${if (cats > 1) "s" else ""}")
    if (a.hasResource("meta")) parts.add("details")
    if (a.hasResource("stream")) parts.add("streams")
    if (a.hasResource("subtitles")) parts.add("subtitles")
    val types = a.manifest.types.joinToString(", ") { typeLabel(it) }
    return listOfNotNull(
        parts.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Provides $it" },
        types.takeIf { it.isNotEmpty() }?.let { "for $it" },
    ).joinToString(" ")
}

@Composable
private fun AddonHeader(a: InstalledAddon) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AddonLogo(a, 52)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.manifest.name, style = GlassType.title3, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (a.manifest.version.isNotEmpty()) {
                Text("Version ${a.manifest.version}", style = GlassType.footnote, color = TextTertiary)
            }
        }
    }
}

@Composable
private fun AddonLogo(a: InstalledAddon, size: Int) {
    val shape = RoundedCornerShape((size * 0.28f).dp)
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            a.manifest.name.take(1).uppercase(),
            style = GlassType.title2,
            color = Color.White,
        )
        if (a.manifest.logo != null) {
            AsyncImage(
                model = a.manifest.logo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AddonCard(addon: InstalledAddon, canMoveUp: Boolean, onMoveUp: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .glass(RoundedCornerShape(22.dp), 0.85f)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddonLogo(addon, 46)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    addon.manifest.name,
                    style = GlassType.headline,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (addon.manifest.version.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text("v${addon.manifest.version}", style = GlassType.caption, color = TextTertiary)
                }
            }
            addon.manifest.description?.let {
                Text(it, style = GlassType.footnote, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(provides(addon), style = GlassType.caption, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (canMoveUp) {
                SmallRoundButton(onMoveUp) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
            SmallRoundButton(onRemove) {
                Icon(Icons.Rounded.Delete, "Remove", tint = Danger, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SmallRoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .glass(CircleShape, 0.8f)
            .noRippleClick(onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
