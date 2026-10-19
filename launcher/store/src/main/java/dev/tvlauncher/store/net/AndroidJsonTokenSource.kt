package dev.tvlauncher.store.net

import android.util.JsonReader
import android.util.JsonToken
import dev.tvlauncher.store.core.JsonTokenSource
import dev.tvlauncher.store.core.JsonTokenSource.Token

/** Adapts android.util.JsonReader (true streaming, constant memory) to the pure parser's interface. */
class AndroidJsonTokenSource(private val r: JsonReader) : JsonTokenSource {

    override fun peek(): Token = when (r.peek()) {
        JsonToken.BEGIN_ARRAY -> Token.BEGIN_ARRAY
        JsonToken.END_ARRAY -> Token.END_ARRAY
        JsonToken.BEGIN_OBJECT -> Token.BEGIN_OBJECT
        JsonToken.END_OBJECT -> Token.END_OBJECT
        JsonToken.NAME -> Token.NAME
        JsonToken.STRING -> Token.STRING
        JsonToken.NUMBER -> Token.NUMBER
        JsonToken.BOOLEAN -> Token.BOOLEAN
        JsonToken.NULL -> Token.NULL
        JsonToken.END_DOCUMENT, null -> Token.END_DOCUMENT
    }

    override fun beginObject() = r.beginObject()
    override fun endObject() = r.endObject()
    override fun beginArray() = r.beginArray()
    override fun endArray() = r.endArray()
    override fun hasNext(): Boolean = r.hasNext()
    override fun nextName(): String = r.nextName()
    override fun nextString(): String = r.nextString()
    override fun nextLong(): Long = r.nextLong()
    override fun nextBoolean(): Boolean = r.nextBoolean()
    override fun nextNull() = r.nextNull()
    override fun skipValue() = r.skipValue()
}
