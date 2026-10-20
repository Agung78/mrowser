package net.mrowser.player

/**
 * What to do with a fatal player error. Pure, so it is unit-tested without a device.
 *
 * Network failures are retried for a time window rather than a fixed count: a Wi-Fi drop or
 * switch fails DNS instantly, so a count-based budget is spent long before the network returns.
 * An HTTP status that a retry can't fix (expired signed URL, gone) gives up at once.
 */
object PlaybackRetryPolicy {
    const val WINDOW_MS = 60_000L
    private const val MAX_DELAY_MS = 8_000L

    sealed class Action {
        data class Retry(val delayMs: Long) : Action()
        object LiveEdge : Action()
        object GiveUp : Action()
    }

    /**
     * @param attempt 1-based retry number within the current outage.
     * @param outageMs time since the first error of the current outage.
     * @param httpStatus the response code when the error was a bad HTTP status, else null.
     */
    fun decide(
        behindLiveWindow: Boolean,
        isNetwork: Boolean,
        httpStatus: Int?,
        attempt: Int,
        outageMs: Long
    ): Action = when {
        behindLiveWindow -> Action.LiveEdge
        !isNetwork -> Action.GiveUp
        httpStatus != null && httpStatus in 400..499 && httpStatus != 408 && httpStatus != 429 -> Action.GiveUp
        outageMs >= WINDOW_MS -> Action.GiveUp
        else -> Action.Retry(minOf(1000L shl minOf(attempt - 1, 3), MAX_DELAY_MS))
    }
}
