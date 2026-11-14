// Golden Thread — service worker: encounter log + SRS badge.
const ENC_KEY = "gt_encounters", SRS_KEY = "gt_srs";
async function get(k, d) { const r = await chrome.storage.local.get(k); return r[k] ?? d; }
async function set(k, v) { return chrome.storage.local.set({ [k]: v }); }

async function refreshBadge() {
  const srs = await get(SRS_KEY, {});
  const now = Date.now();
  const due = Object.values(srs).filter(c => c.due <= now).length;
  chrome.action.setBadgeText({ text: due ? String(due) : "" });
  chrome.action.setBadgeBackgroundColor({ color: "#d4a017" });
}
chrome.runtime.onInstalled.addListener(refreshBadge);
chrome.alarms.get("gt-badge", a => { if (!a) chrome.alarms.create("gt-badge", { periodInMinutes: 60 }); });
chrome.alarms.onAlarm.addListener(refreshBadge);

chrome.runtime.onMessage.addListener((msg, sender, reply) => {
  (async () => {
    if (msg.type === "encounter") {
      const log = await get(ENC_KEY, []);
      const last = log[log.length - 1];
      // dedupe: same moot within 5 minutes counts once
      if (!(last && last.handle === msg.handle && Date.now() - last.ts < 300000)) {
        log.push({ handle: msg.handle, ts: Date.now(), url: msg.url, context: msg.context || "", cls: msg.cls || "" });
        await set(ENC_KEY, log.slice(-500));
      }
      reply({ ok: true, total: log.length });
    } else if (msg.type === "unfollow") {
      const log = await get(ENC_KEY, []);
      log.push({ handle: msg.handle, ts: Date.now(), url: msg.url, cls: "unfollow", ok: !!msg.ok, intent: !!msg.intent });
      await set(ENC_KEY, log.slice(-500));
      reply({ ok: true });
    } else if (msg.type === "grade") {
      const srs = await get(SRS_KEY, {});
      const c = srs[msg.handle] || { interval: 0, reps: 0 };
      if (msg.correct) { c.interval = c.interval === 0 ? 1 : Math.ceil(c.interval * 2.5); c.reps++; }
      else { c.interval = 1; c.reps = 0; }
      c.due = Date.now() + c.interval * 86400000;
      srs[msg.handle] = c;
      await set(SRS_KEY, srs); await refreshBadge();
      reply({ ok: true, card: c });
    } else if (msg.type === "state") {
      reply({ encounters: await get(ENC_KEY, []), srs: await get(SRS_KEY, {}) });
    } else if (msg.type === "clear-encounters") {
      await set(ENC_KEY, []); reply({ ok: true });
    }
  })();
  return true;
});
