package net.mrowser.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

/**
 * Calls [onAvailable] on the main thread whenever an internet-capable network comes up (Wi-Fi
 * reconnected or switched). Register in onStart, unregister in onStop.
 */
class NetworkWatcher(context: Context, private val onAvailable: () -> Unit) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val main = Handler(Looper.getMainLooper())
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post(onAvailable)
        }
    }

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
            registered = true
        } catch (_: RuntimeException) {
            // Some boxes throw (SecurityException / too many callbacks): recovery just falls back to timers.
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        try { cm.unregisterNetworkCallback(callback) } catch (_: RuntimeException) {}
        main.removeCallbacksAndMessages(null)
    }
}
