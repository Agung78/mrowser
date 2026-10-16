package net.mrowser.data

/**
 * Pure: is the app that answers `ACTION_OPEN_DOCUMENT` a real picker?
 *
 * Android TV builds that ship no DocumentsUI still resolve the intent — to a framework stub that
 * shows "You don't have an app that can do this" and returns. Launching it can't throw
 * `ActivityNotFoundException`, so the stub has to be recognised by package, or the import row
 * would end in that generic Toast instead of saying what to do. Seen on the KTC EcoYTTV
 * (Android 14).
 */
object DocumentPicker {

    private val STUBS = setOf("com.android.tv.frameworkpackagestubs")

    fun isUsable(packageName: String?): Boolean = packageName != null && packageName !in STUBS
}
