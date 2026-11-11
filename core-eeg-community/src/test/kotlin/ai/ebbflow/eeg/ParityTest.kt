package ai.ebbflow.eeg

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pipeline parity over SYNTHETIC frame streams: the same bytes must give the same packets,
 * statistics and quality summaries however they are chunked. Proves the shared core only,
 * not the Android or desktop wiring, and says nothing about real MW75 data.
 */
class ParityTest {
    private val tolerance = 1e-12

    private fun frame(counter: Int, channelRaw: (Int) -> Float, corrupt: Boolean = false): ByteArray {
        val packet = ByteArray(Mw75Constants.PACKET_SIZE)
        val bytes = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        packet[0] = Mw75Constants.SYNC_BYTE.toByte()
        packet[1] = Mw75Constants.EEG_EVENT_ID.toByte()
        packet[3] = counter.toByte()
        repeat(Mw75Constants.NUM_EEG_CHANNELS) { bytes.putFloat(12 + it * 4, channelRaw(it)) }
        var checksum = (0 until 61).sumOf { packet[it].toInt() and 0xFF } and 0xFFFF
        if (corrupt) checksum = checksum xor 0xFFFF
        packet[61] = checksum.toByte()
        packet[62] = (checksum shr 8).toByte()
        return packet
    }

    private fun sign(counter: Int) = if (counter % 2 == 0) 1f else -1f

    // Raw 30000 * 0.023842 = 715 uV, above the 500 uV clip threshold.
    private fun clean(counter: Int) = frame(counter, { sign(counter) * (100f + it * 10f) })
    private fun clipped(counter: Int) =
        frame(counter, { if (it < 3) sign(counter) * 30000f else sign(counter) * (100f + it * 10f) })
    private fun flat(flatChannels: Int) = { counter: Int ->
        frame(counter, { if (it < flatChannels) 100f else sign(counter) * (100f + it * 10f) })
    }

    private val streams: Map<String, (Int) -> ByteArray> = linkedMapOf(
        "clean" to ::clean,
        "clipped" to ::clipped,
        "flatline" to flat(Mw75Constants.NUM_EEG_CHANNELS),
        "one_flat" to flat(1),
        "two_flat" to flat(2),
    )

    private fun build(make: (Int) -> ByteArray, frames: Int = 500) =
        (0 until frames).fold(ByteArray(0)) { acc, i -> acc + make(i % 256) }

    private fun summaries(bytes: ByteArray, chunk: Int): List<SignalQualitySummary> {
        val parser = PacketParser()
        val estimator = SignalQualityEstimator()
        val out = mutableListOf<SignalQualitySummary>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunk, bytes.size)
            parser.feed(bytes.copyOfRange(offset, end)).forEach { estimator.add(it)?.let(out::add) }
            offset = end
        }
        return out
    }

    private fun golden(): Map<String, List<List<String>>> {
        val text = checkNotNull(javaClass.getResourceAsStream("/parity/golden_summaries.txt")) {
            "missing parity/golden_summaries.txt"
        }.bufferedReader().readText()
        return text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim().split(" ") }.groupBy { it[0] }
    }

    @Test
    fun goldenSummariesMatchAtEveryChunkSize() {
        val golden = golden()
        for ((name, make) in streams) {
            val expected = golden.getValue(name)
            val bytes = build(make)
            for (chunk in listOf(1, 7, 62, 63, 64, 200, bytes.size)) {
                val actual = summaries(bytes, chunk)
                assertEquals("$name chunk=$chunk count", expected.size, actual.size)
                expected.zip(actual).forEach { (row, summary) ->
                    val where = "$name chunk=$chunk"
                    assertEquals(where, row[1].toDouble(), summary.usableChannelsFraction, tolerance)
                    assertEquals(where, row[2].toDouble(), summary.clippedSamplesFraction, tolerance)
                    assertEquals(where, row[3].toDouble(), summary.flatlineChannelsFraction, tolerance)
                    assertEquals(where, SignalQualityLabel.valueOf(row[4]), summary.label)
                }
            }
        }
    }

    @Test
    fun everyTwoWaySplitYieldsTheSamePackets() {
        val bytes = (0 until 5).fold(ByteArray(0)) { acc, i -> acc + clean(i) }
        for (split in 0..bytes.size) {
            val parser = PacketParser()
            val packets = parser.feed(bytes.copyOfRange(0, split)) + parser.feed(bytes.copyOfRange(split, bytes.size))
            assertEquals("split=$split", listOf(0, 1, 2, 3, 4), packets.map { it.counter })
            assertEquals(5, parser.stats.validPackets)
            assertEquals(0, parser.stats.invalidPackets)
        }
    }

    @Test
    fun decodedChannelsApplyTheScalingFactor() {
        val packet = PacketParser().feed(clean(0)).single()
        assertEquals(Mw75Constants.NUM_EEG_CHANNELS, packet.channels.size)
        repeat(Mw75Constants.NUM_EEG_CHANNELS) {
            val expected = (100f + it * 10f).toDouble() * Mw75Constants.EEG_SCALING_FACTOR
            assertTrue(abs(expected - packet.channels[it]) < tolerance)
        }
    }

    @Test
    fun corruptChecksumIsCountedAndSkippedAtEverySplit() {
        val bytes = clean(1) + frame(2, { 1f }, corrupt = true) + clean(3)
        for (split in 0..bytes.size) {
            val parser = PacketParser()
            val packets = parser.feed(bytes.copyOfRange(0, split)) + parser.feed(bytes.copyOfRange(split, bytes.size))
            assertEquals("split=$split", listOf(1, 3), packets.map { it.counter })
            assertEquals("split=$split", 2, parser.stats.validPackets)
            assertEquals("split=$split", 1, parser.stats.invalidPackets)
        }
    }

    @Test
    fun leadingGarbageWithoutSyncByteIsSkipped() {
        val garbage = ByteArray(200) { 0x11 }
        val parser = PacketParser()
        val packets = parser.feed(garbage + clean(9))
        assertEquals(listOf(9), packets.map { it.counter })
        assertEquals(0, parser.stats.invalidPackets)
    }

    @Test
    fun largeGarbageThenFrameStillRecovers() {
        // The parser's 630-byte overflow reset is not reachable through feed(): the loop
        // always consumes down to under one frame. This pins the observable guarantee,
        // that a large garbage burst cannot wedge the stream.
        val parser = PacketParser()
        assertTrue(parser.feed(ByteArray(5000) { 0x22 }).isEmpty())
        assertEquals(listOf(4), parser.feed(clean(4)).map { it.counter })
    }

    @Test
    fun nonEegEventsAreValidatedButNotEmitted() {
        val packet = ByteArray(Mw75Constants.PACKET_SIZE)
        packet[0] = Mw75Constants.SYNC_BYTE.toByte()
        packet[1] = 0x10
        val sum = (0 until 61).sumOf { packet[it].toInt() and 0xFF }
        packet[61] = sum.toByte()
        packet[62] = (sum shr 8).toByte()
        val parser = PacketParser()
        assertTrue(parser.feed(packet).isEmpty())
        assertEquals(0, parser.stats.invalidPackets)
    }

    @Test
    fun scriptedSourceDeliversStatusPacketAndOptionalStatistics() {
        val events = mutableListOf<String>()
        val source = object : EegSource {
            private var listener: EegSource.Listener? = null
            override fun start(listener: EegSource.Listener) {
                this.listener = listener
                listener.onStatus(EegSourceStatus(EegSourcePhase.CONNECTING))
                listener.onStatus(EegSourceStatus(EegSourcePhase.STREAMING))
                PacketParser().feed(clean(5)).forEach(listener::onPacket)
            }
            override fun stop() {
                listener?.onStatus(EegSourceStatus(EegSourcePhase.STOPPED))
                listener = null
            }
        }
        source.start(object : EegSource.Listener {
            override fun onStatus(status: EegSourceStatus) { events += status.phase.name }
            override fun onPacket(packet: EegPacket) { events += "packet${packet.counter}" }
        })
        source.stop()
        assertEquals(listOf("CONNECTING", "STREAMING", "packet5", "STOPPED"), events)
        assertNotNull(EegStreamStatistics(validPackets = null, invalidPackets = null))
    }
}
