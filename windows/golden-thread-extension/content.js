// Golden Thread — content script v1.1.
// Castes: moots in gold; aligned follows in RGB backlit optimism;
// unaligned follows in muted greyscale; the rest, half-erased (BTTF).
(() => {
  const MOOTS = window.GT_MOOTS || {};
  const FOLLOWS = window.GT_FOLLOWS || {};
  const SELF = "wbic16";
  const lower = o => { const m = {}; for (const h of Object.keys(o)) m[h.toLowerCase()] = h; return m; };
  const mootBy = lower(MOOTS), followBy = lower(FOLLOWS);

  const css = document.createElement("style");
  css.textContent = `
    .gt-moot-name { color: #d4a017 !important; text-shadow: 0 0 6px rgba(212,160,23,.45); }
    article.gt-moot-post { box-shadow: inset 3px 0 0 #d4a017; background: linear-gradient(90deg, rgba(212,160,23,.07), transparent 55%); }
    .gt-badge { display:inline-block; margin-left:6px; padding:0 6px; border:1px solid #d4a017; border-radius:999px;
      color:#d4a017; font-size:11px; font-weight:700; vertical-align:middle; }
    article.gt-aligned-post { background: linear-gradient(90deg, rgba(255,90,90,.10), rgba(90,255,130,.10) 45%, rgba(90,130,255,.10));
      box-shadow: inset 3px 0 0 #7ac74f, 0 0 22px rgba(140,180,255,.25); }
    .gt-aligned-name { text-shadow: -1px 0 rgba(255,60,60,.8), 1px 0 rgba(60,120,255,.8), 0 0 14px rgba(120,255,170,.65) !important; }
    .gt-aligned-badge { border-color:#7ac74f !important; color:#aef0a0 !important; }
    article.gt-muted-post { filter: grayscale(.92) opacity(.72); }
    .gt-muted-name { color:#8a8a8a !important; }
    article.gt-chaff-post { filter: grayscale(.55) blur(.3px); opacity:.48; animation: gt-flicker 9s infinite; }
    article.gt-chaff-post:hover { opacity:.82; animation: none; }
    @keyframes gt-flicker { 0%,100% {opacity:.48} 88% {opacity:.48} 90% {opacity:.30} 92% {opacity:.52} 94% {opacity:.36} 96% {opacity:.48} }
    .gt-unfollow { margin-left:6px; padding:0 8px; border:1px solid #8a8a8a; border-radius:999px; background:transparent;
      color:#aaa; font-size:11px; font-weight:700; cursor:pointer; vertical-align:middle; }
    .gt-unfollow:hover { border-color:#e5534b; color:#e5534b; }
    #gt-tip { position:fixed; z-index:99999; max-width:320px; background:#14100a; color:#f3e5c0; border:1px solid #d4a017;
      border-radius:10px; padding:10px 12px; font:13px/1.45 -apple-system, "Segoe UI", Roboto, sans-serif;
      box-shadow:0 8px 30px rgba(0,0,0,.5); pointer-events:none; }
    #gt-tip b { color:#ffd968; } #gt-tip .gt-dim { color:#b8a888; font-size:12px; }
    #gt-fx { position:fixed; inset:0; z-index:99998; pointer-events:none; }`;
  document.documentElement.appendChild(css);

  // ---- fireworks (gold, for moots) ----
  let fxCanvas = null, fxCtx = null, particles = [], fxRunning = false;
  function burst(x, y, colors) {
    if (!fxCanvas) {
      fxCanvas = document.createElement("canvas"); fxCanvas.id = "gt-fx";
      fxCanvas.width = innerWidth; fxCanvas.height = innerHeight;
      document.documentElement.appendChild(fxCanvas); fxCtx = fxCanvas.getContext("2d");
    }
    colors = colors || ["#ffd968", "#d4a017", "#fff3c4", "#ffb347"];
    for (let i = 0; i < 42; i++) {
      const a = Math.random() * Math.PI * 2, sp = 1.5 + Math.random() * 4.5;
      particles.push({ x, y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp - 1.5, life: 1, color: colors[i % colors.length], r: 1 + Math.random() * 2 });
    }
    if (!fxRunning) { fxRunning = true; requestAnimationFrame(tickFx); }
  }
  function tickFx() {
    fxCtx.clearRect(0, 0, fxCanvas.width, fxCanvas.height);
    particles = particles.filter(p => p.life > 0);
    for (const p of particles) {
      p.x += p.vx; p.y += p.vy; p.vy += 0.06; p.vx *= 0.985; p.vy *= 0.985; p.life -= 0.016;
      fxCtx.globalAlpha = Math.max(p.life, 0); fxCtx.fillStyle = p.color;
      fxCtx.beginPath(); fxCtx.arc(p.x, p.y, p.r, 0, Math.PI * 2); fxCtx.fill();
    }
    fxCtx.globalAlpha = 1;
    if (particles.length) requestAnimationFrame(tickFx);
    else { fxRunning = false; fxCtx.clearRect(0, 0, fxCanvas.width, fxCanvas.height); }
  }

  const esc = v => String(v ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  // ---- tooltip ----
  let tip = null;
  function showTip(html, x, y) {
    hideTip();
    tip = document.createElement("div"); tip.id = "gt-tip"; tip.innerHTML = html;
    document.documentElement.appendChild(tip);
    tip.style.left = Math.min(x + 14, innerWidth - 340) + "px";
    tip.style.top = Math.min(y + 16, innerHeight - 170) + "px";
  }
  function hideTip() { if (tip) { tip.remove(); tip = null; } }

  const safeSend = msg => { try { const r = chrome.runtime.sendMessage(msg); if (r && r.catch) r.catch(() => {}); } catch (e) {} };
  const seen = new Set();
  function logEncounter(handle, cls) {
    if (seen.has(handle)) return; seen.add(handle);
    safeSend({ type: "encounter", handle, cls, url: location.href, context: document.title });
  }
  function badge(text, cls) {
    const b = document.createElement("span"); b.className = "gt-badge " + (cls || ""); b.textContent = text; return b;
  }

  // ---- un-follow button: simply opens the profile in a new window (Will's call) ----
  function unfollowButton(handle) {
    const btn = document.createElement("button");
    btn.className = "gt-unfollow"; btn.textContent = "un-follow"; btn.type = "button";
    btn.title = "Open their profile in a new window to un-follow";
    btn.addEventListener("click", ev => {
      ev.preventDefault(); ev.stopPropagation();
      safeSend({ type: "unfollow", handle, intent: true, url: location.href });
      window.open("https://x.com/" + handle, "_blank");
    });
    return btn;
  }

  function mark(root) {
    const containers = new Set(root.querySelectorAll ? root.querySelectorAll('div[data-testid="User-Name"]') : []);
    if (root.matches && root.matches('div[data-testid="User-Name"]')) containers.add(root);
    const anchors = [];
    containers.forEach(c => c.querySelectorAll('a[href^="/"]').forEach(a => anchors.push(a)));
    anchors.forEach(a => {
      const m = /^\/([A-Za-z0-9_]{1,15})$/.exec(a.getAttribute("href") || "");
      if (!m || a.dataset.gt) return;
      const raw = m[1], hl = raw.toLowerCase();
      if (hl === SELF) { a.dataset.gt = "self"; return; }
      a.dataset.gt = "1";
      const art = a.closest("article");
      const parent = a.parentElement;
      if (mootBy[hl]) {
        const handle = mootBy[hl], d = MOOTS[handle];
        a.classList.add("gt-moot-name");
        if (parent) parent.appendChild(badge("✦ moot"));
        if (art) art.classList.add("gt-moot-post");
        a.addEventListener("mouseenter", ev => {
          burst(ev.clientX, ev.clientY);
          const ints = d.interests && d.interests.length ? d.interests.join(" · ") : "quiet in the canon — no public topics captured";
          showTip(`<b>@${esc(handle)}</b> <span class="gt-dim">moot · ${esc(d.coord || "no coordinate yet")}</span><br>` +
            `Into (canon): <b>${esc(ints)}</b><br>` +
            `<span class="gt-dim">${d.mentions} of Will's posts mention them · ${d.rt} retweeted · in the thread since ${d.first || "—"}</span><br>` +
            `<span class="gt-dim">Encounter logged for review.</span>`, ev.clientX, ev.clientY);
          logEncounter(handle, "moot");
        });
        a.addEventListener("mouseleave", hideTip);
      } else if (followBy[hl]) {
        const handle = followBy[hl], d = FOLLOWS[handle];
        if (d.aligned) {
          a.classList.add("gt-aligned-name");
          if (parent) parent.appendChild(badge("✦ aligned", "gt-aligned-badge"));
          if (art) art.classList.add("gt-aligned-post");
          a.addEventListener("mouseenter", ev => {
            burst(ev.clientX, ev.clientY, ["#ff6b6b", "#7ac74f", "#6ba8ff", "#ffe66d"]);
            showTip(`<b>@${esc(handle)}</b> <span class="gt-dim">you follow · no follow-back — yet</span><br>` +
              `Aligned interests (canon): <b>${esc(d.interests.join(" · "))}</b><br>` +
              `<span class="gt-dim">RGB backlit: their field is your field. Encounter logged.</span>`, ev.clientX, ev.clientY);
            logEncounter(handle, "aligned");
          });
          a.addEventListener("mouseleave", hideTip);
        } else {
          a.classList.add("gt-muted-name");
          if (parent) { parent.appendChild(badge("no follow-back")); parent.appendChild(unfollowButton(handle)); }
          if (art) art.classList.add("gt-muted-post");
          a.addEventListener("mouseenter", ev => {
            showTip(`<b>@${esc(handle)}</b> <span class="gt-dim">you follow · no follow-back</span><br>` +
              `<span class="gt-dim">No aligned interests in canon evidence — muted.</span>`, ev.clientX, ev.clientY);
          });
          a.addEventListener("mouseleave", hideTip);
        }
      } else {
        // the chaff: half-erased from existence — but never downgrade an
        // article already claimed by its own author (quoted-post case)
        if (art && !art.className.includes("gt-")) art.classList.add("gt-chaff-post");
      }
    });
  }
  addEventListener("resize", () => { if (fxCanvas) { fxCanvas.remove(); fxCanvas = null; particles = []; } });
  mark(document);
  new MutationObserver(ms => ms.forEach(m => m.addedNodes.forEach(n => n.nodeType === 1 && mark(n)))).observe(document.body, { childList: true, subtree: true });
})();
