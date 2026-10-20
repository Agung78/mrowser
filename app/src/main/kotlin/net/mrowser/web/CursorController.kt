package net.mrowser.web

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView

/**
 * Cursor state + input synthesis. Moves a virtual pointer with the D-pad and
 * translates OK / edge proximity into MotionEvents dispatched to the WebView.
 * Pure geometry lives in CursorGeometry.
 */
class CursorController(
    private val webView: WebView,
    private val speedMultiplier: () -> Float = { 1f },
    private val invalidate: () -> Unit
) {
    enum class Mode { CURSOR, FOCUS }

    var mode = Mode.CURSOR
        private set

    var x = 0f
        private set
    var y = 0f
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var dirX = 0
    private var dirY = 0
    private var holdStart = 0L
    private val edgeZonePx get() = webView.resources.displayMetrics.density * 48f

    private val mover = object : Runnable {
        override fun run() {
            if (dirX == 0 && dirY == 0) return
            val held = SystemClock.uptimeMillis() - holdStart
            val speed = CursorGeometry.speedForHoldMs(held, speedMultiplier())
            val p = CursorGeometry.step(
                CursorGeometry.Point(x, y), dirX, dirY, speed, webView.width, webView.height
            )
            x = p.x; y = p.y
            // Hover tracks the pointer, so :hover-revealed controls exist before a click lands.
            val now = SystemClock.uptimeMillis()
            dispatch(MotionEvent.ACTION_HOVER_MOVE, now, now)
            val ticks = CursorGeometry.wheelStep(
                dirY, y, webView.height, edgeZonePx, WHEEL_TICKS_PER_FRAME
            )
            if (ticks != 0f) {
                wheel(ticks)
                scrollClippedOverlay(ticks)
            }
            invalidate()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    fun center(width: Int, height: Int) {
        x = width / 2f; y = height / 2f; invalidate()
    }

    fun startMove(dx: Int, dy: Int) {
        if (dirX == dx && dirY == dy) return
        dirX = dx; dirY = dy; holdStart = SystemClock.uptimeMillis()
        handler.removeCallbacks(mover); handler.post(mover)
    }

    fun stopMove() {
        dirX = 0; dirY = 0; handler.removeCallbacks(mover)
    }

    private var pendingRelease: Runnable? = null

    /**
     * A click: mouse hover, then a touchscreen press held [CLICK_HOLD_MS] before release —
     * long enough for pages that re-render between pointerdown and pointerup. The press is
     * touch because this WebView drops mouse-source DOWN/UP (see [dispatch]).
     */
    fun tap() {
        flushRelease()
        val t = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_HOVER_MOVE, t, t)
        dispatch(MotionEvent.ACTION_DOWN, t, t)
        val release = Runnable {
            pendingRelease = null
            val r = SystemClock.uptimeMillis()
            dispatch(MotionEvent.ACTION_UP, t, r)
        }
        pendingRelease = release
        handler.postDelayed(release, CLICK_HOLD_MS)
    }

    /** Runs a still-pending release now, so a second tap never overlaps the first. */
    private fun flushRelease() {
        val r = pendingRelease ?: return
        handler.removeCallbacks(r)
        r.run()
    }

    fun toggleMode() {
        mode = if (mode == Mode.CURSOR) Mode.FOCUS else Mode.CURSOR
        stopMove()
        invalidate()
    }

    /**
     * A mouse wheel at the cursor: positive [ticks] scroll up, negative down.
     *
     * The page, not the WebView, decides what moves. `scrollBy` only ever scrolled
     * the document, so a cookie dialog with its own scroller — fixed, and often in a
     * cross-origin iframe — could not be reached at all (#31). A wheel is hit-tested
     * at the pointer and walks the scroll chain outwards from whatever is under it,
     * exactly as a desktop mouse does, and Chromium clamps it at each end for us.
     */
    private fun wheel(ticks: Float) {
        val t = SystemClock.uptimeMillis()
        // Tell Chromium where the pointer is first: the wheel is hit-tested at that position.
        dispatch(MotionEvent.ACTION_HOVER_MOVE, t, t)
        dispatch(MotionEvent.ACTION_SCROLL, t, t, vscroll = ticks)
    }

    /**
     * What the wheel cannot reach: a fixed overlay cut off by `overflow: hidden`,
     * such as a consent dialog taller than the screen (#31). See ClippedOverlayScroll.
     */
    private fun scrollClippedOverlay(ticks: Float) {
        val js = ClippedOverlayScroll.script(
            x, y, webView.width, webView.height,
            ClippedOverlayScroll.cssDelta(ticks, WHEEL_TICK_PX)
        ) ?: return
        webView.evaluateJavascript(js, null)
    }

    /** One mouse-source event at the cursor. Hover/scroll/button-press go generic; the rest as touch. */
    private fun dispatch(
        action: Int, down: Long, event: Long, buttons: Int = 0, vscroll: Float = 0f
    ) {
        // Press/release go as a touchscreen tap: this WebView drops mouse-source DOWN/UP
        // outright (verified on the TCL TV), and `input mouse tap` arrives as touch too.
        val touch = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = if (touch) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_MOUSE
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = this@CursorController.x
            this.y = this@CursorController.y
            if (vscroll != 0f) setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll)
        })
        val e = MotionEvent.obtain(
            down, event, action, 1, props, coords,
            0, buttons, 1f, 1f, 0, 0,
            if (touch) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_MOUSE, 0
        )
        if (touch) {
            webView.dispatchTouchEvent(e)
        } else {
            webView.dispatchGenericMotionEvent(e)
        }
        e.recycle()
    }

    companion object {
        const val FRAME_MS = 16L

        /** Press-to-release gap of a click. */
        const val CLICK_HOLD_MS = 40L

        /** What the page scrolled per frame back when this moved the document itself. */
        const val SCROLL_STEP_PX = 24

        /** One wheel tick is 128 CSS px in this WebView (measured on the TV box). */
        const val WHEEL_TICK_PX = 128f

        /** Fractional ticks are honoured, so the old per-frame distance is kept. */
        const val WHEEL_TICKS_PER_FRAME = SCROLL_STEP_PX / WHEEL_TICK_PX
    }
}
