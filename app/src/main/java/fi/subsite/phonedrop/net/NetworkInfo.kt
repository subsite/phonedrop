package fi.subsite.phonedrop.net

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfo {
    /**
     * Best guess at the LAN IPv4 address other devices can reach. Enumerates interfaces rather than
     * asking ConnectivityManager so it also works when the phone itself is the Wi-Fi hotspot.
     */
    fun lanIpv4(): String? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { iface ->
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .map { iface.name to it.hostAddress }
            }
        // Prefer Wi-Fi / hotspot / ethernet interfaces over mobile data or VPN tunnels.
        val preferred = listOf("wlan", "ap", "swlan", "eth")
        return candidates.firstOrNull { (name, _) -> preferred.any { name.startsWith(it) } }?.second
            ?: candidates.firstOrNull()?.second
    }
}
