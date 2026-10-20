package dev.tvlauncher.ambient.widgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.ui.Theme
import java.io.File
import java.util.TimeZone
import java.util.concurrent.Executors

/** Reads and writes [AmbientWidgetSettings] in the per-profile "ambient_widgets" store. */
class AmbientWidgetsStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): AmbientWidgetSettings = try {
        AmbientWidgetSettings(
            calendar = sp.getBoolean("calendar", false),
            photos = sp.getBoolean("photos", false),
            albumArt = sp.getBoolean("albumArt", false),
            photoSeconds = sp.getInt("photoSeconds", 60).takeIf { it in AmbientWidgetsModel.PHOTO_SECONDS } ?: 60
        )
    } catch (e: Exception) {
        AmbientWidgetSettings()
    }

    fun save(s: AmbientWidgetSettings) {
        try {
            sp.edit().putBoolean("calendar", s.calendar).putBoolean("photos", s.photos)
                .putBoolean("albumArt", s.albumArt).putInt("photoSeconds", s.photoSeconds).apply()
        } catch (e: Exception) {
        }
    }

    companion object {
        const val FILE = "ambient_widgets"

        fun calendarGranted(context: Context): Boolean = try {
            Build.VERSION.SDK_INT < 23 ||
                context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }
}

/**
 * The ambient screen's optional widgets: a calendar card (next three events), a photo frame from
 * Pictures and USB sticks with an EXIF date caption, and album art as the backdrop while music
 * plays (fed by the Now Playing card's art). [backdrop] goes right above the wallpaper (under the
 * scrims and the dim overlay, so night dim applies); [calendar] goes into the content layer, so it
 * moves with the burn-in drift. LITE: no cross-fades, smaller decode.
 */
class AmbientWidgets(context: Context, private val lite: Boolean) {
    private val app = context.applicationContext
    private val density = context.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private var settings = AmbientWidgetsStore(app).load()
    private var gen = 0

    val backdrop = FrameLayout(context)
    private val image = ImageView(context)
    private val caption = TextView(context)
    val calendar = LinearLayout(context)

    private var photos: List<File> = emptyList()
    private var photoIndex = -1
    private var photo: Bitmap? = null
    private var photoCaption: String? = null
    private var art: Bitmap? = null

    private val photoTick = object : Runnable {
        override fun run() {
            nextPhoto()
            main.postDelayed(this, settings.photoSeconds * 1000L)
        }
    }
    private val calendarTick = object : Runnable {
        override fun run() {
            loadCalendar()
            main.postDelayed(this, CALENDAR_INTERVAL_MS)
        }
    }

    init {
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        backdrop.addView(image, FrameLayout.LayoutParams(-1, -1))
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        caption.typeface = Theme.regular
        caption.setTextColor(Theme.TEXT_2)
        caption.setShadowLayer(px(8f).toFloat(), 0f, px(1f).toFloat(), 0x99000000.toInt())
        caption.maxLines = 1
        caption.ellipsize = TextUtils.TruncateAt.END
        backdrop.addView(caption, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            leftMargin = px(48f); topMargin = px(32f)
        })
        backdrop.visibility = View.GONE
        backdrop.auditTag(AuditTags.FULLBLEED, AuditTags.LAYER)
        calendar.orientation = LinearLayout.VERTICAL
        calendar.gravity = Gravity.END
        calendar.visibility = View.GONE
        calendar.auditTag(AuditTags.CONTENT)
    }

    private fun px(dp: Float) = (dp * density + 0.5f).toInt()

    fun start() {
        gen++
        settings = AmbientWidgetsStore(app).load()
        if (AmbientWidgetsModel.showCalendar(settings, AmbientWidgetsStore.calendarGranted(app))) main.post(calendarTick)
        else calendar.visibility = View.GONE
        if (settings.photos) {
            val g = gen
            io.execute {
                @Suppress("DEPRECATION")
                val pictures = try { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) } catch (e: Throwable) { null }
                val list = try { AmbientWidgetsModel.scan(AmbientWidgetsModel.photoRoots(File("/storage"), pictures)) } catch (e: Throwable) { emptyList() }
                main.post { if (g == gen) { photos = list.shuffled(); photoIndex = -1; main.post(photoTick) } }
            }
        }
        render()
    }

    fun stop() {
        gen++
        main.removeCallbacks(photoTick)
        main.removeCallbacks(calendarTick)
        image.setImageDrawable(null)
        photo?.recycle(); photo = null
        art = null
        backdrop.visibility = View.GONE
    }

    /** Album art from the Now Playing card (null when nothing plays). The bitmap is owned by the tracker. */
    fun setArt(bmp: Bitmap?) {
        art = bmp?.takeIf { !it.isRecycled }
        render()
    }

    private fun render() {
        when (AmbientWidgetsModel.backdrop(settings, art != null, photo != null)) {
            AmbientWidgetsModel.Backdrop.ALBUM_ART -> show(art, null)
            AmbientWidgetsModel.Backdrop.PHOTO -> show(photo, photoCaption)
            AmbientWidgetsModel.Backdrop.WALLPAPER -> { image.setImageDrawable(null); backdrop.visibility = View.GONE }
        }
    }

    private fun show(b: Bitmap?, text: String?) {
        image.setImageBitmap(b)
        caption.text = text ?: ""
        caption.visibility = if (text == null) View.GONE else View.VISIBLE
        if (backdrop.visibility != View.VISIBLE) {
            backdrop.visibility = View.VISIBLE
            if (!lite) { backdrop.alpha = 0f; backdrop.animate().alpha(1f).setDuration(800L).start() }
        }
    }

    private fun nextPhoto() {
        if (photos.isEmpty()) return
        photoIndex = AmbientWidgetsModel.next(photoIndex, photos.size)
        val f = photos[photoIndex]
        val g = gen
        val dm = app.resources.displayMetrics
        val maxSide = if (lite) 1280 else maxOf(dm.widthPixels, dm.heightPixels).coerceAtMost(1920)
        io.execute {
            val bmp = decode(f, maxSide)
            val date = try { AmbientWidgetsModel.exifDate(ExifInterface(f.path).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: ExifInterface(f.path).getAttribute(ExifInterface.TAG_DATETIME)) } catch (e: Throwable) { null }
            val text = AmbientWidgetsModel.caption(date, f.parentFile?.name)
            main.post {
                if (g != gen || bmp == null) { bmp?.recycle(); return@post }
                val old = photo
                photo = bmp; photoCaption = text
                render()
                if (old != null && old !== bmp) old.recycle()
            }
        }
    }

    private fun loadCalendar() {
        val g = gen
        io.execute {
            val now = System.currentTimeMillis()
            val events = try { AmbientWidgetsModel.upcoming(readEvents(now), now) } catch (e: Throwable) { emptyList() }
            main.post { if (g == gen) renderCalendar(events, now) }
        }
    }

    private fun readEvents(now: Long): List<CalendarEvent> {
        if (!AmbientWidgetsStore.calendarGranted(app)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath((now + AmbientWidgetsModel.LOOKAHEAD_MS).toString()).build()
        val out = ArrayList<CalendarEvent>()
        app.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < 20) out += CalendarEvent(c.getString(0) ?: "", c.getLong(1), c.getInt(2) == 1)
        }
        return out
    }

    private fun renderCalendar(events: List<CalendarEvent>, now: Long) {
        calendar.removeAllViews()
        if (events.isEmpty()) { calendar.visibility = View.GONE; return }
        calendar.visibility = View.VISIBLE
        val h24 = android.text.format.DateFormat.is24HourFormat(app)
        for (e in events) {
            val t = TextView(calendar.context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Theme.medium
                setTextColor(Theme.TEXT)
                setShadowLayer(px(10f).toFloat(), 0f, px(1f).toFloat(), 0x66000000)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = px(420f)
                gravity = Gravity.END
                text = "${AmbientWidgetsModel.whenLabel(e, now, TimeZone.getDefault(), h24)}  ${e.title}"
            }
            calendar.addView(t, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4f) })
        }
    }

    private fun decode(f: File, maxSide: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inSampleSize = s
            inPreferredConfig = if (lite) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        })
    } catch (e: Throwable) {
        null
    }

    private companion object {
        const val CALENDAR_INTERVAL_MS = 5 * 60_000L
        val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ambient-widgets").apply { isDaemon = true } }
    }
}
