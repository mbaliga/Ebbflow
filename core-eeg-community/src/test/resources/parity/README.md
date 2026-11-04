# Parity fixtures

Everything here is SYNTHETIC. No file is real MW75 data and none claims to be.

`golden_summaries.txt` lists the `SignalQualitySummary` sequence that `ParityTest`
expects for each synthetic stream (500 frames, window 250, emit every 125, so three
summaries per stream). The values follow from how each stream is built, not from
running the estimator:

- `clean`: every channel alternates sign each frame, none clipped, none flat.
- `clipped`: 3 of 12 channels alternate +/-715 uV (above the 500 uV threshold), so
  clipped fraction is 3/12 = 0.25 and usable is 0.75.
- `flatline`: all 12 channels constant, so flatline fraction is 1.0 and usable is 0.
- `one_flat`: 1 of 12 channels constant, usable 11/12.
- `two_flat`: 2 of 12 channels constant, usable 10/12.

Columns: stream, usable, clipped, flatline, label.
