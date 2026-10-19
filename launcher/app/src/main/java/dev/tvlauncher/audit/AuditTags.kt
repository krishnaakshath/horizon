package dev.tvlauncher.audit

import android.view.View
import dev.tvlauncher.R

/**
 * Hints for the debug-only layout auditor (src/debug/.../audit). Constants only: setting a tag has
 * no effect on layout or drawing, and release builds simply never read it.
 *
 * - [LAYER]: this container stacks its children on purpose (art + label, image + scrim); overlaps
 *   between any two of its descendants are not reported.
 * - [IGNORE]: skip this view and its subtree (decorations: focus rings, glows, ripples, pulses).
 * - [FULLBLEED]: may run to the screen edge (backdrops, hero art); exempt from safe-area/offscreen.
 * - [DENSE]: a dense label; exempt from the (opt-in) 18 sp body-text check, not from the 14 sp floor.
 * - [CONTENT]: treat a plain view as content for overlap/clipping checks.
 * - [SCRIM]: a deliberately translucent dimming layer; never reported as "content shows through".
 * - [OPAQUE]: a card that hides everything beneath it (frosted glass, panel cards): an occluder even
 *   when it is clickable (API 26+ makes clickable views focusable) or its drawable's opacity cannot be
 *   read, and never content itself.
 * - [GLASS]: a glass card that may be translucent (Settings > Appearance > Glass style). Never
 *   content itself; what is beneath it is hidden when its coverage (its drawable's [AuditOpacity])
 *   together with the scrims between them leaves less than AuditConfig.glassLeakMax showing.
 *
 * Usage: `view.auditTag(AuditTags.LAYER)` or `view.auditTag(AuditTags.FULLBLEED, AuditTags.LAYER)`.
 */
object AuditTags {
    const val LAYER = "audit:layer"
    const val IGNORE = "audit:ignore"
    const val FULLBLEED = "audit:fullbleed"
    const val DENSE = "audit:dense"
    const val CONTENT = "audit:content"
    const val SCRIM = "audit:scrim"
    const val OPAQUE = "audit:opaque"
    const val GLASS = "audit:glass"

    /** The key for [View.setTag]/[View.getTag]; frees the plain `view.tag` for app use. */
    val KEY: Int get() = R.id.audit_tag
}

/** A drawable whose real coverage (0..1) the debug auditor should read instead of guessing. */
interface AuditOpacity {
    val auditAlpha: Float
}

/** Marks this view for the layout auditor; a plain keyed string tag, inert in release. */
fun View.auditTag(vararg tags: String) {
    setTag(AuditTags.KEY, tags.joinToString(" "))
}

/** Adds [tag] to the view's audit tags (keeps the ones already set). */
fun View.auditAddTag(tag: String) {
    val cur = getTag(AuditTags.KEY) as? String
    if (cur != null && tag in cur.split(' ')) return
    setTag(AuditTags.KEY, if (cur.isNullOrBlank()) tag else "$cur $tag")
}
