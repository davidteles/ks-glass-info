# Smoke checklist

`python tools/test.py` checks the manifest, assets and build. What needs a real tablet is here: run it before a release that touches what's being shown, and say in the pull request which steps you did.

1. **Each mode with real data:** Day (glass cards over the photos), Now Playing (clock and weather beside the cover) and Night (the Glass Info panel). Nothing overlaps or is cut off.
2. **Empty states:** no events in the next days, no alerts, a chip whose entity is unavailable. Each shows nothing (or a dash) rather than an error, `undefined` or an empty card.
3. **Taps pass through:** on the Day overlay, a tap wakes the screen or reaches the dashboard as it does without the plugin.
4. **Home Assistant restarts:** restart Home Assistant while the screensaver shows. Values come back by themselves within a minute; nothing stays frozen.
5. **The clock:** it keeps time for a few minutes on each mode. The Night panel's clock runs inside the page; the plugin only republishes the panel when the data changes (it compares a fingerprint), so a ticking clock never republishes it.
6. **Settings:** change a chip and the alert colour; the screensaver picks them up without restarting the app.

Last full run: (none recorded yet)

## Partial runs

Steps that were checked on a real tablet outside a full run. Record the version, the device and what was and wasn't looked at.

| Date | Version | Device | What was checked | Result | Not checked |
|---|---|---|---|---|---|
| 2026-11-19 | v0.8.1 (release zip, sha256 matched) | Samsung Galaxy Tab A11, Kiosk Satellite (app version not recorded) | Day mode, Columns layout: portrait, then rotated to landscape on the device without reopening | Portrait: the temperature fits inside its card (the v0.8.0 bug); condition and inside temperature shown in full. Landscape: weather icon back, temperature inside its card, date in full | A cold start in portrait; Night and Now Playing; steps 2–6 above. Still shortened in portrait (by design, not a bug): the long date and long event names |
