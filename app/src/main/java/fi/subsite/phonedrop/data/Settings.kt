package fi.subsite.phonedrop.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.edit
import java.io.File

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var rootPath: String
        get() = prefs.getString(KEY_ROOT, null) ?: defaultRoot
        set(value) = prefs.edit { putString(KEY_ROOT, value) }

    fun resetRoot() = prefs.edit { remove(KEY_ROOT) }

    var passwordLogin: Boolean
        get() = prefs.getBoolean(KEY_PASSWORD_LOGIN, true)
        set(value) = prefs.edit { putBoolean(KEY_PASSWORD_LOGIN, value) }

    companion object {
        private const val KEY_ROOT = "root_path"
        private const val KEY_PASSWORD_LOGIN = "password_login"

        val defaultRoot: String
            get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

        /** Top-level directories of internal storage and any SD cards / USB drives, with display names. */
        fun storageRoots(context: Context): List<Pair<String, File>> {
            val primary = Environment.getExternalStorageDirectory()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return listOf("Internal storage" to primary)
            val volumes = context.getSystemService(StorageManager::class.java).storageVolumes
                .mapNotNull { v -> v.directory?.let { v.getDescription(context) to it } }
            return volumes.ifEmpty { listOf("Internal storage" to primary) }
        }

        /** "/storage/0000-0000/Music" → "SD card › Music", using the volume names Android reports. */
        fun displayPath(context: Context, path: String): String {
            val (name, dir) = storageRoots(context)
                .filter { (_, dir) -> path == dir.path || path.startsWith(dir.path + "/") }
                .maxByOrNull { (_, dir) -> dir.path.length }
                ?: return path
            val relative = path.removePrefix(dir.path).trim('/')
            return if (relative.isEmpty()) name else "$name › ${relative.replace("/", " › ")}"
        }
    }
}
