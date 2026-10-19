package dev.tvlauncher.watch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.TileSpec
import dev.tvlauncher.ui.dp

/**
 * The Continue watching row shown in the hero area: a heading and up to [WatchModel.MAX_CARDS]
 * cards (poster or brand-colour card, title, episode line, progress bar, app icon). Callbacks
 * report OK, long press and DOWN (back to the shelf). Thumbnails arrive later through [setThumb].
 */
class ContinueRow(context: Context) : LinearLayout(context) {
    interface Listener {
        fun onOpen(p: WatchProgram)
        fun onLongPress(p: WatchProgram)
        /** DOWN on a card: focus goes back to the shelf. */
        fun onDown(): Boolean
    }

    var listener: Listener? = null

    private val heading = TextView(context).apply {
        style(Theme.SP_LABEL + 1f, Theme.medium, Theme.TEXT_2)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }
    private val cards = LinearLayout(context).apply {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
        val p = context.dp(FOCUS_ROOM_DP)
        setPadding(p, p, p, p)
    }
    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(cards)
    }
    private val thumbs = HashMap<String, ImageView>()
    var shown: List<WatchProgram> = emptyList()
        private set

    override fun hasOverlappingRendering() = false

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        addView(heading, LayoutParams(-1, -2).apply { marginStart = context.dp(FOCUS_ROOM_DP) })
        addView(scroll, LayoutParams(-1, -2))
    }

    /** Rebuilds the cards; [iconFor] gives the app icon, [accentFor] the brand colour, [thumb] a cached poster. */
    fun bind(
        title: String,
        programs: List<WatchProgram>,
        iconFor: (String) -> Drawable?,
        accentFor: (String) -> Int,
        thumb: (WatchProgram) -> Bitmap?
    ) {
        val keep = cards.hasFocus()
        val focusedKey = (cards.findFocus()?.tag as? WatchProgram)?.key
        heading.text = title
        shown = programs
        cards.removeAllViews()
        thumbs.clear()
        programs.forEachIndexed { i, p -> cards.addView(card(p, iconFor(p.packageName), accentFor(p.packageName), thumb(p)), cardParams(i)) }
        scroll.scrollTo(0, 0)
        if (keep) {
            val again = (0 until cards.childCount).map { cards.getChildAt(it) }.firstOrNull { (it.tag as? WatchProgram)?.key == focusedKey }
            (again ?: cards.getChildAt(0))?.requestFocus()
        }
    }

    fun setThumb(uri: String, b: Bitmap) {
        thumbs[uri]?.let { it.setImageBitmap(b); it.visibility = View.VISIBLE }
    }

    fun focusFirst(): Boolean = cards.getChildAt(0)?.requestFocus() ?: false

    fun hasCards() = cards.childCount > 0

    private fun cardParams(i: Int) = LayoutParams(context.dp(CARD_W_DP), context.dp(CARD_H_DP)).apply {
        if (i > 0) marginStart = context.dp(GAP_DP)
    }

    private fun card(p: WatchProgram, icon: Drawable?, accent: Int, bmp: Bitmap?): View {
        val radius = context.dp(TileSpec.CORNER_DP - 4).toFloat()
        val card = FrameLayout(context).apply {
            tag = p
            isFocusable = true
            isClickable = true
            isLongClickable = true
            contentDescription = listOfNotNull(p.title, WatchModel.episodeLine(p), WatchModel.remainingLabel(p)).joinToString(", ")
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(accent, BrandColors.blend(accent, Theme.BASE, 0.55f))).apply { cornerRadius = radius }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
            clipToOutline = true
        }
        val poster = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = if (bmp != null) View.VISIBLE else View.GONE
            bmp?.let { setImageBitmap(it) }
        }
        card.addView(poster, FrameLayout.LayoutParams(-1, -1))
        p.posterUri?.let { thumbs[it] = poster }
        // Bottom scrim so the title reads on any poster.
        card.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xD9000000.toInt(), 0x00000000))
        }, FrameLayout.LayoutParams(-1, context.dp(CARD_H_DP * 2 / 3), Gravity.BOTTOM))
        val light = true // text sits on the dark scrim
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            val h = context.dp(10)
            setPadding(h, 0, h, context.dp(9))
        }
        texts.addView(TextView(context).apply {
            text = p.title
            style(14f, Theme.semibold, if (light) Theme.TEXT else Theme.TEXT_DARK)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        })
        val line = listOfNotNull(WatchModel.episodeLine(p), WatchModel.remainingLabel(p)).joinToString(" · ")
        if (line.isNotEmpty()) texts.addView(TextView(context).apply {
            text = line
            style(11f, Theme.regular, Theme.TEXT_2)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        })
        card.addView(texts, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        if (icon != null) card.addView(ImageView(context).apply {
            setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(0x66000000) }
            val ip = context.dp(2)
            setPadding(ip, ip, ip, ip)
        }, FrameLayout.LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP), Gravity.TOP or Gravity.START).apply {
            topMargin = context.dp(8); marginStart = context.dp(8)
        })
        val f = WatchModel.progress(p.positionMs, p.durationMs)
        if (f != null && f < WatchModel.FINISHED) {
            card.addView(ProgressLine(context, f, accentOrWhite(accent)), FrameLayout.LayoutParams(-1, context.dp(3), Gravity.BOTTOM))
        }
        // Focus ring: an outline drawn over the card.
        val ring = View(context).apply {
            background = GradientDrawable().apply { cornerRadius = radius; setStroke(context.dp(2), Theme.TEXT) }
            visibility = View.INVISIBLE
        }
        card.addView(ring, FrameLayout.LayoutParams(-1, -1))
        card.setOnFocusChangeListener { v, has ->
            ring.visibility = if (has) View.VISIBLE else View.INVISIBLE
            val s = if (has) 1.06f else 1f
            v.animate().cancel()
            v.animate().scaleX(s).scaleY(s).setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
        }
        card.setOnClickListener { listener?.onOpen(p) }
        card.setOnLongClickListener { listener?.onLongPress(p); true }
        card.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && ev.action == KeyEvent.ACTION_DOWN) listener?.onDown() ?: false else false
        }
        return card
    }

    private fun accentOrWhite(accent: Int): Int =
        if (BrandColors.luminance(accent) < 0.08) Theme.TEXT else BrandColors.blend(accent, Theme.TEXT, 0.15f)

    /** A thin played-fraction bar along the card's bottom edge. */
    private class ProgressLine(context: Context, private val fraction: Float, color: Int) : View(context) {
        private val track = android.graphics.Paint().apply { this.color = 0x4DFFFFFF }
        private val fill = android.graphics.Paint().apply { this.color = color }
        override fun onDraw(canvas: android.graphics.Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), track)
            canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), fill)
        }
    }

    companion object {
        const val CARD_W_DP = 176
        const val CARD_H_DP = 99
        const val GAP_DP = 16
        const val ICON_DP = 24
        /** Room around the cards for the focus scale. */
        const val FOCUS_ROOM_DP = 8
        /** Heading + cards + focus room: the hero area must be at least this tall for the row. */
        const val HEIGHT_DP = 20 + CARD_H_DP + 2 * FOCUS_ROOM_DP + 4
    }
}
