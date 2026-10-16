package net.mrowser.web

import android.view.View
import android.view.ViewGroup
import android.widget.EditText

/**
 * Drives the address-bar overlay: visibility state, slide/fade animation, and
 * focus handoff. Visibility decisions delegate to ChromeVisibility.
 *
 * The bar is only ever opened for interaction (MENU, or a BACK hold), so it never
 * auto-hides: it stays up while the user types / navigates and closes on BACK,
 * MENU, or submit.
 */
class ChromeController(
    private val bar: View,
    private val urlInput: EditText,
    private val webView: View
) {
    private var state = ChromeVisibility.State.HIDDEN

    val isVisible: Boolean get() = state == ChromeVisibility.State.VISIBLE

    fun requestReveal(atTop: Boolean) = dispatch(ChromeVisibility.Event.RevealRequested(atTop))
    fun onInteracted() = dispatch(ChromeVisibility.Event.Interacted)
    fun onPageInteracted() = dispatch(ChromeVisibility.Event.PageInteracted)

    /**
     * An overlay is opening over the page. The bar goes at once rather than sliding: the
     * overlay covers it anyway, and while it is still VISIBLE (even mid-animation) the
     * window-global D-pad focus search can land on its buttons, behind the overlay. Focus is
     * left alone — the overlay is taking it, so the page must not.
     */
    fun dismiss() {
        val next = ChromeVisibility.reduce(state, ChromeVisibility.Event.Dismissed)
        if (next == state) return
        state = next
        bar.animate().cancel()
        bar.visibility = View.GONE
    }

    private fun dispatch(event: ChromeVisibility.Event) {
        val next = ChromeVisibility.reduce(state, event)
        if (next == state) return
        state = next
        if (next == ChromeVisibility.State.VISIBLE) animateIn() else animateOut()
    }

    private fun animateIn() {
        bar.visibility = View.VISIBLE
        bar.translationY = -offscreenOffset()
        bar.alpha = 0f
        bar.animate().translationY(0f).alpha(1f).setDuration(180).start()
        urlInput.requestFocus()
    }

    private fun animateOut() {
        bar.animate().translationY(-offscreenOffset()).alpha(0f).setDuration(160)
            .withEndAction { bar.visibility = View.GONE }.start()
        webView.requestFocus()
    }

    /** The capsule floats below the top edge, so clearing the screen takes its height plus
     *  that margin. A bar still GONE has never been measured: fall back to its fixed height. */
    private fun offscreenOffset(): Float {
        val lp = bar.layoutParams
        val height = if (bar.height > 0) bar.height else lp.height.coerceAtLeast(0)
        val top = (lp as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        return (height + top).toFloat()
    }
}
