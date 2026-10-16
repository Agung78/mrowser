package net.mrowser.home

import android.app.AlertDialog
import net.mrowser.R

/**
 * Marks one button of a showing dialog as destructive: red text at rest, an accent pill with
 * white text under the D-pad. The dialog theme styles every button alike and cannot single
 * one out, so the few call sites that delete or exit apply this after `show()`.
 */
object DestructiveButton {

    /** [which] is `AlertDialog.BUTTON_POSITIVE` / `_NEGATIVE` / `_NEUTRAL`. */
    fun apply(dialog: AlertDialog, which: Int) {
        val button = dialog.getButton(which) ?: return
        button.setBackgroundResource(R.drawable.dialog_button_destructive_bg)
        button.setTextColor(dialog.context.getColorStateList(R.color.dialog_button_destructive_text))
    }
}
