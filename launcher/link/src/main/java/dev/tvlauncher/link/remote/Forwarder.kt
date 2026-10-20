package dev.tvlauncher.link.remote

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import dev.tvlauncher.link.core.LinkContract
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hands a remote command to the launcher: an explicit, ordered broadcast to dev.tvlauncher that
 * only holders of [LinkContract.PERMISSION] can receive (and the launcher's receiver only accepts
 * senders holding it). The launcher answers with a [LinkContract.Outcome] result code: done
 * itself, passed to the Helper, or "needs the Helper". Waits at most [TIMEOUT_MS].
 */
object Forwarder {
    const val TIMEOUT_MS = 1500L

    private val thread = HandlerThread("link-results").apply { start() }
    private val handler = Handler(thread.looper)

    fun send(context: Context, fill: (Intent) -> Unit): Int {
        val i = Intent(LinkContract.ACTION_COMMAND).setPackage(LinkContract.LAUNCHER_PACKAGE)
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        fill(i)
        val result = AtomicInteger(LinkContract.Outcome.NONE)
        val done = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                result.set(resultCode)
                done.countDown()
            }
        }
        return try {
            context.sendOrderedBroadcast(i, LinkContract.PERMISSION, receiver, handler, Activity.RESULT_CANCELED, null, null)
            done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            result.get().let { if (it == Activity.RESULT_CANCELED) LinkContract.Outcome.NONE else it }
        } catch (e: Exception) {
            LinkContract.Outcome.NONE
        }
    }

    fun key(context: Context, key: LinkContract.Key) = send(context) {
        it.putExtra(LinkContract.EXTRA_KIND, LinkContract.KIND_KEY).putExtra(LinkContract.EXTRA_KEY, key.wire)
    }

    fun text(context: Context, text: String, enter: Boolean) = send(context) {
        it.putExtra(LinkContract.EXTRA_KIND, if (enter) LinkContract.KIND_ENTER else LinkContract.KIND_TEXT)
            .putExtra(LinkContract.EXTRA_TEXT, text)
    }

    fun openUrl(context: Context, url: String) = send(context) {
        it.putExtra(LinkContract.EXTRA_KIND, LinkContract.KIND_OPEN_URL).putExtra(LinkContract.EXTRA_URL, url)
    }

    fun launch(context: Context, pkg: String) = send(context) {
        it.putExtra(LinkContract.EXTRA_KIND, LinkContract.KIND_LAUNCH).putExtra(LinkContract.EXTRA_PACKAGE, pkg)
    }
}
