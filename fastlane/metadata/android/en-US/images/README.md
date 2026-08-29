# images/

Sizes and check commands: `Personal-Tracker/store/ASSET_SPECS.md`.

## Needed, none present yet

- `icon.png` — 512x512, no alpha.
- `featureGraphic.png` — 1024x500, no alpha.
- `phoneScreenshots/` — 2 to 8, 1080x1920, no alpha.

## Shoot these, with a real headset and a real signal

1. Headset pairing and connection.
2. The live signal view, actually streaming.
3. A deviation-from-baseline timeline over a real session.
4. The foreground-service notification, showing the session is visible and stoppable.

**Do not fake an EEG trace in a store screenshot.** A synthetic waveform in a
health-adjacent listing is the kind of thing that gets an app pulled rather than
rejected, and it undermines the one claim this app is actually making.

Nothing in a screenshot should look like a clinical readout: no reference ranges,
no red or green "good/bad" bands, no diagnosis-shaped language. Red and green must
not carry meaning anywhere in this project regardless.

```sh
adb exec-out screencap -p > shot.png
magick shot.png -background black -alpha remove -alpha off phoneScreenshots/01.png
```
