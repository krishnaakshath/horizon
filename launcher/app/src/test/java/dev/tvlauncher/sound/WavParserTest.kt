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

    private fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun fmtBody(fmt: Int = 1, ch: Int = 1, rate: Int = 22050, bits: Int = 16, extra: ByteArray = ByteArray(0)) =
        le16(fmt) + le16(ch) + le32(rate.toLong()) + le32((rate * ch * bits / 8).toLong()) +
            le16(ch * bits / 8) + le16(bits) + extra
    private fun chunk(id: String, body: ByteArray, declared: Long = body.size.toLong()): ByteArray =
        id.toByteArray() + le32(declared) + body + (if (body.size % 2 == 1) byteArrayOf(0) else ByteArray(0))
    private fun riff(vararg chunks: ByteArray): ByteArray {
        val body = "WAVE".toByteArray() + chunks.fold(ByteArray(0)) { a, c -> a + c }
        return "RIFF".toByteArray() + le32(body.size.toLong()) + body
    }
    private val d3 = pcm(1, 2, 3)

    @Test fun dataSizeLargerThanArray_rejected() {
        val w = riff(chunk("fmt ", fmtBody()), "data".toByteArray() + le32(1000) + d3)
        assertNull(WavParser.parse(w))
    }

    @Test fun oddChunk_padding_handled() {
        val w = riff(chunk("fmt ", fmtBody()), chunk("junk", ByteArray(3)), chunk("data", d3))
        assertArrayEquals(shortArrayOf(1, 2, 3), WavParser.parse(w)!!.samples)
    }

    @Test fun extraChunksBeforeData_parsed() {
        val w = riff(chunk("LIST", ByteArray(10)), chunk("fmt ", fmtBody()), chunk("fact", ByteArray(4)), chunk("data", d3))
        assertEquals(3, WavParser.parse(w)!!.samples.size)
    }

    @Test fun fmtExtension_18byteFmt_parsed() {
        val w = riff(chunk("fmt ", fmtBody(extra = le16(0))), chunk("data", d3))
        assertEquals(3, WavParser.parse(w)!!.samples.size)
    }

    @Test fun chunkSize_0xFFFFFFFF_rejected_noAllocation() {
        val w = riff(chunk("fmt ", fmtBody()), "data".toByteArray() + le32(0xFFFFFFFFL) + d3)
        assertNull(WavParser.parse(w))
        val w2 = riff(chunk("junk", ByteArray(2), declared = 0xFFFFFFFFL), chunk("fmt ", fmtBody()), chunk("data", d3))
        assertNull(WavParser.parse(w2))
    }

    @Test fun zeroChannels_rejected() {
        assertNull(WavParser.parse(riff(chunk("fmt ", fmtBody(ch = 0)), chunk("data", d3))))
    }

    @Test fun fmtAfterData_rejected() {
        assertNull(WavParser.parse(riff(chunk("data", d3), chunk("fmt ", fmtBody()))))
    }

    @Test fun extensiblePcm16_rejected() {
        // WAVE_FORMAT_EXTENSIBLE (0xFFFE) is documented as unsupported.
        val w = riff(chunk("fmt ", fmtBody(fmt = 0xFFFE, extra = ByteArray(24))), chunk("data", d3))
        assertNull(WavParser.parse(w))
    }
}
