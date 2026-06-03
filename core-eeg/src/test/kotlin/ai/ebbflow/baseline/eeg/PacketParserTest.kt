package ai.ebbflow.baseline.eeg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketParserTest {

    /**
     * Kotlin mirror of `build_eeg_packet` in
     * mw75_streamer/device/mock_rfcomm_manager.py, but deterministic so fields
     * can be asserted. Layout: [0]=0xAA, [1]=eventId, [2]=0x3C, [3]=counter,
     * [4:8]=REF f32, [8:12]=DRL f32, [12:60]=12 x f32 channels, [60]=feature,
     * [61:63]=little-endian checksum of bytes 0..60.
     */
    private fun fakeEegFrame(
        counter: Int,
        eventId: Int = Mw75Constants.EEG_EVENT_ID,
        ref: Float = 1.5f,
        drl: Float = -2.5f,
        channelsRaw: FloatArray = FloatArray(Mw75Constants.NUM_EEG_CHANNELS) { (it * 100).toFloat() },
        feature: Int = 0,
        corruptChecksum: Boolean = false,
    ): ByteArray {
        val p = ByteArray(Mw75Constants.PACKET_SIZE)
        val bb = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        p[0] = Mw75Constants.SYNC_BYTE.toByte()
        p[1] = eventId.toByte()
        p[2] = 0x3C
        p[3] = (counter and 0xFF).toByte()
        bb.putFloat(4, ref)
        bb.putFloat(8, drl)
        for (ch in 0 until Mw75Constants.NUM_EEG_CHANNELS) bb.putFloat(12 + ch * 4, channelsRaw[ch])
        p[60] = feature.toByte()
        var sum = 0
        for (k in 0 until 61) sum += p[k].toInt() and 0xFF
        var checksum = sum and 0xFFFF
        if (corruptChecksum) checksum = checksum xor 0xFFFF
        p[61] = (checksum and 0xFF).toByte()
        p[62] = ((checksum shr 8) and 0xFF).toByte()
        return p
    }

    @Test
    fun parsesValidFrame() {
        val parser = PacketParser()
        val raw = FloatArray(12) { (it * 100).toFloat() }
        val packets = parser.feed(fakeEegFrame(counter = 7, ref = 1.5f, drl = -2.5f, channelsRaw = raw))

        assertEquals(1, packets.size)
        val p = packets[0]
        assertEquals(Mw75Constants.EEG_EVENT_ID, p.eventId)
        assertEquals(7, p.counter)
        assertEquals(1.5f, p.ref, 1e-6f)
        assertEquals(-2.5f, p.drl, 1e-6f)
        assertEquals(0, p.featureStatus)
        assertEquals(12, p.channels.size)
        for (ch in 0 until 12) {
            assertEquals(raw[ch] * Mw75Constants.EEG_SCALING_FACTOR, p.channels[ch], 1e-9)
        }
        assertEquals(1, parser.stats.totalPackets)
        assertEquals(1, parser.stats.validPackets)
        assertEquals(0, parser.stats.invalidPackets)
    }

    @Test
    fun corruptChecksumRejected() {
        val parser = PacketParser()
        val packets = parser.feed(fakeEegFrame(counter = 1, corruptChecksum = true))

        assertTrue(packets.isEmpty())
        assertEquals(1, parser.stats.totalPackets)
        assertEquals(1, parser.stats.invalidPackets)
        assertEquals(0, parser.stats.validPackets)
    }

    /**
     * A stray sync byte before a real frame must only cost a 1-byte slide, not a
     * whole 63-byte skip, so the genuine frame is still recovered.
     */
    @Test
    fun recoverAfterStray() {
        val parser = PacketParser()
        val stream = byteArrayOf(Mw75Constants.SYNC_BYTE.toByte()) + fakeEegFrame(counter = 42)

        val packets = parser.feed(stream)

        assertEquals(1, packets.size)
        assertEquals(42, packets[0].counter)
        // The misaligned 63-byte window at offset 0 fails checksum (counted), then
        // the real frame at offset 1 validates.
        assertEquals(2, parser.stats.totalPackets)
        assertEquals(1, parser.stats.invalidPackets)
        assertEquals(1, parser.stats.validPackets)
    }

    @Test
    fun reframesAcrossOneByteChunks() {
        val parser = PacketParser()
        val n = 5
        val stream = (0 until n).fold(ByteArray(0)) { acc, c -> acc + fakeEegFrame(counter = c) }

        val collected = ArrayList<EegPacket>()
        for (b in stream) collected += parser.feed(byteArrayOf(b))

        assertEquals(n, collected.size)
        assertEquals(listOf(0, 1, 2, 3, 4), collected.map { it.counter })
        assertEquals(n.toLong(), parser.stats.validPackets)
    }

    @Test
    fun reframesAcross64ByteChunks() {
        val parser = PacketParser()
        val n = 6
        val stream = (0 until n).fold(ByteArray(0)) { acc, c -> acc + fakeEegFrame(counter = c) }

        val collected = ArrayList<EegPacket>()
        var off = 0
        while (off < stream.size) {
            val end = minOf(off + 64, stream.size) // 64 deliberately straddles 63-byte frames
            collected += parser.feed(stream.copyOfRange(off, end))
            off = end
        }

        assertEquals(n, collected.size)
        assertEquals((0 until n).toList(), collected.map { it.counter })
    }

    /**
     * A 0xAA byte inside the payload must not break framing: once a valid frame
     * is consumed, internal sync-looking bytes are never re-examined.
     */
    @Test
    fun syncByteInsidePayloadIsHarmless() {
        val parser = PacketParser()
        // Float bits 0x3FAAAAAA -> little-endian bytes AA AA AA 3F (contains 0xAA).
        val withSync = Float.fromBits(0x3FAAAAAA)
        val raw = FloatArray(12) { if (it == 0) withSync else 0f }
        val stream = fakeEegFrame(counter = 1, channelsRaw = raw) +
            fakeEegFrame(counter = 2, channelsRaw = raw)

        val packets = parser.feed(stream)

        assertEquals(2, packets.size)
        assertEquals(listOf(1, 2), packets.map { it.counter })
        assertEquals(
            withSync.toDouble() * Mw75Constants.EEG_SCALING_FACTOR,
            packets[0].channels[0],
            1e-9,
        )
    }

    @Test
    fun nonEegEventConsumedButNotEmitted() {
        val parser = PacketParser()
        // Valid checksum, but a non-EEG event id: consumed, not emitted, and (matching
        // the reference) the EEG stats are left untouched for non-EEG events.
        val packets = parser.feed(fakeEegFrame(counter = 3, eventId = 200))

        assertTrue(packets.isEmpty())
        assertEquals(0, parser.stats.totalPackets)
        // A following real EEG frame still parses, proving the prior frame was consumed.
        val next = parser.feed(fakeEegFrame(counter = 4))
        assertEquals(1, next.size)
        assertEquals(4, next[0].counter)
    }
}
