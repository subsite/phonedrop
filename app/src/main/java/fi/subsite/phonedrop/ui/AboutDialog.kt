package fi.subsite.phonedrop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.subsite.phonedrop.BuildConfig
import fi.subsite.phonedrop.R

private const val REPO_URL = "https://github.com/subsite/phonedrop"
private const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.about_copyright))
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_third_party), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { uriHandler.openUri(REPO_URL) }) {
                    Text(REPO_URL.removePrefix("https://"))
                }
                TextButton(onClick = { uriHandler.openUri(LICENSE_URL) }) {
                    Text(stringResource(R.string.about_view_license))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
