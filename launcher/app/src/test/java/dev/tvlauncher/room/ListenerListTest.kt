package dev.tvlauncher.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerListTest {
    /** A reference the test clears by hand, standing in for a GC'd WeakReference. */
    private class FakeRef<T>(var value: T?) : ListenerList.Ref<T> {
        override fun get(): T? = value
    }

    private val refs = mutableMapOf<String, FakeRef<String>>()
    private fun list() = ListenerList<String> { l -> FakeRef(l).also { refs[l] = it } }

    @Test fun add_dispatch_remove() {
        val l = list()
        l.add("a"); l.add("b"); l.add("a")
        assertEquals(2, l.size)
        val seen = mutableListOf<String>()
        l.forEach { seen += it }
        assertEquals(listOf("a", "b"), seen)
        l.remove("a")
        seen.clear(); l.forEach { seen += it }
        assertEquals(listOf("b"), seen)
        l.remove("zzz")
        assertEquals(1, l.size)
    }

    @Test fun cleared_references_are_pruned_on_dispatch() {
        val l = list()
        l.add("a"); l.add("b"); l.add("c")
        refs["b"]!!.value = null // "collected"
        val seen = mutableListOf<String>()
        l.forEach { seen += it }
        assertEquals(listOf("a", "c"), seen)
        assertEquals(2, l.size)
    }

    @Test fun remove_during_dispatch_is_safe() {
        val l = list()
        l.add("a"); l.add("b"); l.add("c")
        val seen = mutableListOf<String>()
        l.forEach { seen += it; if (it == "a") { l.remove("b"); l.add("d") } }
        assertEquals(listOf("a", "c"), seen)
        seen.clear(); l.forEach { seen += it }
        assertEquals(listOf("a", "c", "d"), seen)
    }

    @Test fun default_uses_weak_references() {
        val l = ListenerList<Any>()
        val strong = Any()
        l.add(strong)
        var n = 0
        l.forEach { n++ }
        assertEquals(1, n)
        assertTrue(l.size <= 1)
    }
}
