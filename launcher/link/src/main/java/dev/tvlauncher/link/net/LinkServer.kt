package dev.tvlauncher.link.net

import dev.tvlauncher.link.web.LinkApi
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * The phone page's server: plain blocking sockets, one request per connection.
 *
 * - The peer address is checked before a single byte is read; anything not on the home network
 *   is closed at once.
 * - At most [MAX_CONNECTIONS] connections are served at a time (extra ones are closed), each
 *   with a [SOCKET_TIMEOUT_MS] read timeout, by a small fixed pool.
 * - Request size limits are [HttpParser]'s and [LinkApi.maxBody].
 */
class LinkServer(private val port: Int, private val api: LinkApi) {
    private var socket: ServerSocket? = null
    private var acceptor: Thread? = null
    private val pool = Executors.newFixedThreadPool(WORKERS) { r -> Thread(r, "link-http").apply { isDaemon = true } }
    private val slots = Semaphore(MAX_CONNECTIONS)

    @Volatile var running = false
        private set

    @Synchronized
    fun start() {
        if (running) return
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(port), BACKLOG)
        socket = s
        running = true
        acceptor = Thread({ acceptLoop(s) }, "link-accept").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
        socket = null
        acceptor = null
        pool.shutdownNow()
        try { pool.awaitTermination(1, TimeUnit.SECONDS) } catch (e: InterruptedException) { }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val c = try { s.accept() } catch (e: SocketException) { break } catch (e: Exception) { continue }
            val peer = c.inetAddress?.address
            if (!IpPolicy.isLocal(peer) || !slots.tryAcquire()) { quietClose(c); continue }
            try {
                pool.execute { try { serve(c, peer!!) } finally { slots.release() } }
            } catch (e: Exception) {
                slots.release(); quietClose(c)
            }
        }
    }

    private fun serve(c: Socket, peer: ByteArray) {
        try {
            c.soTimeout = SOCKET_TIMEOUT_MS
            c.tcpNoDelay = true
            val input = BufferedInputStream(c.getInputStream())
            val out = BufferedOutputStream(c.getOutputStream())
            val response = try {
                val req = HttpParser.read(input, LinkApi::maxBody)
                try { api.handle(req, peer) to (req.method == "HEAD") } catch (e: Exception) { HttpResponse.error(500, "server") to false }
            } catch (e: HttpError) {
                HttpResponse.error(e.status, "request") to false
            }
            response.first.write(out, head = response.second)
        } catch (e: Exception) {
            // Timeout or a closed connection: nothing to answer.
        } finally {
            quietClose(c)
        }
    }

    private fun quietClose(c: Socket) = try { c.close() } catch (e: Exception) { }

    companion object {
        const val MAX_CONNECTIONS = 12
        const val WORKERS = 8
        const val BACKLOG = 16
        const val SOCKET_TIMEOUT_MS = 10_000
    }
}
