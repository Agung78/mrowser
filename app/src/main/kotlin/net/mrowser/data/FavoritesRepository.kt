package net.mrowser.data

interface FavoritesRepository {
    fun findAll(): List<Favorite>
    fun add(favorite: Favorite)
    fun remove(url: String)
    fun update(oldUrl: String, favorite: Favorite)
    fun move(url: String, delta: Int)
    /** Import: append what is new, keep what is there — see [FavoritesOps.merge]. */
    fun addAll(imported: List<Favorite>)
}
