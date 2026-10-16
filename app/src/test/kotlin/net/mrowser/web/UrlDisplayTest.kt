package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDisplayTest {

    @Test fun `https drops the scheme and splits host from the rest`() {
        val p = UrlDisplay.of("https://watch.cinemathek.tv/film/solaris-1972")!!
        assertTrue(p.secure)
        assertEquals("", p.lead)
        assertEquals("watch.cinemathek.tv", p.host)
        assertEquals("/film/solaris-1972", p.rest)
        assertEquals("watch.cinemathek.tv/film/solaris-1972", p.text)
    }

    @Test fun `http keeps its scheme in the lead and is not secure`() {
        val p = UrlDisplay.of("http://example.com/a?b=1")!!
        assertFalse(p.secure)
        assertEquals("http://", p.lead)
        assertEquals("example.com", p.host)
        assertEquals("/a?b=1", p.rest)
    }

    @Test fun `host only url has an empty rest`() {
        val p = UrlDisplay.of("https://example.com")!!
        assertEquals("example.com", p.host)
        assertEquals("", p.rest)
    }

    @Test fun `trailing slash, query and fragment straight after the host go to the rest`() {
        assertEquals("/", UrlDisplay.of("https://example.com/")!!.rest)
        assertEquals("?q=1", UrlDisplay.of("https://example.com?q=1")!!.rest)
        assertEquals("#top", UrlDisplay.of("https://example.com#top")!!.rest)
    }

    @Test fun `port goes to the rest`() {
        val p = UrlDisplay.of("http://127.0.0.1:8000/index.html")!!
        assertEquals("127.0.0.1", p.host)
        assertEquals(":8000/index.html", p.rest)
    }

    @Test fun `userinfo stays in the lead`() {
        val p = UrlDisplay.of("https://user:pw@example.com/x")!!
        assertEquals("user:pw@", p.lead)
        assertEquals("example.com", p.host)
        assertEquals("/x", p.rest)
    }

    @Test fun `ipv6 literal keeps its brackets in the host`() {
        val p = UrlDisplay.of("http://[::1]:8080/x")!!
        assertEquals("[::1]", p.host)
        assertEquals(":8080/x", p.rest)
    }

    @Test fun `scheme is matched case-insensitively and kept as written`() {
        val p = UrlDisplay.of("HTTP://Example.com/A")!!
        assertEquals("HTTP://", p.lead)
        assertEquals("Example.com", p.host)
        assertTrue(UrlDisplay.of("HTTPS://example.com")!!.secure)
    }

    @Test fun `non-web urls and empty hosts are null`() {
        assertNull(UrlDisplay.of("about:blank"))
        assertNull(UrlDisplay.of("data:text/html,hi"))
        assertNull(UrlDisplay.of("file:///sdcard/x.html"))
        assertNull(UrlDisplay.of("https:///path"))
        assertNull(UrlDisplay.of(""))
    }

    // The bar submits whatever the field holds through UrlNormalizer, exactly as before; the
    // shortened text must therefore normalize back to the very page it was made from.
    @Test fun `displayed text submits back to the same url`() {
        listOf(
            "https://watch.cinemathek.tv/film/solaris-1972",
            "https://www.youtube.com/",
            "https://example.com",
            "https://example.com?q=1#f",
            "https://user:pw@example.com:8443/x",
            "http://example.com/a?b=1",
            "http://127.0.0.1:8000/",
        ).forEach { url ->
            assertEquals(url, UrlNormalizer.normalize(UrlDisplay.of(url)!!.text))
        }
    }
}
