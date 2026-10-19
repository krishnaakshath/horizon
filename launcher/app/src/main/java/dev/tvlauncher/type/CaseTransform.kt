package dev.tvlauncher.type

import android.graphics.Rect
import android.view.View
import android.text.method.TransformationMethod
import java.util.Locale

/** Pure, locale-aware upper-casing (Turkish i -> dotted capital I etc.). Null-safe. */
object CaseTransform {
    fun upper(text: CharSequence?, locale: Locale): CharSequence? = text?.toString()?.uppercase(locale)
}

/**
 * Display-time upper-casing for Label styles. Deliberately NOT TextView.setAllCaps / isAllCaps:
 * that setter is API 28+ while minSdk is 24 (NoSuchMethodError and a NewApi lint error).
 * TransformationMethod has existed since API 1.
 */
object UpperCaseTransformation : TransformationMethod {
    override fun getTransformation(source: CharSequence?, view: View?): CharSequence? =
        CaseTransform.upper(source, view?.let { localeOf(it) } ?: Locale.getDefault())

    override fun onFocusChanged(
        view: View?, sourceText: CharSequence?, focused: Boolean, direction: Int, previouslyFocusedRect: Rect?,
    ) = Unit

    private fun localeOf(view: View): Locale =
        (view as? android.widget.TextView)?.textLocale ?: Locale.getDefault()
}
