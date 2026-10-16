package net.mrowser.stream

import java.net.URI

/**
 * Pure: the request headers the native player sends for a sniffed stream.
 *
 * Embedded players run in a cross-origin iframe, and their CDNs gate on the *iframe's* origin
 * (#51: `*.vkvideo.cloud` 403s every request whose `Origin` is not the embed's), so the top page
 * URL is the wrong Referer to send. The WebView already told us what it sent for the stream
 * request itself; replay its `Referer` and `Origin`, and fall back to the page only when it
 * reported none. Nothing else is replayed: custom headers the page set are typically per-request
 * signatures that would be stale on the player's next request, and Cookie is resolved per host
 * by the player itself so a session cookie never rides along to another host.
 */
object PlaybackHeaders {

    fun build(
        streamUrl: String,
        pageUrl: String,
        userAgent: String,
        sniffed: Map<String, String>
    ): Map<String, String> {
        val sniffedReferer = sniffed.header("Referer")
        // A browser sends Origin on the cross-origin fetch/XHR that hls.js and dash.js use. When
        // the WebView reported a referer but no Origin, derive it from that referer the same way.
        // With nothing sniffed (`requestHeaders` is nullable) the headers stay as they always
        // were: the page as Referer and no Origin.
        val origin = sniffed.header("Origin")
            ?: sniffedReferer?.let(::originOf)?.takeIf { it != originOf(streamUrl) }
        val referer = sniffedReferer ?: pageUrl.ifEmpty { null }
        return buildMap {
            put("User-Agent", userAgent)
            referer?.let { put("Referer", it) }
            origin?.let { put("Origin", it) }
        }
    }

    /** `scheme://host[:port]` of an http(s) URL, the default port dropped; null otherwise. */
    fun originOf(url: String): String? {
        val uri = try { URI(url) } catch (e: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    private fun Map<String, String>.header(name: String): String? =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.ifEmpty { null }
}
