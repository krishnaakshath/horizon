package dev.tvlauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode

class TileView(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = v * density

    private val bgView = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val fgView = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val letterView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
        typeface = Typeface.DEFAULT_BOLD
        visibility = View.GONE
    }
    private val labelView = TextView(context).apply {
        maxLines = TileSpec.LABEL_MAX_LINES
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TileSpec.LABEL_SP.toFloat())
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
        setPadding(dp(8).toInt(), dp(4).toInt(), dp(8).toInt(), dp(6).toInt())
        setBackgroundColor(0x66000000)
    }

    private var parallax = false
    private var effectsFull = false

    init {
        isFocusable = true
        isClickable = true
        layoutParams = LayoutParams(dp(TileSpec.WIDTH_DP).toInt(), dp(TileSpec.HEIGHT_DP).toInt())
        addView(bgView, LayoutParams(-1, -1))
        addView(fgView, LayoutParams(-1, -1))
        addView(letterView, LayoutParams(-1, -1))
        addView(labelView, LayoutParams(-1, -2, Gravity.BOTTOM))
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(TileSpec.CORNER_DP))
            }
        }
        clipToOutline = true
        elevation = dp(TileSpec.REST_ELEVATION_DP)
        alpha = TileSpec.REST_ALPHA
    }

    fun bind(app: AppEntry, art: TileArt<Drawable>, profile: DeviceProfile) {
        animate().cancel()
        bgView.animate().cancel()
        fgView.animate().cancel()
        scaleX = 1f
        scaleY = 1f
        alpha = TileSpec.REST_ALPHA
        translationZ = 0f
        setLayerType(LAYER_TYPE_NONE, null)
        labelView.text = app.label
        effectsFull = profile.effectsMode == EffectsMode.FULL
        parallax = effectsFull || TileSpec.PARALLAX_IN_LITE
        bgView.translationX = 0f; fgView.translationX = 0f
        bgView.translationY = 0f; fgView.translationY = 0f
        fgView.setImageDrawable(null)
        letterView.visibility = View.GONE
        when (art) {
            is TileArt.Banner -> bgView.setImageDrawable(art.d)
            is TileArt.Legacy -> bgView.setImageDrawable(art.d)
            is TileArt.Adaptive -> {
                bgView.setImageDrawable(art.bg)
                fgView.setImageDrawable(art.fg)
            }
            is TileArt.Letter -> {
                bgView.setImageDrawable(GradientDrawable().apply { setColor(art.color) })
                letterView.text = art.letter.toString()
                letterView.visibility = View.VISIBLE
            }
        }
        if (art !is TileArt.Adaptive) parallax = false
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateFocus(gainFocus)
    }

    private fun animateFocus(focused: Boolean) {
        val s = if (focused) TileSpec.FOCUS_SCALE else 1f
        val z = dp(if (focused) TileSpec.FOCUS_ELEVATION_DP else TileSpec.REST_ELEVATION_DP)
        animate().cancel()
        if (effectsFull) setLayerType(LAYER_TYPE_HARDWARE, null)
        animate()
            .scaleX(s).scaleY(s)
            .translationZ(if (focused) z - dp(TileSpec.REST_ELEVATION_DP) else 0f)
            .alpha(if (focused) TileSpec.FOCUS_ALPHA else TileSpec.REST_ALPHA)
            .setDuration(TileSpec.FOCUS_MS)
            .withEndAction { if (effectsFull) setLayerType(LAYER_TYPE_NONE, null) }
            .start()
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
