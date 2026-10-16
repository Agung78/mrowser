package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesOpsTest {

    private val a = Favorite("A", "https://a.net")
    private val b = Favorite("B", "https://b.net")
    private val c = Favorite("C", "https://c.net")

    @Test fun `add puts a new favorite at the front`() {
        assertEquals(listOf(b, a), FavoritesOps.add(listOf(a), b))
    }

    @Test fun `add upserts by url without duplicating`() {
        val a2 = Favorite("A renamed", "https://a.net")
        assertEquals(listOf(a2), FavoritesOps.add(listOf(a), a2))
    }

    @Test fun `remove drops the matching url`() {
        assertEquals(listOf(a), FavoritesOps.remove(listOf(a, b), "https://b.net"))
    }

    @Test fun `update replaces the entry at the old url in place`() {
        val b2 = Favorite("B2", "https://b2.net")
        assertEquals(listOf(a, b2), FavoritesOps.update(listOf(a, b), "https://b.net", b2))
    }

    @Test fun `update is a no-op when the old url is absent`() {
        assertEquals(listOf(a), FavoritesOps.update(listOf(a), "https://x.net", b))
    }

    @Test fun `move left swaps a middle entry with its predecessor`() {
        assertEquals(listOf(b, a, c), FavoritesOps.move(listOf(a, b, c), "https://b.net", -1))
    }

    @Test fun `move right swaps a middle entry with its successor`() {
        assertEquals(listOf(a, c, b), FavoritesOps.move(listOf(a, b, c), "https://b.net", 1))
    }

    @Test fun `move left on the first entry is clamped`() {
        assertEquals(listOf(a, b, c), FavoritesOps.move(listOf(a, b, c), "https://a.net", -1))
    }

    @Test fun `move right on the last entry is clamped`() {
        assertEquals(listOf(a, b, c), FavoritesOps.move(listOf(a, b, c), "https://c.net", 1))
    }

    @Test fun `move right on the first entry advances it one step`() {
        assertEquals(listOf(b, a, c), FavoritesOps.move(listOf(a, b, c), "https://a.net", 1))
    }

    @Test fun `move left on the last entry retreats it one step`() {
        assertEquals(listOf(a, c, b), FavoritesOps.move(listOf(a, b, c), "https://c.net", -1))
    }

    @Test fun `move is a no-op when the url is absent`() {
        assertEquals(listOf(a, b), FavoritesOps.move(listOf(a, b), "https://x.net", 1))
    }

    @Test fun `move on a single entry is a no-op either way`() {
        assertEquals(listOf(a), FavoritesOps.move(listOf(a), "https://a.net", -1))
        assertEquals(listOf(a), FavoritesOps.move(listOf(a), "https://a.net", 1))
    }

    @Test fun `move on an empty list is a no-op`() {
        assertEquals(emptyList<Favorite>(), FavoritesOps.move(emptyList(), "https://a.net", 1))
    }

    @Test fun `canMove is false only past an end or for an absent url`() {
        val list = listOf(a, b, c)
        assertEquals(false, FavoritesOps.canMove(list, "https://a.net", -1))
        assertEquals(true, FavoritesOps.canMove(list, "https://a.net", 1))
        assertEquals(true, FavoritesOps.canMove(list, "https://b.net", -1))
        assertEquals(true, FavoritesOps.canMove(list, "https://b.net", 1))
        assertEquals(true, FavoritesOps.canMove(list, "https://c.net", -1))
        assertEquals(false, FavoritesOps.canMove(list, "https://c.net", 1))
        assertEquals(false, FavoritesOps.canMove(list, "https://x.net", 1))
    }

    @Test fun `canMove is false both ways for a single entry`() {
        assertEquals(false, FavoritesOps.canMove(listOf(a), "https://a.net", -1))
        assertEquals(false, FavoritesOps.canMove(listOf(a), "https://a.net", 1))
    }

    @Test fun `move clamps a delta past either end`() {
        assertEquals(listOf(b, c, a), FavoritesOps.move(listOf(a, b, c), "https://a.net", 5))
        assertEquals(listOf(c, a, b), FavoritesOps.move(listOf(a, b, c), "https://c.net", -5))
    }

    @Test fun `canMove agrees with the clamped move for a large delta`() {
        assertEquals(true, FavoritesOps.canMove(listOf(a, b, c), "https://a.net", 5))
        assertEquals(true, FavoritesOps.canMove(listOf(a, b, c), "https://c.net", -5))
        assertEquals(false, FavoritesOps.canMove(listOf(a, b, c), "https://c.net", 5))
        assertEquals(false, FavoritesOps.canMove(listOf(a, b, c), "https://b.net", 0))
    }
}
