package fi.subsite.phonedrop.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fi.subsite.phonedrop.MainActivity
import fi.subsite.phonedrop.R
import fi.subsite.phonedrop.data.Settings
import fi.subsite.phonedrop.net.MdnsAdvertiser
import fi.subsite.phonedrop.net.NetworkInfo
import org.apache.sshd.server.SshServer
import java.io.File
import java.net.BindException
import java.util.concurrent.Executors

class SftpServerService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private var server: SshServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var mdns: MdnsAdvertiser
    @Volatile private var destroyed = false
    @Volatile private var password: String? = null
    private var address: String = ""

    override fun onCreate() {
        super.onCreate()
        mdns = MdnsAdvertiser(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_PASSWORD_LOGIN -> {
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
                executor.execute { setPasswordLogin(enabled) }
            }
            else -> {
                startInForeground(buildNotification(getString(R.string.notif_starting), null))
                if (server == null) {
                    ServerStatus.set(ServerState.Starting)
                    executor.execute { startServer() }
                }
            }
        }
        // Not sticky: a restarted service would need a new password the user can't see.
        return START_NOT_STICKY
    }

    private fun startServer() {
        val root = Settings(this).rootPath
        val rootDir = File(root)
        if (!rootDir.isDirectory) {
            fail(getString(R.string.error_root_missing, root))
            return
        }
        prepareMina(this)
        password = if (Settings(this).passwordLogin) Passwords.generate() else null
        val keys = AuthorizedKeys(authorizedKeysFile(this))
        val ssh = SshServerFactory.create(
            port = SshServerFactory.DEFAULT_PORT,
            root = rootDir.toPath(),
            hostKeyFile = File(filesDir, "hostkey.pem").toPath(),
            password = { password },
            authorizedKeys = { keys.publicKeys() },
            addAuthorizedKeys = keys::add,
        )
        try {
            ssh.start()
        } catch (e: BindException) {
            fail(getString(R.string.error_port_in_use, SshServerFactory.DEFAULT_PORT))
            return
        } catch (e: Exception) {
            fail(e.message ?: e.javaClass.simpleName)
            return
        }
        server = ssh
        acquireLocks()
        mdns.register(ssh.port)
        val ip = NetworkInfo.lanIpv4()
        ServerStatus.set(ServerState.Running(ip, ssh.port, password, root))
        if (destroyed) return // Stop requested mid-startup; the queued shutdown task cleans up.
        address = ip?.let { "sftp://$USERNAME@$it:${ssh.port}" } ?: getString(R.string.no_lan_ip)
        updateRunningNotification()
    }

    /** Switches password login on (with a fresh password) or off while the server keeps running. */
    private fun setPasswordLogin(enabled: Boolean) {
        val running = ServerStatus.state.value as? ServerState.Running ?: return
        if (server == null || destroyed) return
        password = if (enabled) Passwords.generate() else null
        ServerStatus.set(running.copy(password = password))
        updateRunningNotification()
    }

    private fun updateRunningNotification() {
        val text = getString(if (password != null) R.string.notif_open_for_password else R.string.notif_keys_only)
        startInForeground(buildNotification(address, text))
    }

    private fun fail(message: String) {
        ServerStatus.set(ServerState.Error(message))
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        // Runs after any in-flight startServer() on the same single-thread executor.
        executor.execute {
            mdns.unregister()
            runCatching { server?.stop(true) }
            server = null
            password = null
            releaseLocks()
            if (ServerStatus.state.value !is ServerState.Error) ServerStatus.set(ServerState.Stopped)
        }
        executor.shutdown()
        super.onDestroy()
    }

    private fun acquireLocks() {
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PhoneDrop:wifi").apply { acquire() }
        val power = getSystemService(PowerManager::class.java)
        // Held for as long as the server runs; released in onDestroy.
        @Suppress("WakelockTimeout")
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneDrop:server").apply { acquire() }
    }

    private fun releaseLocks() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        wakeLock = null
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SftpServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        // The password is shown only inside the app, never in the notification.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .build()
    }

    companion object {
        const val USERNAME = "drop"
        private const val CHANNEL_ID = "server"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "fi.subsite.phonedrop.STOP"
        private const val ACTION_SET_PASSWORD_LOGIN = "fi.subsite.phonedrop.SET_PASSWORD_LOGIN"
        private const val EXTRA_ENABLED = "enabled"

        fun authorizedKeysFile(context: Context) = File(context.filesDir, "authorized_keys")

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SftpServerService::class.java))
        }

        /** Applies a password-login change to the running server. No-op when it isn't running. */
        fun setPasswordLogin(context: Context, enabled: Boolean) {
            if (ServerStatus.state.value !is ServerState.Running) return
            context.startService(
                Intent(context, SftpServerService::class.java)
                    .setAction(ACTION_SET_PASSWORD_LOGIN)
                    .putExtra(EXTRA_ENABLED, enabled)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SftpServerService::class.java))
        }

        private fun prepareMina(context: Context) {
            // MINA looks for ~/.ssh and friends; give it a writable home.
            System.setProperty("user.home", context.filesDir.absolutePath)
            // Android ships a stripped-down "BC" provider that MINA would otherwise try to use.
            System.setProperty("org.apache.sshd.security.provider.BC.enabled", "false")
        }
    }
}
