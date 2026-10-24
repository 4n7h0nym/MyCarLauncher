package com.openlauncher.app.ui.widget

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.data.DefaultShortcutIcon
import com.openlauncher.app.data.ShortcutConfig
import com.openlauncher.app.model.AppInfo
import com.openlauncher.app.ui.components.toIcon

private data class ShortcutIconChoice(val icon: DefaultShortcutIcon?, val label: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppShortcutsWidget(
    shortcuts: List<ShortcutConfig>,
    apps: List<AppInfo>,
    isEditing: Boolean,
    accent: Color,
    onAssignApp: (Int) -> Unit,
    onLaunchApp: (String) -> Unit,
    onSetIcon: (Int, DefaultShortcutIcon?) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingSlot by remember { mutableIntStateOf(-1) }
    val iconChoices = remember {
        listOf(ShortcutIconChoice(null, "App")) +
            DefaultShortcutIcon.entries.map { ShortcutIconChoice(it, it.name.replace('_', ' ')) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(2.dp)
    ) {
        items(shortcuts.take(6).withIndex().toList(), key = { it.index }) { indexed ->
            val slot = indexed.index
            val shortcut = indexed.value
            val installed = apps.firstOrNull { it.packageName == shortcut.packageName }
            val selectedIcon = shortcut.customIconOverride?.takeIf { it != DefaultShortcutIcon.NONE }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .combinedClickable(
                        onClick = {
                            if (isEditing && shortcut.packageName.isNotBlank()) editingSlot = slot
                            else if (shortcut.packageName.isNotBlank()) onLaunchApp(shortcut.packageName)
                            else onAssignApp(slot)
                        },
                        onLongClick = { editingSlot = slot }
                    )
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    selectedIcon != null -> Icon(selectedIcon.toIcon(), contentDescription = shortcut.label, tint = accent, modifier = Modifier.size(28.dp))
                    installed != null -> {
                        val bitmap = remember(installed.icon) { installed.icon.toBitmap(64, 64).asImageBitmap() }
                        Icon(BitmapPainter(bitmap), contentDescription = shortcut.label, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                    }
                    shortcut.isDefault -> Icon(shortcut.defaultIcon.toIcon(), contentDescription = shortcut.label, tint = accent, modifier = Modifier.size(28.dp))
                    else -> Icon(Icons.Default.Apps, contentDescription = "Choisir une application", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                }
                LocalizedLabel(
                    text = shortcut.label.ifBlank { "Ajouter" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 9.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (shortcuts.size < 6) {
            item(key = "add-shortcut") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .combinedClickable(
                            onClick = { onAssignApp(shortcuts.size) },
                            onLongClick = { }
                        )
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Ajouter un raccourci d’application",
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                    LocalizedLabel(
                        text = "Ajouter",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 9.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (editingSlot >= 0) {
        val slot = editingSlot
        val current = shortcuts.getOrNull(slot)?.customIconOverride
        Dialog(onDismissRequest = { editingSlot = -1 }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalizedLabel("CHOISIR UNE ICÔNE", color = accent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editingSlot = -1 }) { Icon(Icons.Default.Close, "Fermer") }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.heightIn(max = 300.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(iconChoices, key = { it.label }) { choice ->
                            val selected = current == choice.icon
                            Surface(
                                modifier = Modifier.aspectRatio(1f).combinedClickable(
                                    onClick = {
                                        onSetIcon(slot, choice.icon)
                                        editingSlot = -1
                                    },
                                    onLongClick = { }
                                ),
                                color = if (selected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (choice.icon == null) {
                                        Icon(Icons.Default.Apps, choice.label, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Icon(choice.icon.toIcon(), choice.label, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (selected) Icon(Icons.Default.Check, "Sélectionnée", tint = accent, modifier = Modifier.align(Alignment.BottomEnd).size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
