// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.pairing

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Attempts to find the device's local IPv4 address on Wi-Fi or Ethernet.
     */
    fun getLocalIpAddress(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            
            val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
            linkProperties?.linkAddresses?.forEach { linkAddress ->
                val inetAddress = linkAddress.address
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
        return null
    }
}
