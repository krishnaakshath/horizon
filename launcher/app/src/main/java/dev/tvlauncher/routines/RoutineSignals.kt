package dev.tvlauncher.routines

import java.util.concurrent.CopyOnWriteArrayList

/** Tells a screen in front that a routine or Bedtime mode changed per-person settings (main thread). */
object RoutineSignals {
    fun interface Listener { fun onSettingsApplied() }

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun add(l: Listener) { listeners.addIfAbsent(l) }
    fun remove(l: Listener) { listeners.remove(l) }
    fun fire() { for (l in listeners) try { l.onSettingsApplied() } catch (e: RuntimeException) { } }
}
