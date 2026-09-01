package ai.ebbflow.eeg

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Incremental, checksum-validating parser for the documented 63-byte MW75 frame. */
class PacketParser {
    class Stats {
        var validPackets: Long = 0
        var invalidPackets: Long = 0
        var totalPackets: Long = 0
        val errorRate: Double
            get() = if (totalPackets == 0L) 0.0 else invalidPackets.toDouble() / totalPackets * 100.0
    }

    val stats = Stats()
    private var buffer = ByteArray(0)
    private fun unsigned(value: Byte) = value.toInt() and 0xFF

    fun isChecksumValid(packet: ByteArray): Boolean {
        if (packet.size < Mw75Constants.PACKET_SIZE) return false
        val calculated = (0 until 61).sumOf { unsigned(packet[it]) } and 0xFFFF
        val received = unsigned(packet[61]) or (unsigned(packet[62]) shl 8)
        return calculated == received
    }

    private fun parse(packet: ByteArray): EegPacket? {
        if (packet.size != Mw75Constants.PACKET_SIZE || unsigned(packet[0]) != Mw75Constants.SYNC_BYTE) return null
        stats.totalPackets++
        if (!isChecksumValid(packet)) {
            stats.invalidPackets++
            return null
        }
        stats.validPackets++
        val bytes = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        return EegPacket(
            timestampMs = System.currentTimeMillis(),
            eventId = unsigned(packet[1]),
            counter = unsigned(packet[3]),
            ref = bytes.getFloat(4),
            drl = bytes.getFloat(8),
            channels = List(Mw75Constants.NUM_EEG_CHANNELS) { channel ->
                bytes.getFloat(12 + channel * 4).toDouble() * Mw75Constants.EEG_SCALING_FACTOR
            },
            featureStatus = unsigned(packet[60]),
        )
    }

    fun feed(data: ByteArray): List<EegPacket> {
        buffer += data
        val output = mutableListOf<EegPacket>()
        var offset = 0
        while (offset < buffer.size) {
            if (unsigned(buffer[offset]) != Mw75Constants.SYNC_BYTE) {
                offset++
                continue
            }
            if (offset + Mw75Constants.PACKET_SIZE > buffer.size) break
            val candidate = buffer.copyOfRange(offset, offset + Mw75Constants.PACKET_SIZE)
            if (!isChecksumValid(candidate)) {
                parse(candidate)
                offset++
                continue
            }
            if (unsigned(candidate[1]) == Mw75Constants.EEG_EVENT_ID) parse(candidate)?.let(output::add)
            offset += Mw75Constants.PACKET_SIZE
        }
        if (offset > 0) buffer = buffer.copyOfRange(offset, buffer.size)
        if (buffer.size > Mw75Constants.PACKET_SIZE * 10) buffer = ByteArray(0)
        return output
    }
}
