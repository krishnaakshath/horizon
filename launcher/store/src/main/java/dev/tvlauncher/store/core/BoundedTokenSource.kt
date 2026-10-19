package dev.tvlauncher.store.core

import java.io.IOException
import java.io.Reader

/** Thrown when untrusted JSON exceeds a structural limit. */
class JsonLimitExceeded(message: String) : IOException(message)

/** Enforces a maximum nesting depth and string/name length on any [JsonTokenSource]. */
class BoundedTokenSource(
    private val d: JsonTokenSource,
    private val maxDepth: Int = 64,
    private val maxString: Int = 64 * 1024,
) : JsonTokenSource by d {
    private var depth = 0

    private fun enter() { if (++depth > maxDepth) throw JsonLimitExceeded("JSON nested too deeply") }
    private fun check(s: String): String { if (s.length > maxString) throw JsonLimitExceeded("JSON string too long"); return s }

    override fun beginObject() { enter(); d.beginObject() }
    override fun beginArray() { enter(); d.beginArray() }
    override fun endObject() { d.endObject(); depth-- }
    override fun endArray() { d.endArray(); depth-- }
    override fun nextName(): String = check(d.nextName())
    override fun nextString(): String = check(d.nextString())

    // skipValue must go through our own begin/end so depth stays bounded while skipping.
    override fun skipValue() {
        when (peek()) {
            JsonTokenSource.Token.BEGIN_OBJECT -> { beginObject(); while (hasNext()) { nextName(); skipValue() }; endObject() }
            JsonTokenSource.Token.BEGIN_ARRAY -> { beginArray(); while (hasNext()) skipValue(); endArray() }
            JsonTokenSource.Token.STRING, JsonTokenSource.Token.NUMBER -> nextString()
            JsonTokenSource.Token.NAME -> { nextName(); skipValue() }
            else -> d.skipValue()
        }
    }
}

/**
 * Character-level guard placed BEFORE a streaming JSON reader: it aborts as soon as a single string literal
 * exceeds [maxString] chars or nesting exceeds [maxDepth], so the reader never buffers a huge token.
 */
class JsonGuardReader(
    private val inner: Reader,
    private val maxString: Int = 64 * 1024,
    private val maxDepth: Int = 64,
) : Reader() {
    private var inString = false
    private var escape = false
    private var run = 0
    private var depth = 0

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        val n = inner.read(cbuf, off, len)
        for (i in off until off + maxOf(n, 0)) {
            val c = cbuf[i]
            if (inString) {
                if (++run > maxString) throw JsonLimitExceeded("JSON string too long")
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
            } else when (c) {
                '"' -> { inString = true; run = 0 }
                '{', '[' -> if (++depth > maxDepth) throw JsonLimitExceeded("JSON nested too deeply")
                '}', ']' -> depth--
            }
        }
        return n
    }

    override fun close() = inner.close()
}
