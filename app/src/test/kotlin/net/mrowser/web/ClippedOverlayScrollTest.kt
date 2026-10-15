package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ClippedOverlayScrollTest {

    @Test fun `a downward wheel step scrolls the content down in CSS px`() {
        assertEquals(24f, ClippedOverlayScroll.cssDelta(-0.1875f, 128f), 0.001f)
    }

    @Test fun `an upward wheel step scrolls the content up`() {
        assertEquals(-24f, ClippedOverlayScroll.cssDelta(0.1875f, 128f), 0.001f)
    }

    @Test fun `the script is aimed at the cursor as a fraction of the view`() {
        val js = ClippedOverlayScroll.script(480f, 1026f, 960, 1080, 24f)!!
        assertTrue(js, js.endsWith("(0.5,0.95,24.0)"))
    }

    @Test fun `the call keeps a dot decimal under a comma locale`() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("el-GR"))
        try {
            val js = ClippedOverlayScroll.script(240f, 270f, 960, 540, -24f)!!
            assertTrue(js, js.endsWith("(0.25,0.5,-24.0)"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun `the hit test is floored onto the page`() {
        // The last row at 2x is y = 539.5 of 540; elementFromPoint would round it off the page.
        val js = ClippedOverlayScroll.script(480f, 1079f, 960, 1080, 24f)!!
        assertTrue(js, js.contains("elementFromPoint(Math.floor(x),Math.floor(y))"))
    }

    @Test fun `the walk ends at the fixed element`() {
        // Past it lie the page's own boxes: an app root clipped to 100vh must never move.
        val js = ClippedOverlayScroll.script(480f, 1079f, 960, 1080, 24f)!!
        assertTrue(js, js.contains("e&&e!==end&&"))
        assertTrue(js, js.contains("end=a.parentElement"))
    }

    @Test fun `no script before the view has a size`() {
        assertNull(ClippedOverlayScroll.script(0f, 0f, 0, 0, 24f))
    }

    @Test fun `no script for a zero step`() {
        assertNull(ClippedOverlayScroll.script(480f, 530f, 960, 540, 0f))
    }
}
