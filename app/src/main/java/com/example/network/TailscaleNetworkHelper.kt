package com.example.network

import com.example.model.NetworkEndpoints
import com.example.model.NetworkInterfaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

object TailscaleNetworkHelper {

    suspend fun detectEndpoints(): NetworkEndpoints = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<NetworkInterfaceInfo>()
        var detectedTailscaleIp: String? = null
        var detectedWifiIp: String? = null

        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val ifName = networkInterface.name.orEmpty()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address !is Inet4Address || address.isLoopbackAddress) continue

                    val ip = address.hostAddress ?: continue
                    val isTailscale = isTailscaleCgnatIp(ip) ||
                        ifName.startsWith("tailscale", ignoreCase = true)

                    discovered += NetworkInterfaceInfo(
                        name = ifName,
                        ipAddress = ip,
                        isTailscale = isTailscale,
                    )

                    if (isTailscale) {
                        if (detectedTailscaleIp == null || ifName.startsWith("tun", ignoreCase = true)) {
                            detectedTailscaleIp = ip
                        }
                    } else if (
                        ifName.startsWith("wlan", ignoreCase = true) ||
                        ifName.startsWith("eth", ignoreCase = true) ||
                        isPrivateLanIp(ip)
                    ) {
                        if (detectedWifiIp == null) {
                            detectedWifiIp = ip
                        }
                    }
                }
            }
        }

        NetworkEndpoints(
            tailscaleIp = detectedTailscaleIp,
            wifiIp = detectedWifiIp,
            interfaces = discovered,
        )
    }

    /**
     * Tailscale assigns IPv4 addresses in the CGNAT range 100.64.0.0/10
     * (100.64.0.0 through 100.127.255.255).
     */
    fun isTailscaleCgnatIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    private fun isPrivateLanIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }
}
