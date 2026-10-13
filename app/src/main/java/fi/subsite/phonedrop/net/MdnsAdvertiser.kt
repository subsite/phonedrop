package fi.subsite.phonedrop.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build

/** Announces the server as _sftp-ssh._tcp so it shows up in file managers' network browsers. */
class MdnsAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun register(port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "PhoneDrop (${Build.MODEL})"
            serviceType = "_sftp-ssh._tcp"
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
            .onSuccess { listener = l }
    }

    fun unregister() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
    }
}
