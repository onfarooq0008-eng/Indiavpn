# India VPN – API contract (frontend expects this)

Base URL: `API_CONFIG.baseUrl` in `js/api.js` (default `http://localhost:3000/api`).
All requests send `Authorization: Bearer <token>` once login exists. Errors: `{ "message": "..." }`.

| Method | Path | Body | Response |
|---|---|---|---|
| GET  | /servers | – | `[{ id, name, country, code, ping, bars, tags[] }]` |
| POST | /vpn/connect | `{ serverId }` | `{ sessionId, ip }` |
| POST | /vpn/disconnect | `{ sessionId }` | `{ ok: true }` |
| GET  | /plans | – | `[{ id, title, sub, price, best? }]` (price in INR) |
| POST | /subscriptions | `{ planId }` | `{ ok: true, planId }` |
| GET  | /me | – | `{ name, initials, plan, dataUsedGb, sessions, devices[{name,note}], deviceLimit }` |

Planned for the backend step: `POST /auth/login`, `POST /auth/register`, Google Play purchase verification,
WireGuard/OpenVPN config issuing per session, usage metering.
