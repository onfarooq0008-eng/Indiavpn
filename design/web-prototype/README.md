# India VPN – Frontend

Clickable prototype of the 12 screens from the Figma file:
https://www.figma.com/design/aQuKEMhKXG4dpj0ogxy4wG

## Run
Open `index.html` in a browser (or `python3 -m http.server 8080`, then http://localhost:8080).
On desktop you get a phone frame with a screen list; on a phone it fills the screen.
Deep link to a screen: `index.html#servers`, `#speed`, `#premium`, `#settings`, `#split`, `#profile`, `#connected`.

## Figma frame -> screen
01 Splash `#splash` · 02-04 Onboarding `#onboarding` · 05 Home `#home` · 06 Servers `#servers` · 07 Connected `#connected`
08 Speed test `#speed` · 09 Premium `#premium` · 10 Settings `#settings` · 11 Split tunneling `#split` · 12 Profile `#profile`

## Structure
- `css/tokens.css` colors, radii, font (same values as `design/tokens.json`)
- `css/styles.css` all components and screens
- `js/data.js` demo servers, plans, apps, onboarding copy
- `js/api.js` API layer. Set `API_CONFIG.mock = false` to talk to the backend
- `js/app.js` routing, connect flow, server search, speed test, plans, settings
- `docs/API_CONTRACT.md` endpoints the backend should provide

All connect, speed test and payment behavior is simulated until the backend exists.
