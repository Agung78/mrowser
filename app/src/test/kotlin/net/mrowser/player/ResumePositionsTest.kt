package net.mrowser.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePositionsTest {

    private val url = "https://cdn.example/movie/master.m3u8"
    private val hour = 3_600_000L

    @Test fun `unknown url starts from zero`() {
        assertEquals(0L, ResumePositions().startFor(url))
    }

    @Test fun `a mid-stream position is resumed`() {
        val r = ResumePositions()
        r.record(url, positionMs = 600_000, durationMs = hour, isLive = false)
        assertEquals(600_000L, r.startFor(url))
    }

    @Test fun `urls are matched exactly`() {
        val r = ResumePositions()
        r.record(url, positionMs = 600_000, durationMs = hour, isLive = false)
        assertEquals(0L, r.startFor("$url?t=1"))
    }

    @Test fun `a position before the first ten seconds is not worth resuming`() {
        val r = ResumePositions()
        r.record(url, positionMs = 9_999, durationMs = hour, isLive = false)
        assertEquals(0L, r.startFor(url))
    }

    @Test fun `exactly ten seconds is resumed`() {
        val r = ResumePositions()
        r.record(url, positionMs = 10_000, durationMs = hour, isLive = false)
        assertEquals(10_000L, r.startFor(url))
    }

    @Test fun `the last thirty seconds count as finished`() {
        val r = ResumePositions()
        r.record(url, positionMs = hour - 30_000, durationMs = hour, isLive = false)
        assertEquals(0L, r.startFor(url))
    }

    @Test fun `just before the last thirty seconds is resumed`() {
        val r = ResumePositions()
        r.record(url, positionMs = hour - 30_001, durationMs = hour, isLive = false)
        assertEquals(hour - 30_001, r.startFor(url))
    }

    @Test fun `a live stream is never resumed`() {
        val r = ResumePositions()
        r.record(url, positionMs = 600_000, durationMs = hour, isLive = true)
        assertEquals(0L, r.startFor(url))
    }

    @Test fun `an unknown duration is never resumed`() {
        val r = ResumePositions()
        // media3's C.TIME_UNSET
        r.record(url, positionMs = 600_000, durationMs = Long.MIN_VALUE + 1, isLive = false)
        assertEquals(0L, r.startFor(url))
    }

    @Test fun `a later record overwrites the earlier one`() {
        val r = ResumePositions()
        r.record(url, positionMs = 600_000, durationMs = hour, isLive = false)
        r.record(url, positionMs = 900_000, durationMs = hour, isLive = false)
        assertEquals(900_000L, r.startFor(url))
    }

    @Test fun `seeking back to the start removes the saved position`() {
        val r = ResumePositions()
        r.record(url, positionMs = 600_000, durationMs = hour, isLive = false)
        r.record(url, positionMs = 2_000, durationMs = hour, isLive = false)
        assertEquals(0L, r.startFor(url))
    }

    @Test fun `reaching the end removes the saved position`() {
        val r = ResumePositions()
        r.record(url, positionMs = 600_000, durationMs = hour, isLive = false)
        r.record(url, positionMs = hour - 5_000, durationMs = hour, isLive = false)
        assertEquals(0L, r.startFor(url))
    }

    @Test fun `clear removes the saved position`() {
        val r = ResumePositions()
        r.record(url, positionMs = 600_000, durationMs = hour, isLive = false)
        r.clear(url)
        assertEquals(0L, r.startFor(url))
    }

    @Test fun `the least recently used entry is evicted past capacity`() {
        val r = ResumePositions(capacity = 2)
        r.record("a", positionMs = 60_000, durationMs = hour, isLive = false)
        r.record("b", positionMs = 60_000, durationMs = hour, isLive = false)
        r.record("c", positionMs = 60_000, durationMs = hour, isLive = false)
        assertEquals(0L, r.startFor("a"))
        assertEquals(60_000L, r.startFor("b"))
        assertEquals(60_000L, r.startFor("c"))
    }

    @Test fun `reading an entry keeps it from eviction`() {
        val r = ResumePositions(capacity = 2)
        r.record("a", positionMs = 60_000, durationMs = hour, isLive = false)
        r.record("b", positionMs = 60_000, durationMs = hour, isLive = false)
        r.startFor("a")
        r.record("c", positionMs = 60_000, durationMs = hour, isLive = false)
        assertEquals(60_000L, r.startFor("a"))
        assertEquals(0L, r.startFor("b"))
    }

    @Test fun `the default capacity holds twenty streams`() {
        val r = ResumePositions()
        (0..20).forEach { r.record("u$it", positionMs = 60_000, durationMs = hour, isLive = false) }
        assertEquals(0L, r.startFor("u0"))
        assertEquals(60_000L, r.startFor("u1"))
        assertEquals(60_000L, r.startFor("u20"))
    }
}
