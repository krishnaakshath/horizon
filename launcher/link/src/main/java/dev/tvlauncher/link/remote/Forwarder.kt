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

    fun send(context: Context, fill: (Intent) -> Unit): Int = sendForExtras(context, fill).first

    /** Like [send], also returning the launcher's result extras (null when none). */
    fun sendForExtras(context: Context, fill: (Intent) -> Unit): Pair<Int, android.os.Bundle?> {
        val i = Intent(LinkContract.ACTION_COMMAND).setPackage(LinkContract.LAUNCHER_PACKAGE)
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        fill(i)
        val result = AtomicInteger(LinkContract.Outcome.NONE)
        val extras = java.util.concurrent.atomic.AtomicReference<android.os.Bundle?>(null)
        val done = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                result.set(resultCode)
                extras.set(getResultExtras(false))
                done.countDown()
            }
        }
        return try {
            context.sendOrderedBroadcast(i, LinkContract.PERMISSION, receiver, handler, Activity.RESULT_CANCELED, null, null)
            done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            result.get().let { if (it == Activity.RESULT_CANCELED) LinkContract.Outcome.NONE else it } to extras.get()
        } catch (e: Exception) {
            LinkContract.Outcome.NONE to null
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

    fun media(context: Context, action: String) = send(context) {
        it.putExtra(LinkContract.EXTRA_KIND, LinkContract.KIND_MEDIA).putExtra(LinkContract.EXTRA_ACTION, action)
    }

    fun nowPlaying(context: Context): android.os.Bundle? = sendForExtras(context) {
        it.putExtra(LinkContract.EXTRA_KIND, LinkContract.KIND_NOW_PLAYING)
    }.second

    fun powerMenu(context: Context) = send(context) { it.putExtra(LinkContract.EXTRA_KIND, LinkContract.KIND_POWER_MENU) }

    fun quick(context: Context, item: String) = send(context) {
        it.putExtra(LinkContract.EXTRA_KIND, LinkContract.KIND_QUICK).putExtra(LinkContract.EXTRA_ITEM, item)
    }
}
