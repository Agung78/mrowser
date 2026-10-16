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
        val builder = AlertDialog.Builder(context)
        // The dialog theme's context, so the fields and the move pills pick up its styles.
        val ctx = builder.context
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val titleField = EditText(ctx).apply {
            setText(fav.title); hint = ctx.getString(R.string.title_hint)
        }
        val urlField = EditText(ctx).apply {
            setText(fav.url); hint = ctx.getString(R.string.url_hint)
        }
        // Built by hand, so given the dialog buttons' pill style explicitly.
        fun moveButton(text: Int) = Button(ctx, null, 0, R.style.Widget_Mrowser_DialogButton).apply {
            setText(text)
        }
        val moveLeft = moveButton(R.string.move_left)
        val moveRight = moveButton(R.string.move_right)
        val moveRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false // a focused pill scales up past the row's edge
            listOf(moveLeft, moveRight).forEachIndexed { i, button ->
                addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = dp(8)
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
            // The pill has no disabled state, so dim an end-of-list button by hand.
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

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // 26dp, the dialog's padding, minus a little room for the focused pill's scale-up.
            setPadding(dp(24), dp(12), dp(24), dp(4))
            clipChildren = false
            clipToPadding = false
            fun spaced(bottom: Int) = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(bottom) }
            addView(titleField, spaced(10))
            addView(urlField, spaced(10))
            addView(moveRow, spaced(4))
        }
        // Delete is the neutral button so it sits alone at the far left, away from Save.
        builder
            .setTitle(R.string.edit_favorite)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val edited = Favorite(titleField.text.toString(), urlField.text.toString())
                repository.update(fav.url, edited)
                onChanged(edited.url)
            }
            .setNeutralButton(R.string.delete) { _, _ ->
                repository.remove(fav.url)
                onChanged(null)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .also { DestructiveButton.apply(it, AlertDialog.BUTTON_NEUTRAL) }
    }

    private const val DISABLED_ALPHA = 0.35f
}
