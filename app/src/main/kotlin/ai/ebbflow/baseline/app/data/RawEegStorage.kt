package ai.ebbflow.baseline.app.data

/**
 * Whether raw EEG samples are written to storage. This build has no raw recorder: the
 * database keeps quality summaries (see [SignalQualitySample]), each with one channel-averaged
 * µV value from a single packet (`meanUv`), and nothing else in the app writes EEG to disk. When a user-enabled raw-storage setting lands, read it here so
 * every surface (the "Stored on this phone" widget included) reports the real state.
 */
object RawEegStorage {
    const val ENABLED: Boolean = false
}
