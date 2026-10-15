package net.mrowser.player

import net.mrowser.player.DisplayModeMatcher.Mode
import net.mrowser.player.DisplayModeMatcher.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayModeMatcherTest {

    // Refresh rates as a real panel reports them: NTSC rates are 1000/1001 fractions.
    private val r23 = 24000f / 1001f   // 23.976
    private val r29 = 30000f / 1001f   // 29.97
    private val r59 = 60000f / 1001f   // 59.94
    private val r47 = 48000f / 1001f   // 47.952
    private val r119 = 120000f / 1001f // 119.88

    private fun fhd(id: Int, hz: Float) = Mode(id, 1920, 1080, hz)
    private fun uhd(id: Int, hz: Float) = Mode(id, 3840, 2160, hz)
    private fun hd(id: Int, hz: Float) = Mode(id, 1280, 720, hz)

    private fun pickRate(modes: List<Mode>, current: Mode, fps: Float?) =
        DisplayModeMatcher.pick(modes, current, fps, 1920, 1080, matchRate = true, matchResolution = false)

    // --- both off ---

    @Test fun `both toggles off leaves the display alone`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 24f))
        assertNull(DisplayModeMatcher.pick(modes, modes[0], 24f, 1920, 1080, false, false))
    }

    // --- frame rate: exact ---

    @Test fun `24 fps picks an exact 24 Hz mode`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 24f), fhd(3, 50f))
        assertEquals(2, pickRate(modes, modes[0], 24f))
    }

    @Test fun `25 fps picks 25 over 50`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 50f), fhd(3, 25f))
        assertEquals(3, pickRate(modes, modes[0], 25f))
    }

    @Test fun `exact wins over a multiple listed first`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 120f), fhd(3, 24f))
        assertEquals(3, pickRate(modes, modes[0], 24f))
    }

    @Test fun `exact match tolerates float noise in the reported rate`() {
        val modes = listOf(fhd(1, 60.000004f), fhd(2, 23.976025f))
        assertEquals(2, pickRate(modes, modes[0], 23.976f))
    }

    // --- frame rate: 23.976 versus 24 ---

    @Test fun `23_976 does not take a 24 Hz mode`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 24f), fhd(3, r23))
        assertEquals(3, pickRate(modes, modes[0], r23))
    }

    @Test fun `24 does not take a 23_976 Hz mode`() {
        val modes = listOf(fhd(1, r59), fhd(2, r23), fhd(3, 24f))
        assertEquals(3, pickRate(modes, modes[0], 24f))
    }

    @Test fun `23_976 with only 24 and 60 Hz modes leaves the display alone`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 24f))
        assertNull(pickRate(modes, modes[0], r23))
    }

    @Test fun `24 with only NTSC modes leaves the display alone`() {
        val modes = listOf(fhd(1, r59), fhd(2, r23), fhd(3, r119))
        assertNull(pickRate(modes, modes[0], 24f))
    }

    @Test fun `23_976 from a three-decimal manifest attribute still matches`() {
        // HLS FRAME-RATE carries three decimals; 23.976 is not exactly 24000/1001.
        val modes = listOf(fhd(1, 60f), fhd(2, r23))
        assertEquals(2, pickRate(modes, modes[0], 23.976f))
    }

    // --- frame rate: multiples ---

    @Test fun `24 fps with no 24 Hz takes 48 before 72 and 120`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 120f), fhd(3, 72f), fhd(4, 48f))
        assertEquals(4, pickRate(modes, modes[0], 24f))
    }

    @Test fun `24 fps with only 60 and 120 takes 120`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 120f))
        assertEquals(2, pickRate(modes, modes[0], 24f))
    }

    @Test fun `23_976 takes 47_952 then 119_88`() {
        val modes = listOf(fhd(1, 60f), fhd(2, r119), fhd(3, r47))
        assertEquals(3, pickRate(modes, modes[0], r23))
        val noDouble = listOf(fhd(1, 60f), fhd(2, r119), fhd(3, 120f))
        assertEquals(2, pickRate(noDouble, noDouble[0], r23))
    }

    @Test fun `23_976 never takes 120`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 120f))
        assertNull(pickRate(modes, modes[0], r23))
    }

    @Test fun `25 fps takes 50`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 50f))
        assertEquals(2, pickRate(modes, modes[0], 25f))
    }

    @Test fun `29_97 takes 59_94 not 60`() {
        val modes = listOf(fhd(1, 50f), fhd(2, 60f), fhd(3, r59))
        assertEquals(3, pickRate(modes, modes[0], r29))
    }

    @Test fun `30 fps takes 60 not 59_94`() {
        val modes = listOf(fhd(1, 50f), fhd(2, r59), fhd(3, 60f))
        assertEquals(3, pickRate(modes, modes[0], 30f))
    }

    @Test fun `a non-multiple is never picked`() {
        val modes = listOf(fhd(1, 50f), fhd(2, 60f))
        assertNull(pickRate(modes, modes[0], 24f))
        val pal = listOf(fhd(1, 60f), fhd(2, 30f))
        assertNull(pickRate(pal, pal[0], 25f))
    }

    @Test fun `the current mode being the match returns null`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 24f))
        assertNull(pickRate(modes, modes[0], 30f))
        assertNull(pickRate(modes, modes[1], 24f))
    }

    // --- frame rate: unknown ---

    @Test fun `unknown fps leaves the rate alone`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 24f))
        assertNull(pickRate(modes, modes[0], null))
        assertNull(pickRate(modes, modes[0], -1f)) // Format.NO_VALUE
        assertNull(pickRate(modes, modes[0], 0f))
        assertNull(pickRate(modes, modes[0], Float.NaN))
    }

    // --- rate only keeps the resolution ---

    @Test fun `rate only keeps the current resolution`() {
        val modes = listOf(uhd(1, 60f), fhd(2, 24f), uhd(3, 24f))
        assertEquals(3, DisplayModeMatcher.pick(modes, modes[0], 24f, 1920, 1080, true, false))
    }

    @Test fun `rate only never changes resolution to find a rate`() {
        val modes = listOf(uhd(1, 60f), fhd(2, 24f))
        assertNull(DisplayModeMatcher.pick(modes, modes[0], 24f, 1920, 1080, true, false))
    }

    @Test fun `rate only ignores an unknown video size`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 24f))
        assertEquals(2, DisplayModeMatcher.pick(modes, modes[0], 24f, 0, 0, true, false))
    }

    // --- resolution only ---

    private fun pickRes(modes: List<Mode>, current: Mode, w: Int, h: Int) =
        DisplayModeMatcher.pick(modes, current, 24f, w, h, matchRate = false, matchResolution = true)

    @Test fun `720p video on a 4K panel takes the 720p mode`() {
        val modes = listOf(uhd(1, 60f), fhd(2, 60f), hd(3, 60f))
        assertEquals(3, pickRes(modes, modes[0], 1280, 720))
    }

    @Test fun `with no exact size takes the smallest mode that fits`() {
        val modes = listOf(uhd(1, 60f), fhd(2, 60f), hd(3, 60f))
        // Scope 1920x800 fits 1080p; 1440x1080 too.
        assertEquals(2, pickRes(modes, modes[0], 1920, 800))
        assertEquals(2, pickRes(modes, modes[0], 1440, 1080))
        // 960x540 fits 720p before 1080p.
        assertEquals(3, pickRes(modes, modes[0], 960, 540))
    }

    @Test fun `a video bigger than every mode keeps the current resolution`() {
        val modes = listOf(fhd(1, 60f), hd(2, 60f))
        assertNull(pickRes(modes, modes[0], 3840, 2160))
        assertNull(pickRes(modes, modes[1], 3840, 2160))
    }

    @Test fun `an unknown video size keeps the current resolution`() {
        val modes = listOf(uhd(1, 60f), fhd(2, 60f))
        assertNull(pickRes(modes, modes[0], 0, 0))
        assertNull(pickRes(modes, modes[0], -1, -1))
    }

    @Test fun `resolution only keeps the current rate when the new size has it`() {
        val modes = listOf(uhd(1, 50f), fhd(2, 60f), fhd(3, 50f), fhd(4, 24f))
        assertEquals(3, pickRes(modes, modes[0], 1920, 1080))
    }

    @Test fun `resolution only takes the nearest rate when the new size lacks the current one`() {
        val modes = listOf(uhd(1, r59), fhd(2, 24f), fhd(3, 60f))
        assertEquals(3, pickRes(modes, modes[0], 1920, 1080))
    }

    @Test fun `resolution already matching returns null`() {
        val modes = listOf(fhd(1, 60f), uhd(2, 60f))
        assertNull(pickRes(modes, modes[0], 1920, 1080))
    }

    // --- both on ---

    private fun pickBoth(modes: List<Mode>, current: Mode, fps: Float?, w: Int, h: Int) =
        DisplayModeMatcher.pick(modes, current, fps, w, h, matchRate = true, matchResolution = true)

    @Test fun `both on takes the size then the rate within it`() {
        val modes = listOf(uhd(1, 60f), uhd(2, 24f), fhd(3, 60f), fhd(4, 24f), hd(5, 60f))
        assertEquals(4, pickBoth(modes, modes[0], 24f, 1920, 1080))
        assertEquals(2, pickBoth(modes, modes[2], 24f, 3840, 2160))
    }

    @Test fun `both on takes a multiple within the size`() {
        val modes = listOf(uhd(1, 60f), fhd(2, 60f), fhd(3, 50f))
        assertEquals(3, pickBoth(modes, modes[0], 25f, 1920, 1080))
    }

    @Test fun `both on keeps the size even if only another size has the rate`() {
        val modes = listOf(uhd(1, 60f), uhd(2, 24f), fhd(3, 60f))
        assertEquals(3, pickBoth(modes, modes[0], 24f, 1920, 1080))
    }

    @Test fun `both on with unknown fps still matches the size`() {
        val modes = listOf(uhd(1, 60f), fhd(2, 60f), fhd(3, 24f))
        assertEquals(2, pickBoth(modes, modes[0], null, 1920, 1080))
    }

    @Test fun `both on with unknown size still matches the rate`() {
        val modes = listOf(fhd(1, 60f), fhd(2, 24f), uhd(3, 24f))
        assertEquals(2, pickBoth(modes, modes[0], 24f, 0, 0))
    }

    @Test fun `both on and already right returns null`() {
        val modes = listOf(fhd(1, 24f), fhd(2, 60f), uhd(3, 24f))
        assertNull(pickBoth(modes, modes[0], 24f, 1920, 1080))
    }

    // --- degenerate input ---

    @Test fun `no modes leaves the display alone`() {
        assertNull(DisplayModeMatcher.pick(emptyList(), fhd(1, 60f), 24f, 1280, 720, true, true))
    }

    @Test fun `a single mode leaves the display alone`() {
        val only = fhd(1, 60f)
        assertNull(DisplayModeMatcher.pick(listOf(only), only, 24f, 1280, 720, true, true))
    }

    // --- target: what an adaptive stream asks for ---

    @Test fun `target is the largest rendition`() {
        val v = DisplayModeMatcher.target(
            listOf(Video(640, 360, 25f), Video(1920, 1080, 25f), Video(1280, 720, 25f))
        )
        assertEquals(Video(1920, 1080, 25f), v)
    }

    @Test fun `target borrows a known fps when the largest has none`() {
        val v = DisplayModeMatcher.target(listOf(Video(1920, 1080, -1f), Video(1280, 720, r23)))
        assertEquals(Video(1920, 1080, r23), v)
    }

    @Test fun `target with no fps anywhere has null fps`() {
        val v = DisplayModeMatcher.target(listOf(Video(1920, 1080, -1f), Video(1280, 720, null)))
        assertEquals(Video(1920, 1080, null), v)
    }

    @Test fun `target of nothing is null`() {
        assertNull(DisplayModeMatcher.target(emptyList()))
    }

    @Test fun `target prefers a rendition with a size`() {
        val v = DisplayModeMatcher.target(listOf(Video(-1, -1, 30f), Video(1280, 720, 30f)))
        assertEquals(Video(1280, 720, 30f), v)
    }

    @Test fun `target with no size anywhere still carries the fps`() {
        // Rate matching needs no size, so an unsized rendition is still worth returning.
        assertEquals(Video(-1, -1, 30f), DisplayModeMatcher.target(listOf(Video(-1, -1, 30f))))
    }
}
