package net.mrowser.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackHeadersTest {

    private val page = "https://tv.site.net/movie/1"
    private val stream = "https://cdn.video.net/1/abc/master.m3u8"

    @Test fun `replays the referer and origin the webview sent for the stream`() {
        // An embedded player runs in a cross-origin iframe; its CDN checks the iframe's
        // origin, not the top page's (#51).
        val h = PlaybackHeaders.build(
            stream, page, "UA",
            mapOf("Referer" to "https://player.embed.io/?token=1", "Origin" to "https://player.embed.io")
        )
        assertEquals("UA", h["User-Agent"])
        assertEquals("https://player.embed.io/?token=1", h["Referer"])
        assertEquals("https://player.embed.io", h["Origin"])
    }

    @Test fun `matches sniffed header names case-insensitively`() {
        val h = PlaybackHeaders.build(
            stream, page, "UA",
            mapOf("referer" to "https://player.embed.io/", "ORIGIN" to "https://player.embed.io")
        )
        assertEquals("https://player.embed.io/", h["Referer"])
        assertEquals("https://player.embed.io", h["Origin"])
    }

    @Test fun `derives a cross-origin origin from the sniffed referer when origin is not reported`() {
        val h = PlaybackHeaders.build(
            stream, page, "UA", mapOf("Referer" to "https://player.embed.io:8443/frame?x=1")
        )
        assertEquals("https://player.embed.io:8443", h["Origin"])
    }

    @Test fun `sends no origin for a same-origin stream`() {
        val h = PlaybackHeaders.build(
            "https://tv.site.net/hls/master.m3u8", page, "UA", mapOf("Referer" to page)
        )
        assertFalse(h.containsKey("Origin"))
    }

    @Test fun `with nothing sniffed sends exactly what it always did`() {
        // WebResourceRequest.requestHeaders is nullable; the sniffer then sees an empty map.
        val h = PlaybackHeaders.build(stream, page, "UA", emptyMap())
        assertEquals(mapOf("User-Agent" to "UA", "Referer" to page), h)
    }

    @Test fun `sends neither referer nor origin with no page and nothing sniffed`() {
        val h = PlaybackHeaders.build(stream, "", "UA", emptyMap())
        assertEquals(mapOf("User-Agent" to "UA"), h)
    }

    @Test fun `replays no cookie, authorization or other header the page set`() {
        // Per-request signatures and tokens go stale; only the hotlink pair is replayed.
        val h = PlaybackHeaders.build(
            stream, page, "UA",
            mapOf(
                "Referer" to page, "Accepts-Controls" to "d7d8", "Authorizations" to "Bearer x",
                "Authorization" to "Bearer y", "Cookie" to "session=1"
            )
        )
        assertEquals(setOf("User-Agent", "Referer", "Origin"), h.keys)
    }

    @Test fun `origin keeps a non-default port and drops a default one`() {
        assertEquals("http://a.net:8080", PlaybackHeaders.originOf("http://A.net:8080/x"))
        assertEquals("https://a.net", PlaybackHeaders.originOf("https://a.net:443/x"))
        assertEquals("http://a.net", PlaybackHeaders.originOf("http://a.net:80"))
    }

    @Test fun `origin is null for a non-web or malformed url`() {
        assertNull(PlaybackHeaders.originOf("about:blank"))
        assertNull(PlaybackHeaders.originOf("data:text/plain,x"))
        assertNull(PlaybackHeaders.originOf("not a url"))
    }
}
