# Wavefront — the clients

All of Will's Wavefront tooling lives here, one directory per surface.
One canon, every client: the OG Wavefront dataset (897 direct mutuals,
462 follows, the prune queue) is generated once in `shared/` from the
persona register + the archive index, and every client consumes it.

- `shared/` — the dataset generator (`gen_data.py`), the generated
  `data/wavefront.json`, the icon art (the soul-print, supplied by Will
  2026-11-14), and the shared state format: encounters, spaced-repetition
  cards, and prune decisions use the same JSON shapes on every client,
  so state exports move between surfaces.
- `windows/` — the Win11 surface: **Golden Thread for Chrome**
  (`golden-thread-extension/`), where Will uses X on the web. Gold moots,
  RGB-aligned follows, muted unaligned, half-erased chaff; hover
  fireworks, spaced repetition, daily prune of 10, encounter log.
- `android/` — the Pixel surface: **Golden Thread for Android**
  (`golden-thread/`), where Will uses the X app. A floating soul-print
  bubble hovers over X (draw-over-apps, Pokemon-Go style): today's prune
  and the next memory card in the panel, a share-sheet target in X
  ("share to Golden Thread" logs the encounter and shows the card), and
  the same canon + state format as the extension.
- `ios/`, `linux/`, `mac/` — placeholders, built out upon request.

Clients are built per Will's direction, in his order — not speculatively.
