(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const rand = (a, b) => a + Math.random() * (b - a);
  const pad = (n) => String(n).padStart(2, "0");
  const clock = (s) => `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;

  /* ---------- state ---------- */
  const KEY = "ivpn_state_v1";
  const saved = (() => { try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { return {}; } })();
  const state = Object.assign({
    killSwitch: true, autoConnect: false, notifications: true, splitEnabled: true,
    bypass: Object.fromEntries(VPN_DATA.apps.map((a) => [a.id, a.bypass])),
    seen: false, plan: "12m", premium: false, serverId: "in-mum"
  }, saved);
  Object.assign(state, { connected: false, busy: false, seconds: 0, session: null, servers: VPN_DATA.servers, tab: "all", q: "", testing: false, timerId: null });
  const persist = () => {
    const { killSwitch, autoConnect, notifications, splitEnabled, bypass, seen, plan, premium, serverId } = state;
    try { localStorage.setItem(KEY, JSON.stringify({ killSwitch, autoConnect, notifications, splitEnabled, bypass, seen, plan, premium, serverId })); } catch (e) {}
  };
  const server = () => state.servers.find((s) => s.id === state.serverId) || state.servers[0];
  const label = (s) => (s.country === s.name ? s.name : `${s.country} – ${s.name}`);
  const flagHTML = (s) => s.code === "IN" ? '<span class="flag in"></span>' : `<span class="flag" style="background:${s.color || "#3E63DD"}">${s.code}</span>`;

  /* ---------- toast ---------- */
  let toastT;
  function toast(msg) {
    const t = $("#toast"); t.textContent = msg; t.classList.add("show");
    clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove("show"), 2200);
  }

  /* ---------- router ---------- */
  const stack = [];
  let current = null;
  const DEV = [["splash", "Splash"], ["onboarding", "Onboarding"], ["home", "Home"], ["connected", "Connected"], ["servers", "Servers"],
    ["speed", "Speed test"], ["premium", "Premium"], ["settings", "Settings"], ["split", "Split tunneling"], ["profile", "Profile"]];

  function go(id, opts = {}) {
    if (id === "connected") { id = "home"; if (!state.connected) setConnected(true, { ip: "103.87.xx.24" }); }
    if (!opts.back && current && current !== id) stack.push(current);
    current = id;
    $$(".screen").forEach((s) => s.classList.toggle("active", s.id === id));
    const sc = $("#" + id);
    sc.scrollTop = 0;
    const nav = sc.dataset.nav;
    $("#nav").hidden = nav === "none";
    $$("#nav button").forEach((b) => b.classList.toggle("on", b.dataset.go === nav));
    $$("#devList button").forEach((b) => b.classList.toggle("on", b.dataset.id === (id === "home" && state.connected ? "connected" : id)));
    if (id === "servers") renderServers();
    if (id === "speed") $("#speedServer").textContent = label(server());
    if (id === "profile") loadProfile();
    if (id === "premium") renderPlans();
    if (id === "splash") runSplash();
  }
  function back() { const prev = stack.pop(); go(prev || "home", { back: true }); }

  document.addEventListener("click", (e) => {
    const g = e.target.closest("[data-go]"); if (g) return go(g.dataset.go);
    if (e.target.closest("[data-back]")) return back();
    const t = e.target.closest("[data-toast]"); if (t) return toast(t.dataset.toast);
    const sw = e.target.closest(".toggle");
    if (sw) {
      if (sw.dataset.key) state[sw.dataset.key] = !state[sw.dataset.key];
      if (sw.dataset.app) state.bypass[sw.dataset.app] = !state.bypass[sw.dataset.app];
      persist(); syncToggles();
    }
  });

  function syncToggles() {
    $$(".toggle[data-key]").forEach((t) => t.setAttribute("aria-checked", String(!!state[t.dataset.key])));
    $$(".toggle[data-app]").forEach((t) => t.setAttribute("aria-checked", String(!!state.bypass[t.dataset.app])));
  }

  /* ---------- chrome: nav + dev list ---------- */
  const ICONS = {
    home: '<path d="M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
    servers: '<circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/>',
    speed: '<path d="M3 18a9 9 0 1 1 18 0"/><path d="M12 15l4-6"/>',
    settings: '<path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/>'
  };
  $("#nav").innerHTML = [["home", "Home"], ["servers", "Servers"], ["speed", "Speed"], ["settings", "Settings"]]
    .map(([id, l]) => `<button data-go="${id}"><svg viewBox="0 0 24 24" aria-hidden="true">${ICONS[id]}</svg>${l}</button>`).join("");
  $("#devList").innerHTML = DEV.map(([id, l]) => `<button data-id="${id}">${l}</button>`).join("");
  $("#devList").addEventListener("click", (e) => { const b = e.target.closest("button"); if (b) { stack.length = 0; if (b.dataset.id === "home" && state.connected) setConnected(false); go(b.dataset.id); } });

  /* ---------- splash + onboarding ---------- */
  let splashT;
  function runSplash() {
    clearTimeout(splashT);
    splashT = setTimeout(() => { if (current !== "splash") return; stack.length = 0; go(state.seen ? "home" : "onboarding"); if (state.seen && state.autoConnect) toggleConnect(); }, 1900);
  }
  let ob = 0;
  function renderOb() {
    const s = VPN_DATA.onboarding[ob];
    $("#obIcon").innerHTML = s.icon; $("#obTitle").textContent = s.title; $("#obText").textContent = s.text;
    $("#obDots").innerHTML = VPN_DATA.onboarding.map((_, i) => `<i class="${i === ob ? "on" : ""}"></i>`).join("");
    $("#obNext").textContent = ob === VPN_DATA.onboarding.length - 1 ? "Get started" : "Next";
  }
  $("#obNext").addEventListener("click", () => {
    if (ob < VPN_DATA.onboarding.length - 1) { ob++; renderOb(); }
    else { state.seen = true; persist(); ob = 0; renderOb(); go("home"); }
  });
  $("#onboarding .skip").addEventListener("click", () => { state.seen = true; persist(); });

  /* ---------- home / connect ---------- */
  function renderHomeServer() {
    const s = server();
    $("#homeFlag").outerHTML = flagHTML(s).replace("<span", '<span id="homeFlag"');
    $("#homeServer").textContent = label(s);
    $("#homeServerSub").textContent = state.connected ? `Your IP ${state.session ? state.session.ip : "–"}` : `${s.id === "in-mum" ? "Fastest server" : "Selected server"} · ${s.ping} ms`;
    $("#homeChev").hidden = state.connected; $("#homeChange").hidden = !state.connected;
  }
  const graph = Array.from({ length: 18 }, () => rand(8, 30));
  function drawGraph() {
    const box = $("#bars");
    if (!box.children.length) box.innerHTML = graph.map(() => "<i></i>").join("");
    [...box.children].forEach((b, i) => (b.style.height = graph[i] + "px"));
  }
  function setConnected(on, session) {
    state.connected = on; state.session = on ? session : null; state.seconds = 0;
    clearInterval(state.timerId);
    const home = $("#home");
    home.classList.toggle("connected", on);
    home.classList.remove("connecting");
    $("#statusTitle").textContent = on ? "Connected" : "Not connected";
    $("#statusSub").textContent = on ? "Your connection is secure" : "Your connection is not protected";
    $("#hint").textContent = on ? "Tap to disconnect" : "Tap to connect";
    $("#connectBtn").setAttribute("aria-label", on ? "Disconnect" : "Connect");
    $("#thirdLabel").textContent = on ? "Ping" : "Duration";
    $("#third").textContent = on ? `${server().ping} ms` : "00:00:00";
    $("#graphCard").hidden = !on; $("#homeToggles").hidden = on;
    $("#timer").textContent = "00:00:00";
    $("#dl").textContent = $("#ul").textContent = "0 KB/s";
    if (on) {
      const tick = () => {
        $("#timer").textContent = clock(state.seconds);
        $("#dl").textContent = rand(3.5, 5.2).toFixed(1) + " MB/s";
        $("#ul").textContent = rand(0.8, 1.4).toFixed(1) + " MB/s";
        graph.push(rand(12, 52)); graph.shift(); drawGraph();
        state.seconds++;
      };
      tick(); state.timerId = setInterval(tick, 1000);
    }
    renderHomeServer();
    $$("#devList button").forEach((b) => b.classList.toggle("on", b.dataset.id === (current === "home" && on ? "connected" : current)));
  }
  async function toggleConnect() {
    if (state.busy) return;
    state.busy = true;
    const home = $("#home");
    try {
      if (!state.connected) {
        home.classList.add("connecting");
        $("#statusTitle").textContent = "Connecting…"; $("#statusSub").textContent = label(server());
        const session = await api.connect(server().id);
        setConnected(true, session);
      } else {
        await api.disconnect(state.session && state.session.sessionId);
        setConnected(false);
      }
    } catch (err) {
      setConnected(false); toast(err.message || "Could not connect. Check your network and try again.");
    } finally { state.busy = false; }
  }
  $("#connectBtn").addEventListener("click", toggleConnect);

  /* ---------- servers ---------- */
  const TABS = [["all", "All"], ["india", "India"], ["streaming", "Streaming"], ["gaming", "Gaming"]];
  function renderServers() {
    $("#tabs").innerHTML = TABS.map(([id, l]) => `<button data-tab="${id}" class="${state.tab === id ? "on" : ""}">${l}</button>`).join("");
    const q = state.q.trim().toLowerCase();
    const list = state.servers.filter((s) =>
      (state.tab === "all" || (state.tab === "india" ? s.country === "India" : s.tags.includes(state.tab))) &&
      (!q || (s.name + " " + s.country).toLowerCase().includes(q)));
    const row = (s) => `<button class="row ${s.id === state.serverId ? "sel" : ""}" data-server="${s.id}">${flagHTML(s)}
      <span class="txt"><b>${s.name}</b><small>${s.country} · ${s.ping} ms</small></span>
      <span class="sig">${[0, 1, 2, 3].map((i) => `<i class="${i < s.bars ? "on" : ""}" style="height:${i * 3 + 4}px"></i>`).join("")}</span><span class="radio"></span></button>`;
    const ind = list.filter((s) => s.country === "India"), oth = list.filter((s) => s.country !== "India");
    $("#serverList").innerHTML = !list.length ? '<p class="empty">No servers match. Try another city or country.</p>' :
      (ind.length ? `<p class="section">India</p>${ind.map(row).join("")}` : "") + (oth.length ? `<p class="section">Other countries</p>${oth.map(row).join("")}` : "");
  }
  $("#tabs").addEventListener("click", (e) => { const b = e.target.closest("[data-tab]"); if (b) { state.tab = b.dataset.tab; renderServers(); } });
  $("#q").addEventListener("input", (e) => { state.q = e.target.value; renderServers(); });
  $("#serverList").addEventListener("click", async (e) => {
    const b = e.target.closest("[data-server]"); if (!b) return;
    state.serverId = b.dataset.server; persist(); renderHomeServer();
    stack.length = 0; go("home");
    if (state.connected) { toast("Switching server…"); setConnected(false); await toggleConnect(); }
  });

  /* ---------- speed test ---------- */
  function animate(from, to, ms, cb) {
    return new Promise((res) => {
      const t0 = performance.now();
      (function step(t) {
        const p = Math.min((t - t0) / ms, 1), e = 1 - Math.pow(1 - p, 3);
        cb(from + (to - from) * e + (p < 1 ? rand(-1.5, 1.5) : 0)); p < 1 ? requestAnimationFrame(step) : res();
      })(t0);
    });
  }
  const gauge = (v) => { $("#gnum").textContent = Math.max(v, 0).toFixed(1); $("#gval").setAttribute("stroke-dasharray", `${471.2 * Math.min(Math.max(v, 0) / 100, 1)} 628.3`); };
  $("#speedBtn").addEventListener("click", async () => {
    if (state.testing) return;
    state.testing = true;
    const btn = $("#speedBtn"); btn.textContent = "Testing…"; btn.disabled = true; btn.style.opacity = ".6";
    ["sPing", "sDl", "sUl", "sJit", "sLoss"].forEach((id) => ($("#" + id).textContent = "–")); gauge(0);
    $("#gphase").textContent = "Checking ping"; await sleep(700);
    $("#sPing").textContent = Math.round(server().ping + rand(-2, 3)) + " ms";
    const dl = rand(40, 60), ul = rand(18, 28);
    $("#gphase").textContent = "Download"; await animate(0, dl, 2400, gauge); $("#sDl").textContent = dl.toFixed(1);
    $("#gphase").textContent = "Upload"; await animate(dl, ul, 1900, gauge); $("#sUl").textContent = ul.toFixed(1);
    gauge(dl); $("#sJit").textContent = Math.round(rand(2, 5)) + " ms"; $("#sLoss").textContent = "0%";
    $("#gphase").textContent = "Done";
    btn.textContent = "Test again"; btn.disabled = false; btn.style.opacity = ""; state.testing = false;
  });

  /* ---------- premium ---------- */
  async function renderPlans() {
    const plans = await api.getPlans();
    $("#planList").innerHTML = plans.map((p) => `<button class="plan ${p.id === state.plan ? "sel" : ""}" data-plan="${p.id}">
      <span class="radio"></span><span class="txt"><b>${p.title}</b><small>${p.sub}</small></span><span class="price">₹${p.price}</span>
      ${p.best ? '<span class="badge">Best value</span>' : ""}</button>`).join("");
  }
  $("#planList").addEventListener("click", (e) => { const b = e.target.closest("[data-plan]"); if (b) { state.plan = b.dataset.plan; persist(); renderPlans(); } });
  $("#planBtn").addEventListener("click", async () => {
    const btn = $("#planBtn"); btn.disabled = true;
    try { await api.subscribe(state.plan); state.premium = true; persist(); applyPlan(); toast("Premium is on (demo)"); setTimeout(back, 700); }
    catch (err) { toast(err.message || "Payment failed. Try again."); }
    finally { btn.disabled = false; }
  });
  function applyPlan() {
    const p = $("#planPill");
    p.textContent = state.premium ? "Premium" : "Upgrade"; p.classList.toggle("ok", state.premium);
    $("#setPlan").textContent = $("#pPlan").textContent = state.premium ? "Premium plan" : "Free plan";
  }

  /* ---------- split tunneling + profile ---------- */
  $("#appList").innerHTML = VPN_DATA.apps.map((a) => `<div class="app"><i style="background:${a.color}">${a.letter}</i><span>${a.name}</span>
    <button class="toggle" role="switch" aria-label="${a.name} skips VPN" data-app="${a.id}"></button></div>`).join("");
  async function loadProfile() {
    try {
      const p = await api.getProfile();
      $("#pName").textContent = $("#setName").textContent = p.name;
      $("#pAvatar").textContent = $("#setAvatar").textContent = p.initials;
      $("#pData").textContent = p.dataUsedGb + " GB"; $("#pSess").textContent = p.sessions; $("#pDev").textContent = `${p.devices.length} / ${p.deviceLimit}`;
      $("#devices").innerHTML = p.devices.map((d) => `<div class="dev-row"><i class="dv"></i><span><b>${d.name}</b><small>${d.note}</small></span></div>`).join("");
    } catch (err) { toast(err.message || "Could not load your account."); }
  }

  /* ---------- boot ---------- */
  renderOb(); drawGraph(); renderHomeServer(); applyPlan(); syncToggles();
  const hash = location.hash.slice(1);
  go(DEV.some(([id]) => id === hash) && hash !== "splash" ? hash : "splash");
})();
