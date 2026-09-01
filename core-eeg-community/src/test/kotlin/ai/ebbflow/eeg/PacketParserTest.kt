package ai.ebbflow.eeg

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketParserTest {
    private fun frame(counter: Int, corrupt: Boolean = false): ByteArray {
        val packet = ByteArray(Mw75Constants.PACKET_SIZE)
        val bytes = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        packet[0] = Mw75Constants.SYNC_BYTE.toByte()
        packet[1] = Mw75Constants.EEG_EVENT_ID.toByte()
        packet[3] = counter.toByte()
        repeat(12) { bytes.putFloat(12 + it * 4, it.toFloat()) }
        var checksum = (0 until 61).sumOf { packet[it].toInt() and 0xFF } and 0xFFFF
        if (corrupt) checksum = checksum xor 0xFFFF
        packet[61] = checksum.toByte()
        packet[62] = (checksum shr 8).toByte()
        return packet
    }

    @Test
    fun parsesAcrossChunkBoundaries() {
        val parser = PacketParser()
        val bytes = frame(7)
        assertTrue(parser.feed(bytes.copyOfRange(0, 17)).isEmpty())
        val packets = parser.feed(bytes.copyOfRange(17, bytes.size))
        assertEquals(1, packets.size)
        assertEquals(7, packets.single().counter)
    }

    @Test
    fun rejectsBadChecksumAndRecovers() {
        val parser = PacketParser()
        val packets = parser.feed(frame(1, corrupt = true) + frame(2))
        assertEquals(listOf(2), packets.map { it.counter })
        assertEquals(1, parser.stats.invalidPackets)
        assertEquals(1, parser.stats.validPackets)
    }
}
