package fi.subsite.phonedrop.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fi.subsite.phonedrop.R
import fi.subsite.phonedrop.data.Settings
import fi.subsite.phonedrop.data.StorageAccess
import fi.subsite.phonedrop.server.AuthorizedKeys
import fi.subsite.phonedrop.server.ServerState
import fi.subsite.phonedrop.server.ServerStatus
import fi.subsite.phonedrop.server.SftpServerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onOpenKeys: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val state by ServerStatus.state.collectAsStateWithLifecycle()
    var hasStorage by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    var root by remember { mutableStateOf(settings.rootPath) }
    var picking by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var passwordLogin by remember { mutableStateOf(settings.passwordLogin) }
    var keyCount by remember { mutableStateOf(0) }

    LifecycleResumeEffect(Unit) {
        hasStorage = StorageAccess.isGranted(context)
        keyCount = AuthorizedKeys(SftpServerService.authorizedKeysFile(context)).load().size
        onPauseOrDispose {}
    }

    val legacyStoragePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasStorage = it
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The server runs either way; the permission only controls whether the notification is visible.
        SftpServerService.start(context)
    }
    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SftpServerService.start(context)
        }
    }

    val running = state is ServerState.Running || state is ServerState.Starting

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    if (picking) {
        FolderPickerDialog(
            initial = root,
            onPick = { path ->
                settings.rootPath = path
                root = path
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!hasStorage) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.storage_needed), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = {
                            if (StorageAccess.usesAllFilesAccess) {
                                context.startActivity(StorageAccess.allFilesAccessSettingsIntent(context))
                            } else {
                                legacyStoragePermission.launch(StorageAccess.LEGACY_PERMISSION)
                            }
                        }) { Text(stringResource(R.string.grant_access)) }
                    }
                }
            }

            when (val s = state) {
                is ServerState.Running -> RunningCard(s)
                is ServerState.Starting -> Text(stringResource(R.string.notif_starting))
                is ServerState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                ServerState.Stopped -> Text(
                    stringResource(R.string.stopped_hint),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Button(
                onClick = { if (running) SftpServerService.stop(context) else start() },
                enabled = running || hasStorage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = if (running) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.buttonColors(),
            ) {
                Text(
                    stringResource(if (running) R.string.stop_server else R.string.start_server),
                    fontSize = 20.sp,
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.password_login), style = MaterialTheme.typography.labelLarge)
                            Text(
                                stringResource(
                                    if (passwordLogin) R.string.password_login_on else R.string.password_login_off
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = passwordLogin,
                            onCheckedChange = {
                                passwordLogin = it
                                settings.passwordLogin = it
                                SftpServerService.setPasswordLogin(context, it)
                            },
                        )
                    }
                    if (!passwordLogin && keyCount == 0) {
                        Text(
                            stringResource(R.string.no_login_possible),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ssh_keys), style = MaterialTheme.typography.labelLarge)
                        Text(
                            pluralStringResource(R.plurals.keys_configured, keyCount, keyCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = onOpenKeys) { Text(stringResource(R.string.ssh_keys)) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.shared_folder), style = MaterialTheme.typography.labelLarge)
                    Text(Settings.displayPath(context, root), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picking = true }, enabled = !running && hasStorage) {
                            Text(stringResource(R.string.change))
                        }
                        TextButton(
                            onClick = {
                                settings.resetRoot()
                                root = settings.rootPath
                            },
                            enabled = !running && root != Settings.defaultRoot,
                        ) { Text(stringResource(R.string.reset_to_download)) }
                    }
                    if (running) {
                        Text(
                            stringResource(R.string.stop_to_change),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            TextButton(
                onClick = { showAbout = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.about)) }
        }
    }
}

@Composable
private fun RunningCard(s: ServerState.Running) {
    val clipboard = LocalClipboardManager.current
    val user = SftpServerService.USERNAME
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.ip == null) {
                Text(stringResource(R.string.no_lan_ip), color = MaterialTheme.colorScheme.error)
            }
            val host = s.ip ?: "<phone-ip>"
            val url = "sftp://$user@$host:${s.port}"

            if (s.password != null) {
                Text(stringResource(R.string.password), style = MaterialTheme.typography.labelLarge)
                // Hidden again for every new password.
                var revealed by remember(s.password) { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            if (revealed) s.password else "•".repeat(s.password.length),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp,
                            letterSpacing = 4.sp,
                        )
                    }
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            painterResource(if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = stringResource(if (revealed) R.string.hide_password else R.string.show_password),
                        )
                    }
                }
            } else {
                Text(stringResource(R.string.keys_only_running), style = MaterialTheme.typography.bodyMedium)
            }

            Text(stringResource(R.string.address), style = MaterialTheme.typography.labelLarge)
            SelectionContainer {
                Text(url, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                Text(stringResource(R.string.copy_address))
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.terminal), style = MaterialTheme.typography.labelLarge)
            SelectionContainer {
                Text(
                    "sftp -P ${s.port} $user@$host",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(stringResource(R.string.any_username), style = MaterialTheme.typography.bodySmall)
        }
    }
}
