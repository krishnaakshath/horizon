package dev.tvlauncher.controllers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import dev.tvlauncher.input.PadAxes
import dev.tvlauncher.input.PadCapture
import dev.tvlauncher.input.PadVibration
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/**
 * "Test controller": a drawn controller whose buttons, D-pad, triggers and sticks light up live.
 * While it is on screen it takes the controller's raw input ([PadCapture]), so pressing B only
 * lights B; holding B ([PadTestModel.LEAVE_MS]) or the remote's BACK leaves via [onLeave].
 */
class ControllerTestView(context: Context, private val accent: Int, private val onLeave: () -> Unit) : View(context) {
    private val model = PadTestModel()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f // drawing units
        color = Theme.STROKE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Theme.medium
        color = Theme.TEXT
    }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Theme.regular
        color = Theme.TEXT_2
    }
    private val rect = RectF()
    private var buzzedFor = -1

    private val leaveCheck = Runnable {
        if (model.shouldLeave(SystemClock.uptimeMillis())) onLeave()
    }

    private val capture = object : PadCapture.Listener {
        // Both arrive on the main thread (from the Activity's input dispatch).
        override fun onPadKey(keyCode: Int, down: Boolean, deviceId: Int, deviceName: String?) {
            model.onKey(keyCode, down, deviceName, SystemClock.uptimeMillis())
            if (down && buzzedFor != deviceId) { buzzedFor = deviceId; PadVibration.pulse(deviceId) } // shows vibration works
            if (PadTestModel.partOf(keyCode) == PadPart.B) {
                removeCallbacks(leaveCheck)
                if (down) postDelayed(leaveCheck, PadTestModel.LEAVE_MS + 20)
            }
            invalidate()
        }

        override fun onPadAxes(axes: PadAxes, deviceName: String?) {
            model.onAxes(axes, deviceName)
            invalidate()
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        contentDescription = "Controller test"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        PadCapture.listener = capture
    }

    override fun onDetachedFromWindow() {
        if (PadCapture.listener === capture) PadCapture.listener = null
        removeCallbacks(leaveCheck)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * VH / VW).toInt().coerceAtMost(context.dp(MAX_H_DP)))
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width / VW, height / VH)
        canvas.save()
        canvas.translate((width - VW * s) / 2f, (height - VH * s) / 2f)
        canvas.scale(s, s)
        label.textSize = 15f
        caption.textSize = 15f

        val title = model.deviceName?.takeIf { it.isNotBlank() } ?: "Press any button on the controller"
        caption.color = Theme.TEXT
        canvas.drawText(title, VW / 2f, 22f, caption)
        caption.color = Theme.TEXT_2

        pill(canvas, PadPart.L2, 70f, 44f, 120f, 26f, "L2")
        pill(canvas, PadPart.L1, 70f, 78f, 120f, 26f, "L1")
        pill(canvas, PadPart.R2, VW - 190f, 44f, 120f, 26f, "R2")
        pill(canvas, PadPart.R1, VW - 190f, 78f, 120f, 26f, "R1")
        trigger(canvas, model.axes.lt, 70f, 44f); trigger(canvas, model.axes.rt, VW - 190f, 44f)

        stick(canvas, PadPart.L3, 150f, 170f, model.axes.lx, model.axes.ly, "L")
        dpad(canvas, 230f, 262f)
        pill(canvas, PadPart.SELECT, 262f, 150f, 52f, 24f, "Select")
        circle(canvas, PadPart.GUIDE, VW / 2f, 162f, 18f, "")
        pill(canvas, PadPart.START, VW - 314f, 150f, 52f, 24f, "Start")
        val cx = VW - 150f
        circle(canvas, PadPart.Y, cx, 128f, 21f, "Y")
        circle(canvas, PadPart.X, cx - 42f, 170f, 21f, "X")
        circle(canvas, PadPart.B, cx + 42f, 170f, 21f, "B")
        circle(canvas, PadPart.A, cx, 212f, 21f, "A")
        stick(canvas, PadPart.R3, VW - 230f, 262f, model.axes.rx, model.axes.ry, "R")

        canvas.drawText("Hold B to go back", VW / 2f, VH - 8f, caption)
        canvas.restore()
    }

    private fun paintPart(lit: Boolean) {
        fill.color = if (lit) accent else IDLE
    }

    private fun pill(c: Canvas, p: PadPart, x: Float, y: Float, w: Float, h: Float, text: String) {
        paintPart(model.isLit(p))
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, h / 2f, h / 2f, fill)
        c.drawRoundRect(rect, h / 2f, h / 2f, stroke)
        label.textSize = if (w < 60f) 11f else 14f
        c.drawText(text, x + w / 2f, y + h / 2f + label.textSize * 0.36f, label)
    }

    /** The analog part of a trigger: a thin bar along the pill's bottom. */
    private fun trigger(c: Canvas, v: Float, x: Float, y: Float) {
        if (v <= 0.02f) return
        fill.color = Theme.TEXT
        rect.set(x + 10f, y + 21f, x + 10f + 100f * v.coerceIn(0f, 1f), y + 23f)
        c.drawRect(rect, fill)
    }

    private fun circle(c: Canvas, p: PadPart, x: Float, y: Float, r: Float, text: String) {
        paintPart(model.isLit(p))
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, stroke)
        if (text.isNotEmpty()) {
            label.textSize = 16f
            c.drawText(text, x, y + 6f, label)
        }
    }

    private fun stick(c: Canvas, p: PadPart, x: Float, y: Float, sx: Float, sy: Float, text: String) {
        paintPart(model.isLit(p))
        c.drawCircle(x, y, 44f, fill)
        c.drawCircle(x, y, 44f, stroke)
        fill.color = Theme.TEXT
        c.drawCircle(x + sx.coerceIn(-1f, 1f) * 30f, y + sy.coerceIn(-1f, 1f) * 30f, 13f, fill)
        caption.textSize = 12f
        c.drawText(text, x, y + 62f, caption)
        caption.textSize = 15f
    }

    private fun dpad(c: Canvas, x: Float, y: Float) {
        val k = 24f
        square(c, PadPart.UP, x - k / 2, y - k * 1.5f, k)
        square(c, PadPart.DOWN, x - k / 2, y + k / 2, k)
        square(c, PadPart.LEFT, x - k * 1.5f, y - k / 2, k)
        square(c, PadPart.RIGHT, x + k / 2, y - k / 2, k)
    }

    private fun square(c: Canvas, p: PadPart, x: Float, y: Float, k: Float) {
        paintPart(model.isLit(p))
        rect.set(x, y, x + k, y + k)
        c.drawRoundRect(rect, 5f, 5f, fill)
        c.drawRoundRect(rect, 5f, 5f, stroke)
    }

    private companion object {
        /** The drawing's own units, scaled to the view. */
        const val VW = 640f
        const val VH = 330f
        const val MAX_H_DP = 340
        /** Resting parts: faint glass, a little stronger than a chip so the shape reads. */
        const val IDLE = 0x26FFFFFF
    }
}
