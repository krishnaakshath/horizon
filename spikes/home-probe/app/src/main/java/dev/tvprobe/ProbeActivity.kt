package dev.tvprobe

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView
import android.os.Bundle

class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "home probe"
        tv.textSize = 72f
        tv.setTextColor(Color.WHITE)
        tv.setBackgroundColor(Color.BLACK)
        tv.gravity = Gravity.CENTER
        setContentView(tv)
    }
}
