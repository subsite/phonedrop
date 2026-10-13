package fi.subsite.phonedrop.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/** "All files access" on Android 11+, the legacy storage permission before that. */
object StorageAccess {
    const val LEGACY_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE

    val usesAllFilesAccess: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, LEGACY_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }

    @RequiresApi(Build.VERSION_CODES.R)
    fun allFilesAccessSettingsIntent(context: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
}
