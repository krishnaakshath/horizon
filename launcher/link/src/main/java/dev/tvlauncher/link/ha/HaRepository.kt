package dev.tvlauncher.link.ha

import android.content.Context
import dev.tvlauncher.link.LinkStore
import dev.tvlauncher.link.core.LinkContract

/**
 * Process-wide Home Assistant state: the last fetched entities (memory only) and the connection
 * state. Fetches are coalesced: callers within [MIN_GAP_MS] of the last fetch get the cached
 * snapshot, so a panel and the phone page polling together cost one request.
 */
object HaRepository {
    const val MIN_GAP_MS = 2_000L

    @Volatile var state: String = LinkContract.HA_NOT_CONFIGURED
        private set
    @Volatile var updatedMs: Long = 0L
        private set
    @Volatile private var entities: List<HaEntity> = emptyList()
    private var lastFetchMs = 0L
    private val lock = Any()

    private fun client(store: LinkStore): HaClient? {
        val base = HaUrls.normalizeBase(store.haUrl) ?: return null
        if (!HaUrls.isValidToken(store.haToken)) return null
        return HaClient(base, store.haToken)
    }

    /** Fetches /api/states unless a fetch just happened. Blocking; call off the main thread. */
    fun refresh(context: Context, force: Boolean = false): String = synchronized(lock) {
        val store = LinkStore(context)
        val c = client(store) ?: run { state = LinkContract.HA_NOT_CONFIGURED; entities = emptyList(); return state }
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchMs in 0 until MIN_GAP_MS) return state
        lastFetchMs = now
        when (val r = c.states()) {
            is HaClient.Result.Ok -> { entities = r.value; state = LinkContract.HA_OK; updatedMs = now }
            is HaClient.Result.Failed -> state = r.state
        }
        state
    }

    fun all(): List<HaEntity> = entities

    /** The favourites in the owner's order, at most [LinkContract.MAX_HOME_ROWS]. */
    fun favourites(context: Context): List<HaEntity> {
        val byId = entities.associateBy { it.entityId }
        return LinkStore(context).haFavourites.mapNotNull { byId[it] }.take(LinkContract.MAX_HOME_ROWS)
    }

    /** Runs [action] on [entityId] (must be a favourite or a known entity); true when HA accepted it. */
    fun act(context: Context, entityId: String, action: String): Boolean {
        val store = LinkStore(context)
        val c = client(store) ?: return false
        val e = entities.firstOrNull { it.entityId == entityId } ?: return false
        val call = ServiceCall.forAction(e, action) ?: return false
        val ok = c.call(call) is HaClient.Result.Ok
        if (ok) synchronized(lock) { lastFetchMs = 0L } // next refresh shows the new state
        return ok
    }

    /** Saves a new connection and tests it. Returns an HA_* state. */
    fun configure(context: Context, url: String, token: String?): String {
        val store = LinkStore(context)
        val base = HaUrls.normalizeBase(url) ?: return LinkContract.HA_UNREACHABLE
        val t = token ?: store.haToken
        if (!HaUrls.isValidToken(t)) return LinkContract.HA_AUTH
        val probe = HaClient(base, t).probe()
        store.haUrl = base
        store.haToken = t
        synchronized(lock) { lastFetchMs = 0L }
        return when (probe) {
            is HaClient.Result.Ok -> refresh(context, force = true)
            is HaClient.Result.Failed -> probe.state.also { state = it }
        }
    }

    fun forget(context: Context) {
        val store = LinkStore(context)
        store.haUrl = ""; store.haToken = ""; store.haFavourites = emptyList()
        synchronized(lock) { entities = emptyList(); state = LinkContract.HA_NOT_CONFIGURED; lastFetchMs = 0L }
    }
}
