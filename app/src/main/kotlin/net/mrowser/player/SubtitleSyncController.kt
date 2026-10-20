package net.mrowser.player

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.SubtitleView
import net.mrowser.stream.SubtitleTrack

/**
 * Renders the selected side-loaded subtitle track with a live, user-adjustable time offset.
 *
 * The ExoPlayer instance has no text track (PlayerActivity stops passing subtitle configs),
 * so this is the only source of cues for [subtitleView]. A ticker re-pushes the cues active
 * at `currentPosition + offsetMs` every [TICK_MS]; offset changes therefore apply instantly.
 * All methods run on the main thread except the per-track fetch+parse, which is offloaded.
 *
 * The ticker's render() skips [SubtitleView.setCues] when the active cues didn't change, so a
 * cue that stays on screen for seconds doesn't force a layout+draw pass on every tick — that
 * redundant main-thread work is what janked (froze) video on low-power TV hardware.
 */
@OptIn(UnstableApi::class)
class SubtitleSyncController(
    private val player: ExoPlayer,
    private val subtitleView: SubtitleView?,
    private val tracks: List<SubtitleTrack>,
    private val headers: Map<String, String>,
    private val onOffsetChanged: (Long) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val parsedCache = HashMap<Int, List<SubtitleCue>>()
    private val inFlight = HashSet<Int>()
    private var selectedIndex = -1
    private var offsetMs = 0L
    /** Texts of the cues last pushed to [subtitleView]; skips redundant setCues() calls. */
    private var lastTexts = emptyList<String>()
    /** Whether [subtitleView] currently shows cues (needed to clear when selection turns off). */
    private var showing = false

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** Auto-select the first track (today's "auto-show first") and begin rendering. */
    fun start() {
        if (tracks.isNotEmpty()) select(0)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        onOffsetChanged(offsetMs)
    }

    fun stop() {
        handler.removeCallbacks(ticker)
    }

    /** Re-post the ticker after a background round-trip, preserving selection and offset. */
    fun resume() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    fun adjust(deltaMs: Long) {
        offsetMs = (offsetMs + deltaMs).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        onOffsetChanged(offsetMs)
    }

    /** Re-fetch the selected track if its download failed (e.g. during a network outage). */
    fun retryFailed() {
        if (selectedIndex >= 0) select(selectedIndex)
    }

    fun trackLabels(): List<String> = tracks.map { it.label }

    /** True when a track is selected (subtitles render), false when off — drives the CC button icon. */
    fun isShowing(): Boolean = selectedIndex >= 0

    /** The selected track's index, or -1 when off — the picker's checked row. */
    fun selected(): Int = selectedIndex

    /** index -1 = off; otherwise the track index. Fetches+parses on first use. */
    fun select(index: Int) {
        selectedIndex = index
        if (index in tracks.indices && !parsedCache.containsKey(index) && inFlight.add(index)) {
            val track = tracks[index]
            // The subtitle file is frequently on a different host than the manifest, so
            // re-scope the Cookie header to the subtitle host rather than forwarding the
            // manifest-host cookie (cross-host session-cookie leak).
            val cookie = CookieManager.getInstance().getCookie(track.url)
            val trackHeaders = headers.toMutableMap().apply {
                if (cookie.isNullOrEmpty()) remove("Cookie") else put("Cookie", cookie)
            }
            Thread {
                val text = SubtitleFetcher.fetch(track.url, trackHeaders)
                if (text != null) {
                    val cues = SubtitleCueParser.parse(text)
                    handler.post {
                        parsedCache[index] = cues
                        inFlight.remove(index)
                    }
                } else {
                    handler.post {
                        inFlight.remove(index)
                    }
                }
            }.start()
        }
    }

    private fun render() {
        if (selectedIndex < 0) {
            if (showing) {
                showing = false
                subtitleView?.setCues(emptyList())
            }
            return
        }
        val cues = parsedCache[selectedIndex]
        if (cues == null) {
            if (showing) {
                showing = false
                subtitleView?.setCues(emptyList())
            }
            return
        }
        val active = ActiveCueFinder.activeAt(cues, player.currentPosition + offsetMs)
        val texts = active.map { it.text }
        if (texts != lastTexts) {
            lastTexts = texts.toList()
            subtitleView?.setCues(active.map { Cue.Builder().setText(it.text).build() })
            showing = true
        }
    }

    companion object {
        private const val TICK_MS = 150L
        private const val MAX_OFFSET_MS = 30_000L
    }
}
