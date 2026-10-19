package dev.tvlauncher.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingWatchdogTest {
    private class FakeScheduler : PairingWatchdog.Scheduler {
        var now = 0L
        private class Task(val at: Long, val r: Runnable, var cancelled: Boolean = false)
        private val tasks = ArrayList<Task>()
        override fun postDelayed(delayMs: Long, r: Runnable): PairingWatchdog.Cancelable {
            val t = Task(now + delayMs, r); tasks.add(t)
            return PairingWatchdog.Cancelable { t.cancelled = true }
        }
        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
                now = next.at; tasks.remove(next); next.r.run()
            }
            now = target
        }
    }

    private val fired = ArrayList<String>()
    private val sched = FakeScheduler()
    private val dog = PairingWatchdog(60_000, sched) { fired.add(it) }

    @Test fun timeout_flipsPairingToFailed() {
        dog.start("A")
        sched.advance(59_999); assertTrue(fired.isEmpty())
        sched.advance(1)
        assertEquals(listOf("A"), fired)
        assertEquals(PairState.FAILED, NetworkPanelState.pair(PairState.PAIRING, NetworkPanelState.BOND_NONE))
        assertEquals(0, dog.activeCount)
    }

    @Test fun bondBroadcast_cancelsWatchdog() {
        dog.start("A")
        dog.onBondState("A", NetworkPanelState.BOND_BONDED)
        sched.advance(120_000)
        assertTrue(fired.isEmpty())
    }

    @Test fun bondingBroadcast_restartsWatchdog() {
        dog.start("A")
        sched.advance(50_000)
        dog.onBondState("A", NetworkPanelState.BOND_BONDING)
        sched.advance(50_000); assertTrue(fired.isEmpty())
        sched.advance(10_000); assertEquals(listOf("A"), fired)
    }

    @Test fun stop_cancelsWatchdog() {
        dog.start("A"); dog.start("B")
        dog.stop()
        sched.advance(120_000)
        assertTrue(fired.isEmpty())
        assertEquals(0, dog.activeCount)
    }

    @Test fun secondPairing_restartsWatchdog() {
        dog.start("A")
        sched.advance(30_000)
        dog.start("A")
        sched.advance(40_000); assertTrue(fired.isEmpty())
        sched.advance(20_000); assertEquals(listOf("A"), fired)
    }

    @Test fun failedPair_canRetry() {
        dog.start("A")
        sched.advance(60_000)
        assertEquals(PairState.PAIRING, NetworkPanelState.pair(PairState.FAILED, NetworkPanelState.BOND_BONDING))
        dog.start("A")
        sched.advance(60_000)
        assertEquals(listOf("A", "A"), fired)
    }
}
