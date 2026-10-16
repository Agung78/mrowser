package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/**
 * A media URL seen on the current page. `seq` orders by sighting; `headers` are the request
 * headers the WebView reported for it (see [PlaybackHeaders]).
 */
data class StreamCandidate(
    val url: String,
    val kind: MediaKind,
    val seq: Int,
    val headers: Map<String, String> = emptyMap()
)
