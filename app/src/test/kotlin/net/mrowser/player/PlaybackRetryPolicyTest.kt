package net.mrowser.player

import net.mrowser.player.PlaybackRetryPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRetryPolicyTest {

    private fun net(attempt: Int = 1, outageMs: Long = 0, status: Int? = null) =
        PlaybackRetryPolicy.decide(false, true, status, attempt, outageMs)

    @Test fun `backoff doubles and caps at 8s`() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 8000L),
            (1..5).map { (net(attempt = it) as Action.Retry).delayMs })
    }

    @Test fun `gives up once the outage window is spent`() {
        assertEquals(Action.GiveUp, net(outageMs = PlaybackRetryPolicy.WINDOW_MS))
    }

    @Test fun `client errors give up but timeouts and 5xx retry`() {
        assertEquals(Action.GiveUp, net(status = 403))
        assertEquals(Action.GiveUp, net(status = 404))
        assert(net(status = 429) is Action.Retry)
        assert(net(status = 503) is Action.Retry)
    }

    @Test fun `behind live window jumps to the live edge`() {
        assertEquals(Action.LiveEdge, PlaybackRetryPolicy.decide(true, false, null, 1, 0))
    }

    @Test fun `non-network errors give up`() {
        assertEquals(Action.GiveUp, PlaybackRetryPolicy.decide(false, false, null, 1, 0))
    }
}
