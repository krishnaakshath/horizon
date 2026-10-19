package dev.tvlauncher.weather

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class WeatherSettingsActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val refreshIo = Executors.newSingleThreadExecutor()
    private val searchIo = Executors.newSingleThreadExecutor()
    private lateinit var store: WeatherStore

    private lateinit var main: LinearLayout
    private lateinit var searchPane: LinearLayout
    private lateinit var locationText: TextView
    private lateinit var tempText: TextView
    private lateinit var conditionText: TextView
    private lateinit var rangeText: TextView
    private lateinit var updatedText: TextView
    private lateinit var statusText: TextView
    private lateinit var bgText: TextView
    private lateinit var cityValue: TextView
    private lateinit var unitValue: TextView
    private lateinit var searchField: EditText
    private lateinit var results: LinearLayout
    private lateinit var searchStatus: TextView
    private lateinit var cityRow: View

    private var searchRunnable: Runnable? = null
    private var searchSeq = 0
    private var inSearch = false
    @Volatile private var destroyed = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dpf(v: Int) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WeatherStore(this)
        buildUi()
        render()
        RefreshScheduler.schedulePeriodic(this)
        if (store.load().place != null) refreshNow(silent = true)
    }

    override fun onDestroy() {
        destroyed = true
        searchRunnable?.let { ui.removeCallbacks(it) }
        refreshIo.shutdownNow()
        searchIo.shutdownNow()
        super.onDestroy()
    }

    // ---- UI construction -------------------------------------------------

    private fun text(size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    private fun round(color: Int, r: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dpf(r)
        if (stroke > 0) setStroke(dp(stroke), strokeColor)
    }

    private fun focusBg() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused),
            round(WeatherBrand.SURFACE_FOCUS, 16, 3, WeatherBrand.SKY))
        addState(intArrayOf(), round(WeatherBrand.SURFACE, 16, 3, Color.TRANSPARENT))
    }

    private fun row(title: String, onClick: () -> Unit): Pair<LinearLayout, TextView> {
        val value = text(18, WeatherBrand.TEXT_DIM).apply { gravity = Gravity.END }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            background = focusBg()
            setPadding(dp(24), dp(18), dp(24), dp(18))
            addView(text(20, WeatherBrand.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            (getChildAt(0) as TextView).text = title
            addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { onClick() }
        }
        return r to value
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            setBackgroundColor(WeatherBrand.BG)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(dp(680), ViewGroup.LayoutParams.MATCH_PARENT))

        // Header with the brand mark
        column.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply { setImageResource(R.drawable.logo_mark) },
                LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(text(26, WeatherBrand.TEXT, true).apply { text = getString(R.string.app_name); setPadding(dp(14), 0, 0, 0) })
        }, lp())

        main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(main, lp(20))

        // Weather card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(WeatherBrand.SURFACE, 20)
            setPadding(dp(28), dp(24), dp(28), dp(24))
        }
        locationText = text(20, WeatherBrand.TEXT_DIM)
        tempText = text(72, WeatherBrand.TEXT, true)
        conditionText = text(22, WeatherBrand.TEXT)
        rangeText = text(16, WeatherBrand.TEXT_DIM)
        updatedText = text(14, WeatherBrand.TEXT_DIM)
        statusText = text(15, WeatherBrand.ERROR)
        bgText = text(14, WeatherBrand.TEXT_DIM)
        card.addView(locationText)
        card.addView(tempText, lp(8))
        card.addView(conditionText, lp(4))
        card.addView(rangeText, lp(8))
        card.addView(updatedText, lp(12))
        card.addView(statusText, lp(6))
        card.addView(bgText, lp(6))
        main.addView(card, lp())

        val (city, cityV) = row("Change city") { enterSearch() }
        cityRow = city; cityValue = cityV
        main.addView(city, lp(16))
        val (unit, unitV) = row("Temperature unit") { toggleUnit() }
        unitValue = unitV
        main.addView(unit, lp(10))
        val (refresh, _) = row("Refresh now") { refreshNow(silent = false) }
        main.addView(refresh, lp(10))
        main.addView(text(14, WeatherBrand.TEXT_DIM).apply {
            text = "Only the city's coordinates are sent to Open-Meteo. No account. No tracking."
            setPadding(dp(8), 0, dp(8), 0)
        }, lp(20))

        // Search pane
        searchPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        searchField = EditText(this).apply {
            hint = "Search for a city"
            setHintTextColor(WeatherBrand.TEXT_DIM)
            setTextColor(WeatherBrand.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(OpenMeteoClient.MAX_QUERY_CHARS))
            background = focusBg()
            setPadding(dp(24), dp(18), dp(24), dp(18))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = scheduleSearch(s?.toString().orEmpty(), 400)
            })
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    scheduleSearch(text.toString(), 0); true
                } else false
            }
        }
        searchPane.addView(searchField, lp())
        searchStatus = text(15, WeatherBrand.TEXT_DIM).apply { setPadding(dp(8), 0, dp(8), 0) }
        searchPane.addView(searchStatus, lp(12))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchPane.addView(results, lp(8))
        column.addView(searchPane, lp(20))

        setContentView(root)
        cityRow.requestFocus()
    }

    // ---- State rendering -------------------------------------------------

    private fun render() {
        val s = store.load()
        val snap = s.snapshot
        cityValue.text = s.place?.label() ?: "Not set"
        unitValue.text = if (s.unit == Units.F) "°F" else "°C"
        bgText.text = if (store.scheduleFailed()) "Background refresh unavailable" else ""
        locationText.text = s.place?.name ?: "No city selected"
        if (snap == null) {
            tempText.text = "--"
            conditionText.text = if (s.place == null) "Choose a city to see the weather" else "Waiting for first update"
            rangeText.text = ""
            updatedText.text = ""
        } else {
            tempText.text = Units.format(snap.tempC, s.unit)
            conditionText.text = WmoCodes.text(snap.code)
            val hl = if (snap.highC != null && snap.lowC != null)
                "H ${Units.format(snap.highC, s.unit)}   L ${Units.format(snap.lowC, s.unit)}   " else ""
            rangeText.text = hl + "Feels ${Units.format(snap.feelsC, s.unit)}"
            updatedText.text = "Updated " + TimeAgo.format(System.currentTimeMillis(), snap.updatedMs)
        }
    }

    private fun toggleUnit() {
        val next = if (store.load().unit == Units.F) Units.C else Units.F
        store.setUnit(next)
        render()
    }

    private fun refreshNow(silent: Boolean) {
        if (store.load().place == null) { statusText.text = "Choose a city first"; return }
        if (!silent) { statusText.setTextColor(WeatherBrand.TEXT_DIM); statusText.text = "Updating..." }
        refreshIo.execute {
            val ok = try { Refresher.refresh(applicationContext) } catch (e: Exception) { false }
            if (!destroyed) ui.post {
                statusText.setTextColor(WeatherBrand.ERROR)
                statusText.text = if (ok) "" else "Can't reach the weather service"
                render()
            }
        }
    }

    // ---- City search -----------------------------------------------------

    private fun enterSearch() {
        inSearch = true
        main.visibility = View.GONE
        searchPane.visibility = View.VISIBLE
        searchField.setText("")
        results.removeAllViews()
        searchStatus.text = "Type a city name"
        searchField.requestFocus()
    }

    private fun exitSearch() {
        inSearch = false
        searchRunnable?.let { ui.removeCallbacks(it) }
        searchSeq++
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(searchField.windowToken, 0)
        searchPane.visibility = View.GONE
        main.visibility = View.VISIBLE
        cityRow.requestFocus()
        render()
    }

    private fun scheduleSearch(query: String, delayMs: Long) {
        searchRunnable?.let { ui.removeCallbacks(it) }
        val q = OpenMeteoClient.normalizeQuery(query)
        val seq = ++searchSeq
        if (q.length < 2) {
            results.removeAllViews()
            searchStatus.text = "Type a city name"
            return
        }
        val r = Runnable {
            searchStatus.text = "Searching..."
            searchIo.execute {
                val res = OpenMeteoClient.searchPlaces(q)
                if (!destroyed) ui.post { if (seq == searchSeq) showResults(res) }
            }
        }
        searchRunnable = r
        ui.postDelayed(r, delayMs)
    }

    private fun showResults(res: Result<List<Place>>) {
        results.removeAllViews()
        val places = res.getOrNull()
        if (places == null) { searchStatus.text = "Can't reach the weather service"; return }
        searchStatus.text = if (places.isEmpty()) "No matching city" else ""
        places.forEach { p ->
            val (r, _) = row(p.label()) { choose(p) }
            results.addView(r, lp(8))
        }
    }

    private fun choose(p: Place) {
        store.setPlace(p)
        RefreshScheduler.schedulePeriodic(this)
        exitSearch()
        refreshNow(silent = false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && inSearch) { exitSearch(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
