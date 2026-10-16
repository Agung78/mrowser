package net.mrowser.home

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import net.mrowser.R
import net.mrowser.data.Favorite
import net.mrowser.data.FavoritesOps
import net.mrowser.data.FavoritesRepository

/**
 * Edit (title + url), reorder, or delete a favorite. onChanged gets the url of the card that
 * should hold focus once the grid is rebuilt, or null when there is none (deleted).
 */
object FavoriteDialog {

    fun show(context: Context, repository: FavoritesRepository, fav: Favorite, onChanged: (String?) -> Unit) {
        val titleField = EditText(context).apply {
            setText(fav.title); hint = context.getString(R.string.title_hint)
        }
        val urlField = EditText(context).apply {
            setText(fav.url); hint = context.getString(R.string.url_hint)
        }
        // The theme's stock button marks focus with a faint grey lift that is unreadable from
        // the couch; the app's accent focus ring is what every other D-pad target uses.
        fun moveButton(text: Int) = Button(context).apply {
            setText(text)
            setBackgroundResource(R.drawable.focus_ring)
            stateListAnimator = null
        }
        val moveLeft = moveButton(R.string.move_left)
        val moveRight = moveButton(R.string.move_right)
        val gap = (4 * context.resources.displayMetrics.density).toInt()
        val moveRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(moveLeft, moveRight).forEach { button ->
                addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(gap, gap, gap, gap)
                })
            }
        }

        // Moves are keyed by the original url, which only Save can change, so pending
        // field edits survive any number of moves and Save still finds the entry.
        fun syncMoveButtons() {
            val items = repository.findAll()
            moveRow.visibility = if (items.size < 2) View.GONE else View.VISIBLE
            val leftHadFocus = moveLeft.isFocused
            val rightHadFocus = moveRight.isFocused
            moveLeft.isEnabled = FavoritesOps.canMove(items, fav.url, -1)
            moveRight.isEnabled = FavoritesOps.canMove(items, fav.url, 1)
            // focus_ring has no disabled state, so dim an end-of-list button by hand.
            listOf(moveLeft, moveRight).forEach { it.alpha = if (it.isEnabled) 1f else DISABLED_ALPHA }
            // A button disabled under the D-pad would strand focus; hand it to its twin.
            if (leftHadFocus && !moveLeft.isEnabled) moveRight.requestFocus()
            if (rightHadFocus && !moveRight.isEnabled) moveLeft.requestFocus()
        }

        fun move(delta: Int) {
            repository.move(fav.url, delta)
            // The grid behind the dialog follows each press, and focus rides along on the
            // moved card so it is there when the dialog closes, whichever way it closes.
            onChanged(fav.url)
            syncMoveButtons()
        }
        moveLeft.setOnClickListener { move(-1) }
        moveRight.setOnClickListener { move(1) }
        syncMoveButtons()

        val pad = (16 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(titleField)
            addView(urlField)
            addView(moveRow)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.edit_favorite)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val edited = Favorite(titleField.text.toString(), urlField.text.toString())
                repository.update(fav.url, edited)
                onChanged(edited.url)
            }
            .setNegativeButton(R.string.delete) { _, _ ->
                repository.remove(fav.url)
                onChanged(null)
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private const val DISABLED_ALPHA = 0.35f
}
