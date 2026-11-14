#!/usr/bin/env python3
"""Generate data/moots.js for the Golden Thread extension from the canon:
personas register (stats, coordinates) + their public words in exo.db
(retweet corpus -> interest terms; mention dates -> first/last contact)."""
import json, re, sqlite3, collections, pathlib, sys
sys.path.insert(0, str(pathlib.Path.home() / "workspace/custom-skills/singular/scripts"))
from check_singular import WORD_RE
HOME = pathlib.Path.home()
reg = json.loads((HOME / "workspace/personas/personas-source.json").read_text())
con = sqlite3.connect(HOME / "workspace/search-index/exo.db")
STOP = set("""the a an and or of to in is it i you he she we they them his her its our your my me him us this that with for on are was be have has had not but at by from as do does did so if then than too very can will just should now im ive id ill youre hes shes dont cant wont isnt arent about into over under more most other some such only own same s t don shouldve wouldve couldve one two get got like really much many even still back way make made take see know go going gone come well right first last long great little own also around man woman day time year people mr way years work first well way use used using make like""".split())
rt_re = re.compile(r"^RT @([A-Za-z0-9_]{1,15}):\s*(.*)", re.S)
their_words = collections.defaultdict(collections.Counter)
for (text,) in con.execute("SELECT text FROM docs WHERE source='tweets' AND title LIKE '%[RT]%'"):
    m = rt_re.match(text or "")
    if m:
        for w in WORD_RE.findall(m.group(2).lower()):
            if w not in STOP and len(w) > 2: their_words[m.group(1).lower()][w] += 1
mention_re = re.compile(r"@([A-Za-z0-9_]{1,15})")
span = {}
for date, text in con.execute("SELECT date, text FROM docs WHERE source='tweets' AND title NOT LIKE '%[RT]%' ORDER BY date"):
    for h in set(x.lower() for x in mention_re.findall(text or "")):
        if h not in span: span[h] = [date, date]
        else: span[h][1] = date
out = {}
for p in reg["personas"]:
    hl = p["handle"].lower()
    interests = [w for w, _ in their_words.get(hl, collections.Counter()).most_common(6)]
    out[p["handle"]] = {"coord": p["coordinate"], "mentions": p["mentions"], "rt": p["retweets"],
                        "words": p["words"], "interests": interests,
                        "first": span.get(hl, [None, None])[0], "last": span.get(hl, [None, None])[1]}
# Will's interest lexicon: top content words of his own authored posts + core signal words
mine = collections.Counter()
for (t,) in con.execute("SELECT text FROM docs WHERE source='tweets' AND title NOT LIKE '%[RT]%'"):
    for w in WORD_RE.findall((t or "").lower()):
        if w not in STOP and len(w) > 2: mine[w] += 1
lex = set(w for w, _ in mine.most_common(300))
CORE = {"phext","exocortex","agi","consciousness","singularity","llm","agents","memory","inference","local","solar","compute","phextura","mirrorborn"}
their_c = {}
for h, c in their_words.items(): their_c[h] = c
mention_counts = collections.Counter()
for (t,) in con.execute("SELECT text FROM docs WHERE source='tweets' AND title NOT LIKE '%[RT]%'"):
    for h in set(x.lower() for x in mention_re.findall(t or "")): mention_counts[h] += 1
follows = {}
for aid, name in con.execute("SELECT account_id, screen_name FROM people WHERE is_follower=0 AND is_following=1"):
    if not name or name in out: continue
    terms = [w for w, _ in their_c.get(name.lower(), collections.Counter()).most_common(6)]
    aligned = len(set(terms) & lex) >= 2 or bool(set(terms) & CORE)
    follows[name] = {"aligned": aligned, "interests": terms, "mentions": mention_counts.get(name.lower(), 0)}
prune = sorted([h for h, v in follows.items() if not v["aligned"]],
               key=lambda h: (1 if follows[h]["interests"] else 0, follows[h]["mentions"], h.lower()))
q = ["# Prune queue — unaligned follows, no follow-back (ranked: no canon interests first, then fewest mentions)", ""]
for i, h in enumerate(prune, 1):
    v = follows[h]
    q.append(f"{i}. @{h} — mentions {v['mentions']}" + (f" — terms: {', '.join(v['interests'])}" if v["interests"] else " — no canon interests"))
pathlib.Path("prune-queue.md").write_text("\n".join(q) + "\n")
print(f"prune pool: {len(prune)}")
canon = {"moots": out, "follows": follows, "prune": prune}
cpath = pathlib.Path(__file__).parent / "data" / "wavefront.json"
cpath.write_text(json.dumps(canon, ensure_ascii=False))
print(f"wavefront.json: {cpath} ({cpath.stat().st_size} bytes)")
js = ("/* Generated from the OG Wavefront canon (personas register + exo.db). Snapshot ends 2025-06-16 — by design: final and historic. */\n"
      "window.GT_MOOTS = " + json.dumps(out, ensure_ascii=False) + ";\n"
      "window.GT_FOLLOWS = " + json.dumps(follows, ensure_ascii=False) + ";\n"
      "window.GT_PRUNE = " + json.dumps(prune, ensure_ascii=False) + ";\n")
print(f"follows: {len(follows)}; aligned: {sum(1 for v in follows.values() if v['aligned'])}")
(pathlib.Path(__file__).parent.parent / "windows/golden-thread-extension/data/moots.js").write_text(js)
with_i = sum(1 for v in out.values() if v["interests"])
print(f"moots: {len(out)}; with interests: {with_i}; bytes: {len(js)}")
