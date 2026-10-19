package dev.tvlauncher.ui

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.ui.Theme.style

/**
 * A 16:9 card with its label underneath. Banners fill the card; other apps get their icon
 * centered on a card tinted with the brand [accent]. Focus: scale, ring, deeper shadow, full
 * alpha and a bright label, all via ViewPropertyAnimator.
 */
class TileView(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = v * density
    private fun dpi(v: Int) = (v * density + 0.5f).toInt()

    /** Holds everything; the cold-start reveal animates this so focus animation stays independent. */
    val content = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    private val tint = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Theme.SURFACE, Theme.BASE))
    private val card = FrameLayout(context).apply {
        background = tint
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(TileSpec.CORNER_DP))
            }
        }
        clipToOutline = true
        elevation = dp(TileSpec.REST_ELEVATION_DP)
    }
    private val bannerView = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val iconBox = FrameLayout(context).apply {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.24f)
            }
        }
        clipToOutline = true
    }
    // Adaptive layers are 108/72 of the visible icon; oversize them so the box shows the safe zone.
    private val bgView = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val fgView = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val legacyView = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val letterView = TextView(context).apply {
        gravity = Gravity.CENTER
        style(40f, Theme.medium)
    }
    private val ring = View(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(TileSpec.CORNER_DP + TileSpec.RING_OFFSET_DP)
            setStroke(dpi(TileSpec.RING_STROKE_DP), Theme.TEXT)
        }
        alpha = 0f
    }
    private val labelView = TextView(context).apply {
        maxLines = TileSpec.LABEL_MAX_LINES
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        style(TileSpec.LABEL_SP.toFloat(), Theme.medium, Theme.TEXT_2)
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private var parallax = false
    private var effectsFull = false

    init {
        isFocusable = true
        isClickable = true
        clipChildren = false
        clipToPadding = false
        layoutParams = LayoutParams(dpi(TileSpec.VIEW_WIDTH_DP), dpi(TileSpec.VIEW_HEIGHT_DP))
        val pad = dpi(TileSpec.PAD_DP)
        val iconOuter = dpi(TileSpec.ICON_DP * 108 / 72)
        iconBox.addView(bgView, LayoutParams(iconOuter, iconOuter, Gravity.CENTER))
        iconBox.addView(fgView, LayoutParams(iconOuter, iconOuter, Gravity.CENTER))
        card.addView(bannerView, LayoutParams(-1, -1))
        card.addView(iconBox, LayoutParams(dpi(TileSpec.ICON_DP), dpi(TileSpec.ICON_DP), Gravity.CENTER))
        card.addView(legacyView, LayoutParams(dpi(TileSpec.ICON_DP), dpi(TileSpec.ICON_DP), Gravity.CENTER))
        card.addView(letterView, LayoutParams(-1, -1))
        content.addView(card, LayoutParams(dpi(TileSpec.WIDTH_DP), dpi(TileSpec.HEIGHT_DP)).apply {
            leftMargin = pad; topMargin = pad
        })
        val ro = dpi(TileSpec.RING_OFFSET_DP)
        content.addView(ring, LayoutParams(dpi(TileSpec.WIDTH_DP) + 2 * ro, dpi(TileSpec.HEIGHT_DP) + 2 * ro).apply {
            leftMargin = pad - ro; topMargin = pad - ro
        })
        content.addView(labelView, LayoutParams(dpi(TileSpec.WIDTH_DP), dpi(TileSpec.LABEL_HEIGHT_DP)).apply {
            leftMargin = pad; topMargin = pad + dpi(TileSpec.HEIGHT_DP + TileSpec.LABEL_GAP_DP)
        })
        addView(content, LayoutParams(-1, -1))
        outlineProvider = null
        alpha = TileSpec.REST_ALPHA
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        pivotY = dp(TileSpec.PAD_DP) + dp(TileSpec.HEIGHT_DP) / 2f
    }

    fun bind(app: AppEntry, art: TileArt<Drawable>, profile: DeviceProfile, accent: Int) {
        animate().cancel()
        card.animate().cancel()
        ring.animate().cancel()
        bgView.animate().cancel()
        fgView.animate().cancel()
        scaleX = 1f
        scaleY = 1f
        alpha = TileSpec.REST_ALPHA
        translationZ = 0f
        card.translationZ = 0f
        ring.alpha = 0f
        labelView.setTextColor(Theme.TEXT_2)
        setLayerType(LAYER_TYPE_NONE, null)
        labelView.text = app.label
        effectsFull = profile.effectsMode == EffectsMode.FULL
        parallax = effectsFull || TileSpec.PARALLAX_IN_LITE
        bgView.translationX = 0f; fgView.translationX = 0f
        bgView.translationY = 0f; fgView.translationY = 0f
        tint.colors = intArrayOf(
            BrandColors.blend(Theme.BASE, accent, 0.92f),
            BrandColors.blend(Theme.BASE, accent, 0.5f)
        )
        if (effectsFull && Build.VERSION.SDK_INT >= 28) {
            card.outlineSpotShadowColor = accent
            card.outlineAmbientShadowColor = accent
        }
        bannerView.setImageDrawable(null)
        bgView.setImageDrawable(null)
        fgView.setImageDrawable(null)
        legacyView.setImageDrawable(null)
        bannerView.visibility = View.GONE
        iconBox.visibility = View.GONE
        legacyView.visibility = View.GONE
        letterView.visibility = View.GONE
        when (art) {
            is TileArt.Banner -> {
                bannerView.setImageDrawable(art.d)
                bannerView.visibility = View.VISIBLE
            }
            is TileArt.Adaptive -> {
                bgView.setImageDrawable(art.bg)
                fgView.setImageDrawable(art.fg)
                iconBox.visibility = View.VISIBLE
            }
            is TileArt.Legacy -> {
                legacyView.setImageDrawable(art.d)
                legacyView.visibility = View.VISIBLE
            }
            is TileArt.Letter -> {
                letterView.text = art.letter.toString()
                letterView.visibility = View.VISIBLE
            }
        }
        if (art !is TileArt.Adaptive) parallax = false
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateFocus(gainFocus)
    }

    private fun animateFocus(focused: Boolean) {
        val s = if (focused) TileSpec.FOCUS_SCALE else 1f
        val z = dp(TileSpec.FOCUS_ELEVATION_DP) - dp(TileSpec.REST_ELEVATION_DP)
        animate().cancel()
        if (effectsFull) setLayerType(LAYER_TYPE_HARDWARE, null)
        labelView.setTextColor(if (focused) Theme.TEXT else Theme.TEXT_2)
        animate()
            .scaleX(s).scaleY(s)
            .translationZ(if (focused) z else 0f)
            .alpha(if (focused) TileSpec.FOCUS_ALPHA else TileSpec.REST_ALPHA)
            .setDuration(TileSpec.FOCUS_MS)
            .withEndAction { if (effectsFull) setLayerType(LAYER_TYPE_NONE, null) }
            .start()
        card.animate().translationZ(if (focused) z else 0f).setDuration(TileSpec.FOCUS_MS).start()
        ring.animate().alpha(if (focused) 1f else 0f).setDuration(TileSpec.FOCUS_MS).start()
        if (parallax) {
            bgView.animate().cancel()
            fgView.animate().cancel()
            val bg = if (focused) dp(TileSpec.PARALLAX_BG_DP) else 0f
            val fg = if (focused) -dp(TileSpec.PARALLAX_FG_DP) else 0f
            bgView.animate().translationX(bg).translationY(bg).setDuration(TileSpec.FOCUS_MS).start()
            fgView.animate().translationX(fg).translationY(fg).setDuration(TileSpec.FOCUS_MS).start()
        }
    }
}
