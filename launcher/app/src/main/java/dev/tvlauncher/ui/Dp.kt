package dev.tvlauncher.ui

import android.content.Context

internal fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
