package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavParserTest {
    private fun wav(fmt: Int = 1, ch: Int = 1, rate: Int = 22050, bits: Int = 16, data: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + data.size); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16)
        bb.putShort(fmt.toShort()); bb.putShort(ch.toShort()); bb.putInt(rate)
        bb.putInt(rate * ch * bits / 8); bb.putShort((ch * bits / 8).toShort()); bb.putShort(bits.toShort())
        bb.put("data".toByteArray()); bb.putInt(data.size); bb.put(data)
        return bb.array()
    }
    private fun pcm(vararg v: Int): ByteArray {
        val bb = ByteBuffer.allocate(v.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { bb.putShort(it.toShort()) }; return bb.array()
    }

    @Test fun parses_valid_mono16() {
        val p = WavParser.parse(wav(data = pcm(100, -200, 300)))!!
        assertEquals(22050, p.sampleRate)
        assertArrayEquals(shortArrayOf(100, -200, 300), p.samples)
    }

    @Test fun downmixes_stereo() {
        val p = WavParser.parse(wav(ch = 2, data = pcm(100, 300, -50, -150)))!!
        assertArrayEquals(shortArrayOf(200, -100), p.samples)
    }

    @Test fun rejects_8bit_or_float() {
        assertNull(WavParser.parse(wav(bits = 8, data = ByteArray(10))))
        assertNull(WavParser.parse(wav(fmt = 3, bits = 32, data = ByteArray(16))))
    }

    @Test fun rejects_truncated_header() {
        val w = wav(data = pcm(1, 2, 3))
        assertNull(WavParser.parse(w.copyOf(20)))
        assertNull(WavParser.parse(ByteArray(0)))
        assertNull(WavParser.parse("garbage garbage garbage garbage garbage garbage".toByteArray()))
    }

    @Test fun rejects_oversize() {
        assertNull(WavParser.parse(wav(data = ByteArray(501 * 1024))))
    }

    @Test fun rejects_bad_rate_and_channels() {
        assertNull(WavParser.parse(wav(rate = 4000, data = pcm(1, 2))))
        assertNull(WavParser.parse(wav(rate = 96000, data = pcm(1, 2))))
        assertNull(WavParser.parse(wav(ch = 6, data = pcm(1, 2, 3, 4, 5, 6))))
    }
}
