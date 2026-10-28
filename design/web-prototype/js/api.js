/* API layer. Set mock:false and baseUrl when the backend is ready.
   Endpoint contract: see docs/API_CONTRACT.md */
window.API_CONFIG = { baseUrl: "http://localhost:3000/api", mock: true };

(function () {
  const delay = (ms) => new Promise((r) => setTimeout(r, ms));
  const token = () => { try { return localStorage.getItem("ivpn_token"); } catch (e) { return null; } };

  async function http(path, opts = {}) {
    const res = await fetch(API_CONFIG.baseUrl + path, {
      ...opts,
      headers: { "Content-Type": "application/json", ...(token() ? { Authorization: "Bearer " + token() } : {}), ...(opts.headers || {}) },
      body: opts.body ? JSON.stringify(opts.body) : undefined
    });
    if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || "Request failed (" + res.status + ")");
    return res.json();
  }

  window.api = {
    async getServers() {
      if (API_CONFIG.mock) { await delay(150); return VPN_DATA.servers; }
      return http("/servers");
    },
    async connect(serverId) {
      if (API_CONFIG.mock) { await delay(1300); return { sessionId: "mock-" + Date.now(), ip: "103.87." + (10 + Math.floor(Math.random() * 80)) + ".24" }; }
      return http("/vpn/connect", { method: "POST", body: { serverId } });
    },
    async disconnect(sessionId) {
      if (API_CONFIG.mock) { await delay(300); return { ok: true }; }
      return http("/vpn/disconnect", { method: "POST", body: { sessionId } });
    },
    async getPlans() {
      if (API_CONFIG.mock) return VPN_DATA.plans;
      return http("/plans");
    },
    async subscribe(planId) {
      if (API_CONFIG.mock) { await delay(600); return { ok: true, planId }; }
      return http("/subscriptions", { method: "POST", body: { planId } });
    },
    async getProfile() {
      if (API_CONFIG.mock) return { name: "Guest User", initials: "GU", plan: "Free plan", dataUsedGb: 12.4, sessions: 86, devices: [
        { name: "Android phone", note: "This device · Active now" }, { name: "Android tablet", note: "Last seen 2 days ago" }], deviceLimit: 5 };
      return http("/me");
    }
  };
})();
