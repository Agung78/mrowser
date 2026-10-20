package net.mrowser.player

/** Selects the cues that should be on screen at a given media time (start inclusive, end exclusive).
 *
 * Cues are [SubtitleCueParser.parse]-sorted by start time, so the scan short-circuits once a cue
 * starts past [timeMs] — no whole-list scan on every 150 ms subtitle tick. */
object ActiveCueFinder {
    fun activeAt(cues: List<SubtitleCue>, timeMs: Long): List<SubtitleCue> =
        cues.takeWhile { it.startMs <= timeMs }.filter { timeMs < it.endMs }
}
