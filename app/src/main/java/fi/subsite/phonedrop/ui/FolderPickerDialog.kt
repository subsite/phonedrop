package fi.subsite.phonedrop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.subsite.phonedrop.R
import fi.subsite.phonedrop.data.Settings
import java.io.File

/**
 * Browses the filesystem directly. The app has "All files access", so unlike the system folder
 * picker this can select any folder, including Download and the storage root.
 */
@Composable
fun FolderPickerDialog(initial: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val roots = remember { Settings.storageRoots(context) }
    var current by remember {
        mutableStateOf(File(initial).takeIf { it.isDirectory } ?: roots.first().second)
    }
    val subdirs = remember(current) {
        current.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
    }
    val atRoot = roots.any { it.second == current }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_folder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (roots.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        roots.forEach { (name, dir) ->
                            FilterChip(
                                selected = current.path.startsWith(dir.path),
                                onClick = { current = dir },
                                label = { Text(name) },
                            )
                        }
                    }
                }
                Text(Settings.displayPath(context, current.path), style = MaterialTheme.typography.titleSmall)
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (!atRoot) {
                        item { FolderRow("..") { current = current.parentFile ?: current } }
                    }
                    items(subdirs, key = { it.path }) { dir ->
                        FolderRow(dir.name + "/") { current = dir }
                    }
                    if (subdirs.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_subfolders),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current.path) }) { Text(stringResource(R.string.use_this_folder)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Text(
        name,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}
