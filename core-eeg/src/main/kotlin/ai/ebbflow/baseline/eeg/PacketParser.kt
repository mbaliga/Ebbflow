package ai.ebbflow.baseline.eeg

import ai.ebbflow.baseline.eeg.Mw75Constants.EEG_EVENT_ID
import ai.ebbflow.baseline.eeg.Mw75Constants.EEG_SCALING_FACTOR
import ai.ebbflow.baseline.eeg.Mw75Constants.NUM_EEG_CHANNELS
import ai.ebbflow.baseline.eeg.Mw75Constants.PACKET_SIZE
import ai.ebbflow.baseline.eeg.Mw75Constants.SYNC_BYTE
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Faithful Kotlin port of the Python `PacketProcessor`
 * (mw75_streamer/data/packet_processor.py).
 *
 * RFCOMM delivers arbitrary-sized chunks while EEG packets are exactly
 * [PACKET_SIZE] bytes, so bytes are accumulated in a continuous buffer across
 * [feed] calls. Packets are located by the [SYNC_BYTE] and validated by
 * checksum; on a checksum failure the window advances by a single byte (not a
 * whole packet) so a real frame boundary is never skipped when payload happens
 * to contain a sync byte.
 *
 * Pure JVM — no Android dependencies — so it is unit-testable on the JVM/CI.
 */
class PacketParser {

    /** Running validation counters, mirroring the Python `ChecksumStats`. */
    class Stats {
        var validPackets: Long = 0
        var invalidPackets: Long = 0
        var totalPackets: Long = 0

        val errorRate: Double
            get() = if (totalPackets > 0) invalidPackets.toDouble() / totalPackets * 100.0 else 0.0
    }

    val stats = Stats()

    private var buffer = ByteArray(0)

    private fun u(b: Byte): Int = b.toInt() and 0xFF

    /**
     * Validate the MW75 checksum: sum of the first 61 bytes masked to 16 bits,
     * stored little-endian in bytes 61-62.
     */
    fun isChecksumValid(packet: ByteArray): Boolean {
        if (packet.size < PACKET_SIZE) return false
        var sum = 0
        for (k in 0 until 61) sum += u(packet[k])
        val calculated = sum and 0xFFFF
        val received = u(packet[61]) or (u(packet[62]) shl 8)
        return calculated == received
    }

    /**
     * Parse a single [PACKET_SIZE]-byte candidate. Returns the packet only when
     * the sync byte and checksum are valid; updates [stats] exactly as the
     * Python `parse_eeg_packet` does (total++ always, then valid++ or invalid++).
     */
    private fun parseEegPacket(packet: ByteArray): EegPacket? {
        if (packet.size != PACKET_SIZE || u(packet[0]) != SYNC_BYTE) return null

        val valid = isChecksumValid(packet)
        stats.totalPackets++
        if (!valid) {
            stats.invalidPackets++
            return null
        }
        stats.validPackets++

        val bb = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val ref = bb.getFloat(4)
        val drl = bb.getFloat(8)
        val channels = ArrayList<Double>(NUM_EEG_CHANNELS)
        for (ch in 0 until NUM_EEG_CHANNELS) {
            val raw = bb.getFloat(12 + ch * 4)
            channels.add(raw.toDouble() * EEG_SCALING_FACTOR)
        }

        return EegPacket(
            timestampMs = System.currentTimeMillis(),
            eventId = u(packet[1]),
            counter = u(packet[3]),
            ref = ref,
            drl = drl,
            channels = channels,
            featureStatus = u(packet[60]),
        )
    }

    /**
     * Append [data] to the internal buffer and return every complete EEG packet
     * (event id == [EEG_EVENT_ID]) that can now be decoded. Non-EEG events with a
     * valid checksum are consumed but not returned (matching the reference).
     */
    fun feed(data: ByteArray): List<EegPacket> {
        buffer += data

        val out = ArrayList<EegPacket>()
        var i = 0
        val size = buffer.size
        while (i < size) {
            if (u(buffer[i]) != SYNC_BYTE) {
                i += 1
                continue
            }
            if (i + PACKET_SIZE > size) {
                break // wait for the rest of this packet on the next feed
            }

            val packet = buffer.copyOfRange(i, i + PACKET_SIZE)
            if (!isChecksumValid(packet)) {
                // Record the failure (total++/invalid++) and slide by one byte so a
                // genuine alignment isn't skipped when payload contains a sync byte.
                parseEegPacket(packet)
                i += 1
                continue
            }

            if (u(packet[1]) == EEG_EVENT_ID) {
                parseEegPacket(packet)?.let(out::add)
            }
            i += PACKET_SIZE
        }

        if (i > 0) {
            buffer = buffer.copyOfRange(i, buffer.size)
        }

        // Defensive guard against unbounded growth if sync is lost.
        val maxBuffer = PACKET_SIZE * 10
        if (buffer.size > maxBuffer) {
            var syncPos = -1
            var j = buffer.size - PACKET_SIZE
            while (j >= 0) {
                if (u(buffer[j]) == SYNC_BYTE) {
                    syncPos = j
                    break
                }
                j -= 1
            }
            buffer = if (syncPos >= 0) buffer.copyOfRange(syncPos, buffer.size) else ByteArray(0)
        }

        return out
    }
}
