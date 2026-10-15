package net.mrowser.web

/**
 * The edge-scroll fallback for an overlay whose content is cut off by `overflow: hidden`.
 *
 * A consent dialog taller than the TV's 540 CSS px viewport, centred in a fixed
 * `overflow: hidden` box (Quantcast's on antenna.gr, #31), has nothing a wheel can
 * scroll: the box refuses user scrolling, the dialog's own `overflow: auto` text area
 * is never height-constrained so it never overflows, and the wheel falls through to the
 * document behind. Its buttons sit below the screen. A desktop browser reaches them by
 * keyboard focus, which scrolls clipped boxes programmatically; this does the same
 * from the cursor.
 *
 * The script walks out from the element under the cursor the way scroll chaining
 * does. A user-scrollable box with room left is the wheel's job, so it stops there. A
 * clipped box with room left is scrolled by [cssDelta]. It only acts under a
 * `position: fixed` element — an overlay the document can never scroll into view —
 * and the walk ends at that element, so a carousel or a truncated card in the page
 * keeps its clipping, and so does an app root (`height: 100vh; overflow: hidden`)
 * that merely contains a fixed bottom nav. `html` and `body` are left to the wheel
 * too: a scroll-locked page behind a modal stays put.
 * Main frame only (`evaluateJavascript`); a dialog in a cross-origin iframe still
 * relies on the wheel.
 */
object ClippedOverlayScroll {

    private const val FUNCTION = "(function(fx,fy,dy){" +
        "var v=window.visualViewport;" +
        "var x=v?v.offsetLeft+fx*v.width:fx*window.innerWidth;" +
        "var y=v?v.offsetTop+fy*v.height:fy*window.innerHeight;" +
        "var hit=document.elementFromPoint(x,y),a=hit;" +
        "while(a&&getComputedStyle(a).position!=='fixed')a=a.parentElement;" +
        "if(!a)return;" +
        "for(var e=hit,end=a.parentElement;" +
        "e&&e!==end&&e!==document.body&&e!==document.documentElement;e=e.parentElement){" +
        "var o=getComputedStyle(e).overflowY;" +
        "if(o==='visible'||o==='clip')continue;" +
        "if(dy>0?e.scrollHeight-e.clientHeight-e.scrollTop<1:e.scrollTop<=0)continue;" +
        "if(o==='hidden')e.scrollBy({top:dy,behavior:'instant'});" +
        "return;}" +
        "})"

    /** CSS px for one frame's wheel [ticks] (positive = up, as `AXIS_VSCROLL`): positive scrolls down. */
    fun cssDelta(ticks: Float, tickPx: Float): Float = -ticks * tickPx

    /**
     * The script for a cursor at ([x], [y]) in a [width]×[height] view, or null when
     * there is nothing to do. The cursor goes in as a fraction of the view so the page
     * maps it onto its own viewport, whatever the WebView's scale.
     */
    fun script(x: Float, y: Float, width: Int, height: Int, dyCss: Float): String? {
        if (width <= 0 || height <= 0 || dyCss == 0f) return null
        // Float.toString, not String.format: a Greek or German locale would write "0,5".
        return "$FUNCTION(${x / width},${y / height},$dyCss)"
    }
}
