package fi.subsite.phonedrop.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fi.subsite.phonedrop.R
import fi.subsite.phonedrop.server.AuthorizedKeys
import fi.subsite.phonedrop.server.SftpServerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AuthorizedKeys(SftpServerService.authorizedKeysFile(context)) }
    var keys by remember { mutableStateOf(store.load()) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }

    // System picker is fine for single files (only folder picking is restricted). .pub has no
    // registered MIME type, so allow everything and let parsing reject non-keys.
    val pickKeyFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileError = runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { readSmallText(it) }
            store.add(text)
        }.fold(
            onSuccess = { added -> if (added == 0) context.getString(R.string.key_already_added) else null },
            onFailure = { it.message ?: context.getString(R.string.key_file_unreadable) },
        )
        keys = store.load()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ssh_keys)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.keys_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; error = null },
                        placeholder = { Text("ssh-ed25519 AAAA… user@host") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                runCatching { store.add(input) }
                                    .onSuccess { input = ""; keys = store.load() }
                                    .onFailure { error = it.message }
                            },
                            enabled = input.isNotBlank(),
                        ) { Text(stringResource(R.string.add_key)) }
                        OutlinedButton(onClick = { fileError = null; pickKeyFile.launch(arrayOf("*/*")) }) {
                            Text(stringResource(R.string.add_key_from_file))
                        }
                    }
                    fileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (keys.isEmpty()) {
                item { Text(stringResource(R.string.no_keys), style = MaterialTheme.typography.bodySmall) }
            }
            items(keys, key = { it.line }) { key ->
                Card {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                key.comment.ifEmpty { key.type },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(key.type, style = MaterialTheme.typography.bodySmall)
                            Text(key.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { store.remove(key); keys = store.load() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

/** Key files are tiny; refuse anything big so picking a video by mistake can't exhaust memory. */
private const val MAX_KEY_FILE_BYTES = 64 * 1024

private fun readSmallText(input: java.io.InputStream): String {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
        val n = input.read(chunk)
        if (n < 0) break
        buffer.write(chunk, 0, n)
        require(buffer.size() <= MAX_KEY_FILE_BYTES) { "That file is too large to be a public key." }
    }
    return buffer.toString(Charsets.UTF_8.name())
}
