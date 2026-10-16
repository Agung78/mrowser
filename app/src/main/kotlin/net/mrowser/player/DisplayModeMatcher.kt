package net.mrowser.player

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure: which display mode to ask for so the panel matches the video — its frame rate (no 3:2
 * judder from 24 fps on a 60 Hz panel) and/or its resolution (the TV's scaler instead of the
 * box's). Has no Android types; PlayerActivity maps `Display.Mode` in and applies the id out.
 */
object DisplayModeMatcher {

    /** One mode the display offers, as `Display.Mode` reports it. */
    data class Mode(val id: Int, val width: Int, val height: Int, val refreshHz: Float)

    /** One rendition of the stream. A size ≤ 0 or an fps that is null/≤ 0 is unknown. */
    data class Video(val width: Int, val height: Int, val fps: Float?)

    /**
     * Allowed drift per multiple of the frame rate, in Hz. Wide enough for float noise in what a
     * panel reports (23.976025) and a three-decimal manifest value (23.976 for 24000/1001), and
     * far narrower than 24 − 23.976 = 0.024, so the two are never confused.
     */
    private const val TOLERANCE_HZ = 0.01f

    /**
     * The mode id to request, or null to leave the display as it is.
     *
     * Resolution first: the mode of the video's exact size, else the smallest that holds it in
     * both dimensions, else the current size. Then the rate, among modes of that size only: an
     * exact match, else the smallest integer multiple of the frame rate — never a non-multiple.
     * With no rate to match, the current rate is kept, or the nearest one if the new size lacks it.
     */
    fun pick(
        modes: List<Mode>,
        current: Mode,
        videoFps: Float?,
        videoWidth: Int,
        videoHeight: Int,
        matchRate: Boolean,
        matchResolution: Boolean
    ): Int? {
        if (!matchRate && !matchResolution) return null

        val size = (if (matchResolution && videoWidth > 0 && videoHeight > 0)
            bestSize(modes, videoWidth, videoHeight) else null)
            ?: (current.width to current.height)
        val sameSize = modes.filter { it.width == size.first && it.height == size.second }
        if (sameSize.isEmpty()) return null
        val sizeChanged = size != (current.width to current.height)

        val fps = known(videoFps)
        val chosen = (if (matchRate && fps != null) bestRate(sameSize, fps) else null)
            ?: if (sizeChanged) nearestRate(sameSize, current.refreshHz) else null
        return chosen?.id?.takeIf { it != current.id }
    }

    /**
     * What an adaptive stream asks of the display: its largest rendition, since the player
     * climbs to it once bandwidth allows and the first variant is often the smallest. Variants
     * share a frame rate, so a missing one is borrowed from any rendition that declares it.
     */
    fun target(renditions: List<Video>): Video? {
        if (renditions.isEmpty()) return null
        val largest = renditions
            .filter { it.width > 0 && it.height > 0 }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: renditions.first()
        val fps = known(largest.fps) ?: renditions.firstNotNullOfOrNull { known(it.fps) }
        return largest.copy(fps = fps)
    }

    private fun known(fps: Float?): Float? = fps?.takeIf { it.isFinite() && it > 0f }

    /** Exact size, else the smallest mode at least as large in both dimensions. */
    private fun bestSize(modes: List<Mode>, w: Int, h: Int): Pair<Int, Int>? {
        modes.firstOrNull { it.width == w && it.height == h }?.let { return w to h }
        return modes
            .filter { it.width >= w && it.height >= h }
            .minByOrNull { it.width.toLong() * it.height }
            ?.let { it.width to it.height }
    }

    /** The mode at the smallest integer multiple k of [fps] (k = 1 is an exact match). */
    private fun bestRate(modes: List<Mode>, fps: Float): Mode? =
        modes.mapNotNull { m ->
            val k = (m.refreshHz / fps).roundToInt()
            val drift = abs(m.refreshHz - k * fps)
            if (k >= 1 && drift <= TOLERANCE_HZ * k) Triple(m, k, drift) else null
        }.minWithOrNull(compareBy<Triple<Mode, Int, Float>> { it.second }.thenBy { it.third })?.first

    /** Closest refresh to [hz]; on a tie the higher one. */
    private fun nearestRate(modes: List<Mode>, hz: Float): Mode? =
        modes.minWithOrNull(compareBy<Mode> { abs(it.refreshHz - hz) }.thenByDescending { it.refreshHz })
}
