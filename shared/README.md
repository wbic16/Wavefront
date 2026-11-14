# shared — one canon, every client

- `gen_data.py` — regenerates the dataset from the persona register
  (~/workspace/personas) and the archive index (exo.db): moots with
  coordinates + canon interests, follows with alignment, the ranked
  prune queue. Outputs `data/wavefront.json` (all clients) and the
  extension's `data/moots.js`.
- `data/wavefront.json` — the generated canon. Snapshot ends
  2025-06-16 on purpose: the OG Wavefront is final and historic.
- Icon art: `icon-source-square.png`, `icon-512.png` (Will's soul-print).
- State format (shared by all clients): encounters
  `[{handle, ts, cls, surface}]`, srs `{handle: {interval, reps, due}}`,
  prune `{day, today[], decisions{handle:{a, day, ts, until}}}`.
