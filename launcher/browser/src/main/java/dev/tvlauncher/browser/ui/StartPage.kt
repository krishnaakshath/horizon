package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.data.Bookmark
import dev.tvlauncher.browser.data.Visit
import dev.tvlauncher.browser.net.FaviconLoader
import dev.tvlauncher.browser.net.IconColors
import dev.tvlauncher.browser.net.UrlPolicy
import dev.tvlauncher.browser.ui.Theme.style

/**
 * Native start page: brand + glass hero search, "Favorites" (first 12 bookmarks, 4 x 3, real
 * site icons) and "Recently visited". Long-press a favorite to edit it.
 */
class StartPage(
    context: Context,
    private val loader: FaviconLoader,
    private val topInsetDp: Int,
    private val onSearch: () -> Unit,
    private val onOpen: (String) -> Unit,
    private val onTileMenu: (Bookmark, Int) -> Unit
) : ScrollView(context) {
    private val grid = GridLayout(context).apply { columnCount = 4; clipChildren = false; clipToPadding = false }
    private val recentRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
    private val recentEmpty = TextView(context).apply { style(13f, Theme.regular, Theme.TEXT_3); text = "Pages you visit will show up here." }
    private val engineChip = TextView(context)
    val hero: Card
    val tiles = ArrayList<Card>()

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        clipChildren = false
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF121726.toInt(), Theme.BASE))
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(context.dp(Theme.MARGIN_H), context.dp(topInsetDp + 10), context.dp(Theme.MARGIN_H), context.dp(24))
        }
        // Brand + hero search row.
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        top.addView(ImageView(context).apply { setImageResource(R.drawable.brand_mark) }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
        top.addView(ImageView(context).apply { setImageResource(R.drawable.brand_wordmark); scaleType = ImageView.ScaleType.FIT_START },
            LinearLayout.LayoutParams(context.dp(108), context.dp(20)).apply { marginStart = context.dp(12) })
        hero = Card(context, Theme.RADIUS_PILL, rest = 0x1AFFFFFF, on = 0x2EFFFFFF, lift = 1.02f, restStroke = 0x33FFFFFF).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(20), 0, context.dp(10), 0)
            setOnClickListener { onSearch() }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_search); imageTintList = ColorStateList.valueOf(Theme.TEXT)
            }, LinearLayout.LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(14) })
            addView(TextView(context).apply { style(17f, Theme.regular, Theme.TEXT_2); text = "Search or type a web address" },
                LinearLayout.LayoutParams(0, -2, 1f))
            engineChip.style(12f, Theme.medium, Theme.TEXT_2)
            engineChip.background = Theme.rounded(context, 0x14FFFFFF, Theme.RADIUS_PILL)
            engineChip.setPadding(context.dp(12), context.dp(7), context.dp(12), context.dp(7))
            addView(engineChip)
        }
        top.addView(android.widget.Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(hero, LinearLayout.LayoutParams(context.dp(560), context.dp(56)))
        col.addView(top, LinearLayout.LayoutParams(-1, -2))

        col.addView(label("Favorites"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(22); bottomMargin = context.dp(10) })
        col.addView(grid, LinearLayout.LayoutParams(-2, -2))
        col.addView(label("Recently visited"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(20); bottomMargin = context.dp(10) })
        val recentBox = FrameLayout(context).apply { clipChildren = false }
        recentBox.addView(recentRow)
        recentBox.addView(recentEmpty)
        col.addView(recentBox, LinearLayout.LayoutParams(-1, -2))
        addView(col, LayoutParams(-1, -2))
    }

    private fun label(t: String) = TextView(context).apply {
        style(13f, Theme.medium, Theme.TEXT_2); text = t.uppercase(); letterSpacing = 0.1f
    }

    fun setEngine(label: String) { engineChip.text = label }

    fun bind(favorites: List<Bookmark>, recent: List<Visit>) {
        grid.removeAllViews(); tiles.clear()
        val gap = context.dp(14)
        favorites.take(12).forEachIndexed { i, b ->
            val t = tile(b, i)
            tiles.add(t)
            grid.addView(t, GridLayout.LayoutParams().apply {
                width = context.dp(206); height = context.dp(62)
                if (i % 4 != 0) leftMargin = gap
                if (i >= 4) topMargin = context.dp(12)
            })
        }
        recentRow.removeAllViews()
        recent.take(5).forEachIndexed { i, v ->
            val chip = Card(context, 16, lift = 1.04f).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(10), 0, context.dp(12), 0)
                setOnClickListener { onOpen(v.url) }
                val icon = SiteIcon(context, 26).also { it.bind(v.url, loader) }
                addView(icon, LinearLayout.LayoutParams(context.dp(26), context.dp(26)))
                addView(TextView(context).apply {
                    style(13f, Theme.medium); text = v.title.ifBlank { UrlPolicy.hostOf(v.url) }; isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(10) })
            }
            recentRow.addView(chip, LinearLayout.LayoutParams(context.dp(164), context.dp(48)).apply { if (i > 0) marginStart = context.dp(14) })
        }
        recentEmpty.visibility = if (recent.isEmpty()) VISIBLE else GONE
    }

    private fun tile(b: Bookmark, index: Int): Card {
        val card = Card(context)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(context.dp(10), 0, context.dp(10), 0)
        card.contentDescription = b.title
        card.setOnClickListener { onOpen(b.url) }
        card.setOnLongClickListener { onTileMenu(b, index); true }
        val icon = SiteIcon(context, 42)
        card.addView(icon, LinearLayout.LayoutParams(context.dp(42), context.dp(42)))
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            style(15f, Theme.medium); text = b.title; isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        texts.addView(TextView(context).apply {
            style(11.5f, Theme.regular, Theme.TEXT_2); text = UrlPolicy.hostOf(b.url); isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(4) })
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(12) })
        icon.bind(b.url, loader) { c ->
            card.colors = intArrayOf(IconColors.blend(Theme.SURFACE, c, 0.30f), Theme.SURFACE)
        }
        return card
    }
}
