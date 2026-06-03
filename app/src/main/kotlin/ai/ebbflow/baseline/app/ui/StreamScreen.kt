package ai.ebbflow.baseline.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.ebbflow.baseline.app.model.StreamPhase
import ai.ebbflow.baseline.app.model.StreamState
import kotlin.math.abs

/**
 * Single-screen bring-up UI: a start/stop control, the live connection phase, the
 * placeholder focus index, and enough diagnostics (battery, packet stats, latest
 * channel µV) to confirm the end-to-end pipeline is working on real hardware.
 */
@Composable
fun StreamScreen(
    state: StreamState,
    persistedCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val streaming = state.phase == StreamPhase.STREAMING
    val busy = state.phase in setOf(
        StreamPhase.SCANNING,
        StreamPhase.ACTIVATING,
        StreamPhase.CONNECTING_RFCOMM,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ebbflow", style = MaterialTheme.typography.headlineMedium)
        Text(
            "MW75 EEG focus — bring-up",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(state)
        FocusCard(state)

        if (streaming || state.validPackets > 0) {
            DiagnosticsCard(state, persistedCount)
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onStart,
                enabled = !streaming && !busy,
                modifier = Modifier.weight(1f),
            ) { Text("Start") }
            OutlinedButton(
                onClick = onStop,
                enabled = streaming || busy,
                modifier = Modifier.weight(1f),
            ) { Text("Stop") }
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusCard(state: StreamState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledValue("Status", state.phase.label())
            LabeledValue("Device", state.deviceName ?: "—")
            LabeledValue("Battery", state.batteryPercent?.let { "$it%" } ?: "—")
        }
    }
}

@Composable
private fun FocusCard(state: StreamState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Focus (placeholder)", style = MaterialTheme.typography.labelLarge)
            val focus = state.focus
            Text(
                focus?.let { "%.0f".format(it * 100) } ?: "—",
                style = MaterialTheme.typography.displayLarge,
            )
            LinearProgressIndicator(
                progress = { (focus ?: 0.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            state.thetaBetaRatio?.let {
                Text(
                    "θ/β ratio %.2f".format(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Not a validated measure — replaced by the Phase 2 sensing core.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(state: StreamState, persistedCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledValue("Valid packets", state.validPackets.toString())
            LabeledValue("Invalid packets", state.invalidPackets.toString())
            LabeledValue("Error rate", "%.2f%%".format(state.errorRatePercent))
            LabeledValue("Samples stored", state.samplesPersisted.toString())
            LabeledValue("Read back from DB", persistedCount.toString())
            if (state.latestChannelsUv.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Latest channels (µV)", style = MaterialTheme.typography.labelMedium)
                state.latestChannelsUv.forEachIndexed { i, v -> ChannelBar(i, v) }
            }
        }
    }
}

@Composable
private fun ChannelBar(index: Int, microvolts: Double) {
    // Normalise to a coarse ±100 µV range just for a visual sense of activity.
    val magnitude = (abs(microvolts) / 100.0).toFloat().coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("CH%02d".format(index + 1), style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(progress = { magnitude }, modifier = Modifier.weight(1f))
        Text("%+.1f".format(microvolts), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun StreamPhase.label(): String = when (this) {
    StreamPhase.IDLE -> "Idle"
    StreamPhase.SCANNING -> "Scanning…"
    StreamPhase.ACTIVATING -> "Activating EEG…"
    StreamPhase.CONNECTING_RFCOMM -> "Connecting…"
    StreamPhase.STREAMING -> "Streaming"
    StreamPhase.STOPPED -> "Stopped"
    StreamPhase.ERROR -> "Error"
}
