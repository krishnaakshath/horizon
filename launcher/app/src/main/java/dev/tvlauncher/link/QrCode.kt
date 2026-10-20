package dev.tvlauncher.link

/**
 * A small QR code encoder (ISO/IEC 18004): byte mode, error correction level M, versions 1 to 10
 * (up to 213 bytes), automatic mask choice by the standard penalty rules. Pure Kotlin, no Android.
 * [modules] is row-major: modules[y][x] is true for a dark module.
 */
class QrCode private constructor(val version: Int, val mask: Int, val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size

    operator fun get(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        const val MAX_VERSION = 10

        // Level M per version 1..10: error correction codewords per block, and blocks as
        // (count, data codewords) for group 1 and group 2.
        private val EC_PER_BLOCK = intArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
        private val G1 = arrayOf(1 to 16, 1 to 28, 1 to 44, 2 to 32, 2 to 43, 4 to 27, 4 to 31, 2 to 38, 3 to 36, 4 to 43)
        private val G2 = arrayOf(0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0, 2 to 39, 2 to 37, 1 to 44)
        private val ALIGN = arrayOf(
            intArrayOf(), intArrayOf(6, 18), intArrayOf(6, 22), intArrayOf(6, 26), intArrayOf(6, 30),
            intArrayOf(6, 34), intArrayOf(6, 22, 38), intArrayOf(6, 24, 42), intArrayOf(6, 26, 46), intArrayOf(6, 28, 50),
        )

        fun dataCapacity(version: Int): Int = G1[version - 1].let { it.first * it.second } + G2[version - 1].let { it.first * it.second }

        /** Bytes that fit in [version] (mode and length header included). */
        fun byteCapacity(version: Int): Int = (dataCapacity(version) * 8 - 4 - countBits(version)) / 8

        private fun countBits(version: Int) = if (version < 10) 8 else 16

        /** Encodes [text] as UTF-8; null when it does not fit in version 10. [forceMask] is for tests. */
        fun encode(text: String, forceMask: Int = -1): QrCode? = encode(text.toByteArray(Charsets.UTF_8), forceMask)

        fun encode(data: ByteArray, forceMask: Int = -1): QrCode? {
            val version = (1..MAX_VERSION).firstOrNull { byteCapacity(it) >= data.size } ?: return null
            val codewords = interleave(version, dataCodewords(version, data))
            val base = Builder(version)
            base.drawFunctionPatterns()
            base.drawCodewords(codewords)
            val masks = if (forceMask in 0..7) listOf(forceMask) else (0..7).toList()
            var best: Builder? = null
            var bestScore = Int.MAX_VALUE
            for (m in masks) {
                val b = base.copy()
                b.applyMask(m)
                b.drawFormatBits(m)
                val score = penalty(b.modules)
                if (score < bestScore) { bestScore = score; b.mask = m; best = b }
            }
            val b = best!!
            return QrCode(version, b.mask, b.modules)
        }

        internal fun dataCodewords(version: Int, data: ByteArray): IntArray {
            val cap = dataCapacity(version)
            val bits = ArrayList<Boolean>(cap * 8)
            fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v ushr i) and 1 == 1 }
            put(0b0100, 4)
            put(data.size, countBits(version))
            for (b in data) put(b.toInt() and 0xFF, 8)
            put(0, minOf(4, cap * 8 - bits.size))
            while (bits.size % 8 != 0) bits += false
            val out = IntArray(cap)
            for (i in 0 until bits.size / 8) {
                var v = 0
                for (j in 0 until 8) v = (v shl 1) or (if (bits[i * 8 + j]) 1 else 0)
                out[i] = v
            }
            var pad = 0xEC
            for (i in bits.size / 8 until cap) { out[i] = pad; pad = if (pad == 0xEC) 0x11 else 0xEC }
            return out
        }

        internal fun interleave(version: Int, data: IntArray): IntArray {
            val ec = EC_PER_BLOCK[version - 1]
            val blocks = ArrayList<IntArray>()
            var k = 0
            for ((count, len) in listOf(G1[version - 1], G2[version - 1])) {
                repeat(count) { blocks += data.copyOfRange(k, k + len); k += len }
            }
            val div = rsDivisor(ec)
            val ecBlocks = blocks.map { rsRemainder(it, div) }
            val out = ArrayList<Int>()
            val maxLen = blocks.maxOf { it.size }
            for (i in 0 until maxLen) for (b in blocks) if (i < b.size) out += b[i]
            for (i in 0 until ec) for (b in ecBlocks) out += b[i]
            return out.toIntArray()
        }

        internal fun gfMul(a: Int, b: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((b ushr i) and 1) * a)
            }
            return z and 0xFF
        }

        internal fun rsDivisor(degree: Int): IntArray {
            val r = IntArray(degree)
            r[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in 0 until degree) {
                    r[j] = gfMul(r[j], root)
                    if (j + 1 < degree) r[j] = r[j] xor r[j + 1]
                }
                root = gfMul(root, 0x02)
            }
            return r
        }

        internal fun rsRemainder(data: IntArray, div: IntArray): IntArray {
            val r = IntArray(div.size)
            for (b in data) {
                val f = b xor r[0]
                System.arraycopy(r, 1, r, 0, r.size - 1)
                r[r.size - 1] = 0
                for (i in r.indices) r[i] = r[i] xor gfMul(div[i], f)
            }
            return r
        }

        /** The 15 format bits for level M and [mask] (BCH code, XOR 0x5412). */
        fun formatBits(mask: Int): Int {
            val data = (0b00 shl 3) or mask // M = 00
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            return ((data shl 10) or rem) xor 0x5412
        }

        /** The 18 version bits (versions 7 and up). */
        fun versionBits(version: Int): Int {
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            return (version shl 12) or rem
        }

        internal fun penalty(m: Array<BooleanArray>): Int {
            val n = m.size
            var score = 0
            // Rule 1 (runs of 5+) and rule 3 (finder-like 1:1:3:1:1 with 4 light), rows and columns.
            for (pass in 0..1) {
                for (a in 0 until n) {
                    fun at(b: Int) = if (pass == 0) m[a][b] else m[b][a]
                    var run = 1
                    for (b in 1 until n) {
                        if (at(b) == at(b - 1)) {
                            run++
                            if (run == 5) score += 3 else if (run > 5) score++
                        } else run = 1
                    }
                    for (b in 0..n - 7) {
                        if (at(b) && !at(b + 1) && at(b + 2) && at(b + 3) && at(b + 4) && !at(b + 5) && at(b + 6)) {
                            fun light(from: Int, to: Int) = (from until to).all { it < 0 || it >= n || !at(it) }
                            if (light(b - 4, b)) score += 40
                            if (light(b + 7, b + 11)) score += 40
                        }
                    }
                }
            }
            // Rule 2: 2x2 blocks.
            for (y in 0 until n - 1) for (x in 0 until n - 1) {
                val c = m[y][x]
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) score += 3
            }
            // Rule 4: dark proportion.
            val dark = m.sumOf { row -> row.count { it } }
            val total = n * n
            val k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            score += maxOf(0, k) * 10
            return score
        }
    }

    private class Builder(val version: Int) {
        val n = version * 4 + 17
        var modules = Array(n) { BooleanArray(n) }
        var function = Array(n) { BooleanArray(n) }
        var mask = 0

        fun copy(): Builder = Builder(version).also { b ->
            b.modules = Array(n) { modules[it].copyOf() }
            b.function = function
        }

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            function[y][x] = true
        }

        fun drawFunctionPatterns() {
            for (i in 0 until n) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            finder(3, 3); finder(n - 4, 3); finder(3, n - 4)
            val align = ALIGN[version - 1]
            for (i in align.indices) for (j in align.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == align.size - 1) || (i == align.size - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(align[i] + dx, align[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
            drawFormatBits(0) // reserves the area; redrawn after masking
            if (version >= 7) {
                val bits = versionBits(version)
                for (i in 0 until 18) {
                    val bit = (bits ushr i) and 1 == 1
                    val a = n - 11 + i % 3
                    val b = i / 3
                    set(a, b, bit); set(b, a, bit)
                }
            }
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until n || y !in 0 until n) continue
                val d = maxOf(Math.abs(dx), Math.abs(dy))
                set(x, y, d != 2 && d != 4)
            }
        }

        fun drawFormatBits(m: Int) {
            val bits = formatBits(m)
            fun bit(i: Int) = (bits ushr i) and 1 == 1
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
            for (i in 9..14) set(14 - i, 8, bit(i))
            for (i in 0..7) set(n - 1 - i, 8, bit(i))
            for (i in 8..14) set(8, n - 15 + i, bit(i))
            set(8, n - 8, true)
        }

        fun drawCodewords(data: IntArray) {
            var i = 0
            var right = n - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until n) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) n - 1 - vert else vert
                        if (!function[y][x] && i < data.size * 8) {
                            modules[y][x] = (data[i ushr 3] ushr (7 - (i and 7))) and 1 == 1
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(m: Int) {
            for (y in 0 until n) for (x in 0 until n) {
                if (function[y][x]) continue
                val flip = when (m) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (flip) modules[y][x] = !modules[y][x]
            }
        }
    }
}
