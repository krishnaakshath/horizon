package dev.tvlauncher.browser.ui

import android.content.Context
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import dev.tvlauncher.browser.net.FaviconLoader
import dev.tvlauncher.browser.net.IconColors
import dev.tvlauncher.browser.net.UrlPolicy
import dev.tvlauncher.browser.ui.Theme.style

/**
 * Rounded icon well: a generated letter tile (stable color per host) that cross-fades to the
 * site's own icon once [FaviconLoader] has it. Transparent favicons sit on a white plate.
 */
class SiteIcon(context: Context, private val sizeDp: Int) : FrameLayout(context) {
    private val letter = TextView(context).apply {
        style(sizeDp * 0.42f, Theme.medium, Theme.TEXT)
        gravity = Gravity.CENTER
    }
    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0f
    }
    var color: Int? = null; private set
    private var bound: String? = null

    init {
        val r = context.dpf(sizeDp * 0.27f)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, r)
        }
        clipToOutline = true
        addView(letter, LayoutParams(-1, -1))
        addView(image, LayoutParams(-1, -1))
    }

    /** Binds to [url]; [onColor] receives the icon's dominant color (or the letter color). */
    fun bind(url: String, loader: FaviconLoader, onColor: ((Int) -> Unit)? = null) {
        bound = url
        val host = UrlPolicy.hostOf(url)
        val lc = IconColors.letterColor(host)
        letter.text = IconColors.letterFor(host).toString()
        letter.background = Theme.rounded(context, lc, 0)
        image.alpha = 0f
        image.setImageDrawable(null)
        color = lc
        onColor?.invoke(lc)
        val cached = loader.peek(url)
        if (cached != null) show(cached, false, onColor) else loader.load(url) { icon ->
            if (icon != null && bound == url) show(icon, true, onColor)
        }
    }

    private fun show(icon: FaviconLoader.Icon, fade: Boolean, onColor: ((Int) -> Unit)?) {
        image.setImageBitmap(icon.bitmap)
        val pad = if (icon.fullBleed) 0 else context.dp((sizeDp * 0.14f).toInt())
        image.setPadding(pad, pad, pad, pad)
        image.setBackgroundColor(if (icon.fullBleed) 0 else Theme.TEXT)
        if (fade) image.animate().alpha(1f).setDuration(180).start() else image.alpha = 1f
        icon.color?.let { color = it; onColor?.invoke(it) }
    }
}
