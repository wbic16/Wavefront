# Golden Thread — Wavefront Moots (Chrome extension)

Will's direct X mutuals — the OG Wavefront — in gold thread.

## What it does
- **Golden highlight.** On x.com, every moot's name renders in gold with an ✦ moot badge, and their posts get a gold thread down the left edge.
- **Hover fireworks + persona card.** Hovering a moot's name fires a small gold firework burst and shows their card: persona coordinate, what they're into (from the canon), how often Will's posts mention them, how many of their posts he retweeted, and when they entered the thread.
- **Spaced repetition.** The popup's Review tab quizzes Will on what each moot has been into (multiple choice drawn from other moots' topics). Right answers stretch the interval (1d → ~3d → ~8d → ~20d…); a miss resets it to a day. The toolbar badge shows how many cards are due.
- **Encounter log.** The first hover on a moot in a session logs the encounter (handle, time, page). The Encounters tab lists them, exports the log as JSON, and copies a "fetch latest for: @a, @b, …" list — the worklist for the next persona-refresh pass with Emise.

## The corollary (v1.1) — everyone else, classified
- **Aligned follows (no follow-back): RGB backlit optimism.** Accounts Will follows who haven't followed back, whose canon interests align with his (their amplified words share his field — top-300 lexicon of his own posts, or a core signal term), render in full colour with an RGB backlit glow and a prismatic hover burst. 101 of them. Their encounters are logged too — they are the fetch-latest candidates.
- **Quick un-follow (v1.2).** Every muted (unaligned, no follow-back) account carries an un-follow button beside its badge: first click arms it, second click drives X's own menu (caret → Unfollow) and flips the post to half-erased. The action is written to the encounter log for review.
- **Unaligned follows: muted greyscale.** The remaining followed accounts (361 with handles) render desaturated and dimmed, badged "no follow-back".
- **The chaff: half-erased, Back to the Future style.** Every other author fades to ~48% opacity, greying and flickering faintly, as if being erased from the photograph. Hovering one briefly restores it — resisting erasure — then it fades again.
Will's own posts are exempt from all treatments.

## Daily prune (v1.3)
The popup's Prune tab serves **10 accounts a day** from the muted pool (361: followed, no follow-back, no aligned interests in the canon), ranked quietest first — no canon interests, then fewest of Will's mentions. Each card: profile ↗ (new tab), **un-followed ✓**, **keep** (out of the queue for good), **snooze 30d**. Decisions persist; a new day rolls the next 10. The full ranked queue is in prune-queue.md.

## Run it
chrome://extensions → Developer mode → Load unpacked → select this folder. No build step, no dependencies, no network calls: the moot data (data/moots.js) is generated from the persona register and ships inside the extension.

## Data
897 direct mutuals from the OG Wavefront canon (X archive snapshot ending 2025-06-16 — by design: final and historic). 128 carry interest terms drawn from their public words in the canon; interests refresh from the encounter log, not from silent scraping.

## Epic enhancements — shipped in v1
- Recall-before-reveal is built into the deck's design: the quiz asks before the card tells.
- Due-count badge on the toolbar icon.
- One-click fetch-latest worklist for persona refreshes.

## Epic enhancements — next, on request
- **Constellation overlay:** press G on any X page to dim everything except moots, sized by coordinate distance from Will (1.1.1/1.1.1/1.1.1).
- **Reply context rail:** while composing a reply to a moot, a side rail shows their interests and your last exchanges from the canon.
- **Wavefront weather:** a weekly digest — moots encountered, moots gone quiet (no encounter in 90+ days), anniversaries ("you first mentioned @X on this day in 2016").
- **Live refresh:** with Will's go-ahead, fetch a moot's latest public posts on encounter and fold them into the persona (the log already queues exactly this).

## Security scan & fixes (v1.3.1, 2026-11-14)
Scanned: dangerous-pattern battery (eval/Function/document.write/network APIs), permissions audit, message surface, inline-code check.
- **Clean:** zero network calls of any kind; no eval, no remote code, no inline scripts; permissions are only `storage` + `alarms` with host access limited to x.com/twitter.com; no externally-connectable surface; no web-accessible resources; all data stays in local extension storage.
- **Fixed (hardening):** all data-derived strings (handles, interest terms, coordinates) are now HTML-escaped before insertion in the tooltip and popup — the data is self-generated, but a crafted corpus word can no longer break markup.
- **Fixed (bugs):** (1) posts whose name block is inserted as a whole node were missed by the marker — now caught; (2) a quoted author inside a moot's post could stamp the whole post as chaff — an article already claimed by its author is never downgraded; (3) the prune day rolled over at UTC midnight (6pm CST) — now local midnight; (4) the Deck tab could double-print its stats card — now rebuilt cleanly; (5) extension-reload while a page is open no longer throws unhandled messaging errors; (6) the fireworks canvas now follows window resizes.

## Android
Chrome for Android has no extension runtime at all — Golden Thread cannot run in it. The realistic mobile path is a Firefox for Android port (Firefox supports MV3 add-ons on Android; this codebase needs manifest adjustments, not a rewrite). Edge for Android offers only a small curated extension set. Kiwi Browser, the old workaround, was discontinued in January 2025.
