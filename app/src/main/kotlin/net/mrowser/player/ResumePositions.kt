package net.mrowser.player

/**
 * Where each stream was left, so re-entering the player from the browser picks up there
 * instead of at 0 (issue #49). Keyed by the exact stream URL and held for the process
 * lifetime only — nothing is written to disk — as an LRU capped at [capacity] streams.
 *
 * Not thread-safe: the player reads and writes it on the main thread.
 */
class ResumePositions(private val capacity: Int = 20) {

    // Access order, so a read counts as a use and the eldest entry is the least recently used.
    private val positions = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > capacity
    }

    /**
     * Saves [positionMs] when it is worth resuming, else forgets the stream: a live stream or
     * an unknown duration (media3's `C.TIME_UNSET` is negative) has nothing to resume, the
     * first [MIN_POSITION_MS] are not worth it, and the last [END_MARGIN_MS] count as finished.
     */
    fun record(url: String, positionMs: Long, durationMs: Long, isLive: Boolean) {
        val resumable = !isLive && durationMs > 0 &&
            positionMs >= MIN_POSITION_MS && positionMs < durationMs - END_MARGIN_MS
        if (resumable) positions[url] = positionMs else positions.remove(url)
    }

    /** The saved position for [url], or 0 to start from the beginning. */
    fun startFor(url: String): Long = positions[url] ?: 0L

    fun clear(url: String) {
        positions.remove(url)
    }

    companion object {
        const val MIN_POSITION_MS = 10_000L
        const val END_MARGIN_MS = 30_000L
    }
}
