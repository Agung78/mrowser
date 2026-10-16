package net.mrowser.data

/** Pure list operations on favorites, keyed by url. */
object FavoritesOps {

    /** Upsert by url; the new/updated entry goes to the front. */
    fun add(list: List<Favorite>, favorite: Favorite): List<Favorite> =
        listOf(favorite) + list.filterNot { it.url == favorite.url }

    fun remove(list: List<Favorite>, url: String): List<Favorite> =
        list.filterNot { it.url == url }

    /** Replace the entry at oldUrl, keeping its position; no-op if absent. */
    fun update(list: List<Favorite>, oldUrl: String, favorite: Favorite): List<Favorite> =
        list.map { if (it.url == oldUrl) favorite else it }

    /**
     * Shift the entry at url by delta places in list order (-1 = earlier, +1 = later),
     * clamped at both ends; no-op if absent.
     */
    fun move(list: List<Favorite>, url: String, delta: Int): List<Favorite> {
        val from = list.indexOfFirst { it.url == url }
        if (from < 0) return list
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Whether move(list, url, delta) would change anything — drives the dialog's button state. */
    fun canMove(list: List<Favorite>, url: String, delta: Int): Boolean {
        val from = list.indexOfFirst { it.url == url }
        return from >= 0 && (from + delta).coerceIn(0, list.lastIndex) != from
    }
}
