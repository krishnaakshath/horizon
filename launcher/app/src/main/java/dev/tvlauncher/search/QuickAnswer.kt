package dev.tvlauncher.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The inline calculator: simple arithmetic only (+, -, *, /, x, ×, ÷, parentheses, decimals with
 * a point or a comma). No units, no functions, no variables. A query is an expression only when
 * it holds at least one operator between two numbers; anything else (a word, a lone number, a
 * division by zero, an overflow) has no answer.
 */
object QuickAnswer {
    const val MAX_LEN = 64
    private const val MAX_DEPTH = 16

    data class Answer(val expression: String, val value: String)

    fun answer(query: String): Answer? {
        val q = query.trim().removeSuffix("=").trim()
        if (q.isEmpty() || q.length > MAX_LEN) return null
        val tokens = tokenize(q) ?: return null
        if (tokens.count { it is Tok.Num } < 2 || tokens.none { it is Tok.Op && it.binary }) return null
        val p = Parser(tokens)
        val v = try { p.expr(0) } catch (e: ArithmeticException) { return null } catch (e: IllegalStateException) { return null }
        if (!p.done()) return null
        if (v.isNaN() || v.isInfinite() || kotlin.math.abs(v) > 1e15) return null
        return Answer(pretty(tokens), format(v))
    }

    /** Up to 10 significant digits, no trailing zeros, no exponent: 0.1 + 0.2 = 0.3. */
    fun format(v: Double): String {
        if (v == 0.0) return "0"
        val bd = BigDecimal(v).round(MathContext(10, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val s = bd.toPlainString()
        return if (s == "-0") "0" else s
    }

    private sealed class Tok {
        data class Num(val v: Double, val text: String) : Tok()
        /** [binary] is false for a sign in front of a number or parenthesis. */
        data class Op(val c: Char, val binary: Boolean) : Tok()
        object Open : Tok()
        object Close : Tok()
    }

    private fun tokenize(s: String): List<Tok>? {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || ((c == '.' || c == ',') && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val start = i
                    var sep = false
                    while (i < s.length && (s[i].isDigit() || ((s[i] == '.' || s[i] == ',') && !sep))) {
                        if (s[i] == '.' || s[i] == ',') sep = true
                        i++
                    }
                    val text = s.substring(start, i)
                    if (text.endsWith('.') || text.endsWith(',')) return null
                    // "1,000" may mean a thousand: too ambiguous to answer.
                    if (text.contains(',') && text.substringAfter(',').length == 3) return null
                    val v = text.replace(',', '.').toDoubleOrNull() ?: return null
                    out += Tok.Num(v, text)
                }
                c == '(' -> { out += Tok.Open; i++ }
                c == ')' -> { out += Tok.Close; i++ }
                c in "+-*/×÷xX" -> {
                    val prev = out.lastOrNull()
                    val binary = prev is Tok.Num || prev == Tok.Close
                    val op = when (c) { '×', 'x', 'X' -> '*'; '÷' -> '/'; else -> c }
                    // "x" is a multiply sign only between two operands.
                    if ((c == 'x' || c == 'X') && !binary) return null
                    if (!binary && op != '-' && op != '+') return null
                    out += Tok.Op(op, binary)
                    i++
                }
                else -> return null
            }
        }
        return out
    }

    private fun pretty(tokens: List<Tok>): String = buildString {
        for (t in tokens) when (t) {
            is Tok.Num -> append(t.text)
            is Tok.Op -> if (t.binary) append(' ').append(when (t.c) { '*' -> '×'; '/' -> '÷'; '-' -> '−'; else -> t.c }).append(' ') else append(if (t.c == '-') '−' else '+')
            Tok.Open -> append('(')
            Tok.Close -> append(')')
        }
    }

    /** Precedence climbing over the tokens: unary signs, then * and /, then + and -. */
    private class Parser(val t: List<Tok>) {
        var i = 0
        fun done() = i == t.size

        fun expr(depth: Int): Double {
            check(depth <= MAX_DEPTH)
            var v = term(depth)
            while (i < t.size) {
                val op = t[i] as? Tok.Op ?: break
                if (!op.binary || (op.c != '+' && op.c != '-')) break
                i++
                val r = term(depth)
                v = if (op.c == '+') v + r else v - r
            }
            return v
        }

        fun term(depth: Int): Double {
            var v = unary(depth)
            while (i < t.size) {
                val op = t[i] as? Tok.Op ?: break
                if (!op.binary || (op.c != '*' && op.c != '/')) break
                i++
                val r = unary(depth)
                v = if (op.c == '*') v * r else {
                    if (r == 0.0) throw ArithmeticException("division by zero")
                    v / r
                }
            }
            return v
        }

        fun unary(depth: Int): Double {
            check(i < t.size)
            val tok = t[i]
            if (tok is Tok.Op && !tok.binary) {
                i++
                val v = unary(depth + 1)
                return if (tok.c == '-') -v else v
            }
            return atom(depth)
        }

        fun atom(depth: Int): Double {
            check(i < t.size)
            return when (val tok = t[i++]) {
                is Tok.Num -> tok.v
                Tok.Open -> {
                    val v = expr(depth + 1)
                    check(i < t.size && t[i] == Tok.Close)
                    i++
                    v
                }
                else -> throw IllegalStateException("unexpected token")
            }
        }
    }
}
