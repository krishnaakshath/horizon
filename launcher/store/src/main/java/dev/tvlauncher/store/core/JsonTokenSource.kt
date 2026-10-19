package dev.tvlauncher.store.core

/**
 * The streaming-JSON operations the F-Droid parser needs. On device it is backed by android.util.JsonReader
 * (see net/AndroidJsonTokenSource); JVM tests use a small pure tokenizer, so the parsing logic stays pure.
 */
interface JsonTokenSource {
    enum class Token { BEGIN_ARRAY, END_ARRAY, BEGIN_OBJECT, END_OBJECT, NAME, STRING, NUMBER, BOOLEAN, NULL, END_DOCUMENT }

    fun peek(): Token
    fun beginObject()
    fun endObject()
    fun beginArray()
    fun endArray()
    fun hasNext(): Boolean
    fun nextName(): String
    fun nextString(): String
    fun nextLong(): Long
    fun nextBoolean(): Boolean
    fun nextNull()
    fun skipValue()
}

/** Type-tolerant reads: a value of an unexpected type is skipped and reported as null. */
internal fun JsonTokenSource.stringOrNull(): String? = when (peek()) {
    JsonTokenSource.Token.STRING, JsonTokenSource.Token.NUMBER -> nextString()
    else -> { skipValue(); null }
}

internal fun JsonTokenSource.longOrNull(): Long? = when (peek()) {
    JsonTokenSource.Token.NUMBER -> try { nextLong() } catch (_: NumberFormatException) { null }
    JsonTokenSource.Token.STRING -> nextString().trim().toLongOrNull()
    else -> { skipValue(); null }
}

/** Iterates an object's members if the next value is an object; otherwise skips it. */
internal inline fun JsonTokenSource.forEachMember(block: (String) -> Unit) {
    if (peek() != JsonTokenSource.Token.BEGIN_OBJECT) { skipValue(); return }
    beginObject()
    while (hasNext()) block(nextName())
    endObject()
}

/** Iterates an array's elements if the next value is an array; otherwise skips it. */
internal inline fun JsonTokenSource.forEachElement(block: () -> Unit) {
    if (peek() != JsonTokenSource.Token.BEGIN_ARRAY) { skipValue(); return }
    beginArray()
    while (hasNext()) block()
    endArray()
}
