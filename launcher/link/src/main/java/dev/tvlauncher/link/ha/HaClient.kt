package dev.tvlauncher.link.ha

import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.net.SafeHttp
import java.io.IOException

/**
 * Home Assistant REST client. The transport is injectable so retries, headers and error mapping
 * are JVM-tested; production uses [SafeHttp].
 */
class HaClient(
    private val base: String,
    private val token: String,
    private val transport: Transport = Transport { m, u, h, b -> SafeHttp.request(m, u, h, b, maxBytes = MAX_STATES_BYTES) },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun interface Transport {
        fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?): SafeHttp.Response
    }

    /** Outcome of a call, mapped to the calm states the TV shows. */
    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        /** [state] is one of LinkContract.HA_UNREACHABLE / HA_AUTH. */
        data class Failed(val state: String, val status: Int? = null) : Result<Nothing>()
    }

    private fun headers(json: Boolean): Map<String, String> {
        val h = linkedMapOf("Authorization" to HaUrls.authHeader(token), "Accept" to "application/json")
        if (json) h["Content-Type"] = "application/json"
        return h
    }

    /** GET /api/states (retried on network errors and 5xx). */
    fun states(): Result<List<HaEntity>> {
        var attempt = 0
        while (true) {
            var status: Int? = null
            var networkError = false
            try {
                val r = transport.send("GET", HaUrls.states(base), headers(json = false), null)
                status = r.status
                when (r.status) {
                    200 -> return try { Result.Ok(HaParser.parseStates(r.text())) } catch (e: Exception) { Result.Failed(LinkContract.HA_UNREACHABLE, 200) }
                    401, 403 -> return Result.Failed(LinkContract.HA_AUTH, r.status)
                }
            } catch (e: SafeHttp.Refused) {
                return Result.Failed(LinkContract.HA_UNREACHABLE)
            } catch (e: IOException) {
                networkError = true
            }
            if (!HaRetry.shouldRetry(idempotent = true, attempt = attempt, status = status, networkError = networkError)) {
                return Result.Failed(LinkContract.HA_UNREACHABLE, status)
            }
            sleep(HaRetry.DELAYS_MS[attempt.coerceAtMost(HaRetry.DELAYS_MS.size - 1)])
            attempt++
        }
    }

    /** POST /api/services/... once (never retried). */
    fun call(c: ServiceCall): Result<Unit> {
        val url = HaUrls.service(base, c.domain, c.service) ?: return Result.Failed(LinkContract.HA_UNREACHABLE)
        return try {
            val r = transport.send("POST", url, headers(json = true), c.body.toString().toByteArray(Charsets.UTF_8))
            when (r.status) {
                in 200..299 -> Result.Ok(Unit)
                401, 403 -> Result.Failed(LinkContract.HA_AUTH, r.status)
                else -> Result.Failed(LinkContract.HA_UNREACHABLE, r.status)
            }
        } catch (e: IOException) {
            Result.Failed(LinkContract.HA_UNREACHABLE)
        }
    }

    /** GET /api/ (the "API running" probe) for "Test connection". */
    fun probe(): Result<Unit> = try {
        val r = transport.send("GET", HaUrls.api(base), headers(json = false), null)
        when (r.status) {
            200 -> Result.Ok(Unit)
            401, 403 -> Result.Failed(LinkContract.HA_AUTH, r.status)
            else -> Result.Failed(LinkContract.HA_UNREACHABLE, r.status)
        }
    } catch (e: IOException) {
        Result.Failed(LinkContract.HA_UNREACHABLE)
    }

    companion object {
        const val MAX_STATES_BYTES = 6 shl 20
    }
}
