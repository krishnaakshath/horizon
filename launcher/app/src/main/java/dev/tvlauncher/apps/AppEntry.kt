package dev.tvlauncher.apps

import android.content.ComponentName

data class AppEntry(val packageName: String, val label: String, val activityClass: String)

fun AppEntry.component(): ComponentName = ComponentName(packageName, activityClass)
