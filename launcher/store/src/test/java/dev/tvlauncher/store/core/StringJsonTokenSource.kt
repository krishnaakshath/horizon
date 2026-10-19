package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.JsonTokenSource.Token

/**
 * Minimal streaming JSON tokenizer used only by JVM tests, mirroring the subset of
 * android.util.JsonReader semantics that [JsonTokenSource] exposes. Throws IllegalStateException on malformed input.
 */
class StringJsonTokenSource(private val s: String) : JsonTokenSource {
    private var i = 0
    private val stack = ArrayDeque<Char>() // 'o' object expecting name, 'v' object expecting value, 'a' array
    private val first = ArrayDeque<Boolean>()
    private var peeked: Token? = null

    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun fail(msg: String): Nothing = throw IllegalStateException("$msg at $i")

    override fun peek(): Token {
        peeked?.let { return it }
        ws()
        val t = when (val top = stack.lastOrNull()) {
            'o' -> {
                if (i >= s.length) fail("eof")
                if (s[i] == '}') Token.END_OBJECT else {
                    if (!first.last()) { if (s[i] != ',') fail("expected ,"); i++; ws() }
                    if (i >= s.length || s[i] != '"') fail("expected name")
                    Token.NAME
                }
            }
            'v' -> { if (i >= s.length || s[i] != ':') fail("expected :"); i++; ws(); valueToken() }
            'a' -> {
                if (i >= s.length) fail("eof")
                if (s[i] == ']') Token.END_ARRAY else {
                    if (!first.last()) { if (s[i] != ',') fail("expected ,"); i++; ws() }
                    valueToken()
                }
            }
            null -> if (i >= s.length) Token.END_DOCUMENT else valueToken()
            else -> fail("bad state $top")
        }
        peeked = t
        return t
    }

    private fun valueToken(): Token {
        if (i >= s.length) fail("eof")
        return when (val c = s[i]) {
            '{' -> Token.BEGIN_OBJECT
            '[' -> Token.BEGIN_ARRAY
            '"' -> Token.STRING
            't', 'f' -> Token.BOOLEAN
            'n' -> Token.NULL
            else -> if (c == '-' || c.isDigit()) Token.NUMBER else fail("unexpected $c")
        }
    }

    private fun expect(t: Token) { if (peek() != t) fail("expected $t got ${peek()}"); peeked = null }

    private fun afterValue() {
        if (stack.lastOrNull() == 'v') { stack.removeLast(); stack.addLast('o') }
        if (first.isNotEmpty()) { first.removeLast(); first.addLast(false) }
    }

    override fun beginObject() { expect(Token.BEGIN_OBJECT); i++; markValueStart(); stack.addLast('o'); first.addLast(true) }
    override fun endObject() { expect(Token.END_OBJECT); i++; stack.removeLast(); first.removeLast(); afterValue() }
    override fun beginArray() { expect(Token.BEGIN_ARRAY); i++; markValueStart(); stack.addLast('a'); first.addLast(true) }
    override fun endArray() { expect(Token.END_ARRAY); i++; stack.removeLast(); first.removeLast(); afterValue() }

    // Opening a container counts as consuming a value slot of the parent.
    private fun markValueStart() {
        if (stack.lastOrNull() == 'v') { stack.removeLast(); stack.addLast('o') }
        if (first.isNotEmpty()) { first.removeLast(); first.addLast(false) }
    }

    override fun hasNext(): Boolean { val t = peek(); return t != Token.END_OBJECT && t != Token.END_ARRAY && t != Token.END_DOCUMENT }

    override fun nextName(): String {
        expect(Token.NAME)
        val name = readString()
        if (first.isNotEmpty()) { first.removeLast(); first.addLast(false) }
        stack.removeLast(); stack.addLast('v')
        return name
    }

    override fun nextString(): String {
        val t = peek()
        val v = when (t) {
            Token.STRING -> { peeked = null; readString() }
            Token.NUMBER -> { peeked = null; readNumber() }
            else -> fail("expected string got $t")
        }
        afterValue()
        return v
    }

    override fun nextLong(): Long {
        val t = peek()
        val raw = when (t) {
            Token.NUMBER -> { peeked = null; readNumber() }
            Token.STRING -> { peeked = null; readString() }
            else -> fail("expected number got $t")
        }
        val v = raw.toLongOrNull() ?: raw.toDoubleOrNull()?.let { d -> if (d == Math.floor(d)) d.toLong() else null }
            ?: throw NumberFormatException(raw)
        afterValue()
        return v
    }

    override fun nextBoolean(): Boolean {
        expect(Token.BOOLEAN)
        val v = if (s.startsWith("true", i)) { i += 4; true } else if (s.startsWith("false", i)) { i += 5; false } else fail("bool")
        afterValue()
        return v
    }

    override fun nextNull() {
        expect(Token.NULL)
        if (!s.startsWith("null", i)) fail("null")
        i += 4
        afterValue()
    }

    override fun skipValue() {
        when (peek()) {
            Token.BEGIN_OBJECT -> { beginObject(); while (hasNext()) { nextName(); skipValue() }; endObject() }
            Token.BEGIN_ARRAY -> { beginArray(); while (hasNext()) skipValue(); endArray() }
            Token.STRING, Token.NUMBER -> nextString()
            Token.BOOLEAN -> nextBoolean()
            Token.NULL -> nextNull()
            Token.NAME -> { nextName(); skipValue() }
            else -> fail("cannot skip")
        }
    }

    private fun readString(): String {
        if (s[i] != '"') fail("expected quote")
        i++
        val sb = StringBuilder()
        while (true) {
            if (i >= s.length) fail("unterminated string")
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (i >= s.length) fail("bad escape")
                    when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun readNumber(): String {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        return s.substring(start, i)
    }
}
