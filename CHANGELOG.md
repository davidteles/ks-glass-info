# Changelog

All notable changes to this project. Versions follow [semantic versioning](https://semver.org/); each entry links to its full release notes on GitHub, which also say how each change was tested.

## [0.8.1] — 2026-11-19

The weather card fits after rotating to portrait.

- **Columns layout, after rotating the tablet to portrait:** the weather card's temperature ran past its card and off the right edge of the screen.

## [0.8.0] — 2026-11-16

Serious alerts in red.

- Alerts whose text starts with "!" (for example "!💧 Water leak") are drawn in a red tint everywhere alerts appear: day chips (Corners and Columns), beside Now Playing, the info line and the night panel. The "!" is not shown.

## [0.7.0] — 2026-11-16

- New **Day layout style** setting: Corners (default, unchanged) or **Columns** — the photo-screensaver widgets arranged like beside Now Playing: clock + date and every alert on the left; weather, chips and the next events on the right; the middle of the photo stays free.

## [0.6.0] — 2026-11-16

- **Now Playing:** everything that matters beside the cover. Left: big clock + date, then every alert as an amber chip ("+N" when they don't all fit). Right: weather + inside, your chips (alarm, car, power…) and the next events while there is room.
- **Day chips:** when they don't fit one row they wrap onto up to three centred rows (before, an overflowing row started at the left edge and lost the extra chips).

## [0.5.1] — 2026-11-16

- Chips, calendars and free-text cards are lists: up to 8 chips (entity, name, icon), up to 4 calendars (each with its colour), up to 6 free-text cards (template, position, style, size).
- With nothing changed it looks exactly like 0.4.1.

## [0.5.0] — 2026-11-16

- Layout: each card on/off and in any corner, distance from the edge, alerts as chips or one iPad-style info line (bottom or top, left / centre / right).
- Look: overall and per-card size, card darkness, border, corner rounding, glass tint, text colour, font, clock weight.
- Clock: date on/off, date format, seconds. Weather: icon / condition / inside on or off, icon colour. Events: width, colour per calendar.
- Chips: up to 8 entities with names and icons; alert colour.
- Free text: any Home Assistant template as a card or plain text, in any corner.

## [0.4.1] — 2026-11-16

- Now Playing: a big clock and date left of the cover, weather and inside temperature on the right, and the alerts in a slimmer pill at the top.

## [0.4.0] — 2026-11-16

- New: while the full-screen Now Playing player shows, a slim glass strip at the top keeps the time, weather, inside temperature and alerts in view (setting: Info strip above Now Playing).

## [0.3.2] — 2026-11-16

- Fixes the plugin icon size (256×256) so Kiosk Satellite accepts repository installs.

## [0.3.1] — 2026-11-16

- Day: glass widgets over any screensaver (clock and date, weather and inside temperature, next events, alert and entity chips).
- Night: the Glass Info screensaver mode, one glass panel on black, with the stock widgets hidden over it.
- Live data from Home Assistant (one template subscription; calendars every 10 minutes).

[0.8.1]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.8.1
[0.8.0]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.8.0
[0.7.0]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.7.0
[0.6.0]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.6.0
[0.5.1]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.5.1
[0.5.0]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.5.0
[0.4.1]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.4.1
[0.4.0]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.4.0
[0.3.2]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.3.2
[0.3.1]: https://github.com/davidteles/ks-glass-info/releases/tag/v0.3.1
