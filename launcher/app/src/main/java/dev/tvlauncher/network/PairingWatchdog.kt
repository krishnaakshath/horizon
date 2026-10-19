package dev.tvlauncher.network

/**
 * Per-address timeout for a pairing attempt so "Pairing…" can never stay on screen forever.
 * Final bond states cancel it, a BONDING broadcast restarts it, [stop] cancels everything.
 * The scheduler is injected so the logic is JVM-testable.
 */
class PairingWatchdog(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val scheduler: Scheduler,
    private val onTimeout: (String) -> Unit
) {
    fun interface Cancelable { fun cancel() }
    interface Scheduler { fun postDelayed(delayMs: Long, r: Runnable): Cancelable }

    private val pending = HashMap<String, Cancelable>()
    val activeCount: Int get() = pending.size

    fun start(address: String) {
        pending.remove(address)?.cancel()
        pending[address] = scheduler.postDelayed(timeoutMs) {
            pending.remove(address)
            onTimeout(address)
        }
    }

    fun onBondState(address: String, bondState: Int) {
        if (bondState == NetworkPanelState.BOND_BONDING) start(address) else pending.remove(address)?.cancel()
    }

    fun stop() {
        for (c in pending.values) c.cancel()
        pending.clear()
    }

    companion object { const val DEFAULT_TIMEOUT_MS = 60_000L }
}
