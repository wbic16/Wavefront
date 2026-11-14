// Golden Thread — popup: spaced-repetition review + encounter log.
const MOOTS = window.GT_MOOTS || {};
const withInterests = Object.keys(MOOTS).filter(h => MOOTS[h].interests && MOOTS[h].interests.length);
let state = { encounters: [], srs: {} };
const $ = s => document.querySelector(s);
const esc = v => String(v ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

document.querySelectorAll("nav button").forEach(b => b.onclick = () => {
  document.querySelectorAll("section").forEach(s => s.classList.toggle("on", s.id === b.dataset.tab));
});

function dueCards() {
  const now = Date.now();
  return withInterests.filter(h => { const c = state.srs[h]; return !c || c.due <= now; });
}
function drawQuiz() {
  const due = dueCards();
  const box = $("#quiz");
  if (!withInterests.length) { box.innerHTML = "<span class='dim'>No interest cards in the canon yet.</span>"; return; }
  if (!due.length) { box.innerHTML = `<div class="card">All caught up ✦<br><span class="dim">${withInterests.length} moots in the deck; nothing due right now.</span></div>`; renderDeck(); return; }
  const handle = due[Math.floor(Math.random() * due.length)];
  const mine = MOOTS[handle].interests;
  const others = withInterests.filter(h => h !== handle).sort(() => Math.random() - .5).slice(0, 3)
    .map(h => MOOTS[h].interests[0]);
  const opts = [...new Set([mine[0], ...others])].sort(() => Math.random() - .5);
  box.innerHTML = `<div class="card"><b>@${esc(handle)}</b> — what have they been into?<br>` +
    `<span class="dim">Their wider field: ${esc(mine.slice(1).join(" · ")) || "—"}</span>` +
    opts.map(o => `<button class="opt" data-o="${esc(o)}">${esc(o)}</button>`).join("") +
    `<div class="dim" id="quiz-msg"></div></div>`;
  box.querySelectorAll(".opt").forEach(btn => btn.onclick = async () => {
    const correct = btn.dataset.o === mine[0];
    btn.classList.add(correct ? "right" : "wrong");
    $("#quiz-msg").textContent = correct ? "✦ Right — interval grows." : `Not quite — it's “${mine[0]}”. Back to a 1-day interval.`;
    await chrome.runtime.sendMessage({ type: "grade", handle, correct });
    state = await chrome.runtime.sendMessage({ type: "state" });
    setTimeout(drawQuiz, 1100);
  });
}
function renderEnc() {
  const enc = state.encounters || [];
  $("#enc-summary").textContent = `${enc.length} encounters logged`;
  $("#enc-list").innerHTML = enc.slice(-12).reverse().map(e =>
    `<li><b>@${esc(e.handle)}</b> <span class="dim">${new Date(e.ts).toLocaleString()}</span></li>`).join("");
}
function renderDeck() {
  const F = window.GT_FOLLOWS || {};
  const aligned = Object.values(F).filter(v => v.aligned).length;
  const legend = `<div class="card"><b>The four castes</b><br>
    ✦ <b>Gold</b> — moots: ${Object.keys(MOOTS).length}<br>
    ✦ <span style="color:#aef0a0">RGB backlit</span> — aligned follows, no follow-back yet: ${aligned}<br>
    <span style="color:#8a8a8a">Muted greyscale</span> — follows without aligned interests in evidence: ${Object.keys(F).length - aligned}<br>
    <span style="opacity:.55">Half-erased</span> — everyone else (the chaff)</div>`;
  $("#deck-stats").innerHTML = legend;
  const now = Date.now();
  const learned = Object.keys(state.srs).length;
  const due = dueCards().length;
  $("#deck-stats").innerHTML = legend + `<div class="card">Deck: <b>${withInterests.length}</b> moots with canon interests<br>` +
    `In rotation: <b>${learned}</b> · Due now: <b>${due}</b><br>` +
    `<span class="dim">Intervals double-ish on recall (1d → 3d → 8d → 20d…), reset on a miss.</span></div>`;
}
$("#enc-export").onclick = () => {
  const blob = new Blob([JSON.stringify(state.encounters, null, 1)], { type: "application/json" });
  const a = document.createElement("a"); a.href = URL.createObjectURL(blob);
  a.download = "golden-thread-encounters.json"; a.click();
};
$("#enc-fetch").onclick = async () => {
  const uniq = [...new Set((state.encounters || []).map(e => e.handle))];
  await navigator.clipboard.writeText("Fetch latest for: " + uniq.map(h => "@" + h).join(", "));
  $("#enc-summary").textContent = "Fetch-latest list copied — hand it to Emise.";
};
$("#enc-clear").onclick = async () => {
  await chrome.runtime.sendMessage({ type: "clear-encounters" });
  state = await chrome.runtime.sendMessage({ type: "state" }); renderEnc();
};
// ---- daily prune: 10 suggestions, persisted decisions ----
const PRUNE = window.GT_PRUNE || [];
let pruneState = { day: "", today: [], decisions: {} };
function todayStr() { const d = new Date(); return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0"); }
async function loadPrune() {
  const r = await chrome.storage.local.get("gt_prune");
  if (r.gt_prune) pruneState = r.gt_prune;
  if (pruneState.day !== todayStr()) {
    const now = Date.now();
    const blocked = h => { const d = pruneState.decisions[h];
      return d && (d.a === "unfollowed" || d.a === "keep" || (d.a === "snooze" && d.until > now)); };
    pruneState.today = PRUNE.filter(h => !blocked(h)).slice(0, 10);
    pruneState.day = todayStr();
    await chrome.storage.local.set({ gt_prune: pruneState });
  }
  renderPrune();
}
function renderPrune() {
  const F = window.GT_FOLLOWS || {};
  const decided = pruneState.today.filter(h => pruneState.decisions[h] && pruneState.decisions[h].day === pruneState.day).length;
  const remaining = PRUNE.filter(h => { const d = pruneState.decisions[h];
    return !(d && (d.a === "unfollowed" || d.a === "keep" || (d.a === "snooze" && d.until > Date.now()))); }).length;
  $("#prune-head").textContent = `Today's 10 · ${decided}/10 decided · pool after today: ${remaining}`;
  $("#prune-list").innerHTML = pruneState.today.map(h => {
    const v = F[h] || {}; const d = pruneState.decisions[h];
    const done = d && d.day === pruneState.day;
    const reason = `no follow-back · mentioned ${v.mentions ?? 0}× · ` + (v.interests && v.interests.length ? "terms: " + v.interests.join(", ") : "no canon interests");
    return `<div class="card"><b>@${esc(h)}</b> <span class="dim">${esc(reason)}</span><br>` +
      (done ? `<span class="dim">— ${d.a}</span>` :
      `<button data-act="open" data-h="${h}">profile ↗</button>
       <button data-act="unfollowed" data-h="${h}">un-followed ✓</button>
       <button data-act="keep" data-h="${h}">keep</button>
       <button data-act="snooze" data-h="${h}">snooze 30d</button>`) + `</div>`;
  }).join("");
  document.querySelectorAll("#prune-list button").forEach(btn => btn.onclick = async () => {
    const h = btn.dataset.h, act = btn.dataset.act;
    if (act === "open") { chrome.tabs.create({ url: "https://x.com/" + h }); return; }
    pruneState.decisions[h] = { a: act, day: pruneState.day, ts: Date.now(), until: act === "snooze" ? Date.now() + 30 * 86400000 : 0 };
    await chrome.storage.local.set({ gt_prune: pruneState });
    renderPrune();
  });
}
(async () => { state = await chrome.runtime.sendMessage({ type: "state" }); drawQuiz(); renderEnc(); renderDeck(); loadPrune(); })();
