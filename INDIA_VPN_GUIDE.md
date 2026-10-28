# India VPN – setup guide

This project is your FastVPN Android app + control API, re-skinned as **India VPN**:

| Part | What changed |
|---|---|
| Android theme | Navy / saffron / green palette (dark + light) from the Figma design. Power ring, tabs, logo tile and status colors follow it |
| Branding | App name "India VPN", notification, settings, consent screen, new tricolor launcher icon |
| Server list | India servers are pinned to the top of the Locations list; other countries stay A-Z |
| Backend pages | Landing page, privacy, terms, admin dashboard and favicon/share image renamed and recolored |
| CI | `.github/workflows/build.yml`: debug APK, signed release APK, signed AAB |

Package id is now **`com.indiavpn.fast`** (namespace and applicationId in `app/build.gradle`, source folders `com/indiavpn/fast`).
Still unchanged: backend URL `https://api.fastvpnn.pp.ua` (`DEFAULT_BACKEND_API_URL`), contact email, WireGuard code.

Because the package id is new, this is a **new app** for Google Play and for Yandex Ads:

- Create a new app in Play Console with id `com.indiavpn.fast`. It cannot update an old `com.fastvpnn.app` listing.
- In the Yandex Partner dashboard, add the app `com.indiavpn.fast` and create its banner, native and interstitial units, then put the new
  IDs in `app/src/main/java/com/indiavpn/fast/ads/AdConfig.kt`. The current IDs belong to the old app and may not fill for the new one.
- The Play button on the landing page already points to `id=com.indiavpn.fast`; it shows "Coming soon" until the listing is live.
- Users of an older `com.fastvpnn.app` install would need to install India VPN as a separate app.

## 1. Backend with Indian servers

On the API (brain) VPS, once: `sudo bash backend/setup.sh --role api --domain api.yourdomain.com`

On every VPS that should appear as an Indian server:

```bash
sudo bash setup.sh --role node --api-url https://api.yourdomain.com --admin-key <ADMIN_KEY> \
  --country "India" --country-code IN --city Mumbai
```

Repeat with `--city "New Delhi"`, `Bangalore`, `Chennai`, `Hyderabad`, `Kolkata`. Servers sharing `IN` group under one
"India" entry in the app, and it is listed first. Check them at `https://api.yourdomain.com/adminui`.

## 2. Signing key (one time)

```bash
keytool -genkeypair -v -keystore india-vpn-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias indiavpn
base64 -w0 india-vpn-release.jks > keystore.b64     # macOS: base64 -i india-vpn-release.jks -o keystore.b64
```

Keep the `.jks` file and its passwords somewhere safe. If you lose them you cannot update the app on Google Play.
Never commit them (`.gitignore` already blocks `*.jks`).

In GitHub: **Settings > Secrets and variables > Actions > New repository secret**

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | contents of `keystore.b64` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | `indiavpn` (the alias you used) |
| `RELEASE_KEY_PASSWORD` | key password |

Optional variable `VERSION_CODE_BASE` (Variables tab): versionCode = base + run number. Default base is 100.
Set it above the highest versionCode you already uploaded to Play.

## 3. Build

| You do | You get |
|---|---|
| Open a pull request | tests + `IndiaVPN-debug-apk` |
| Push to `main` | tests + debug APK + `IndiaVPN-release-apk-signed` + `IndiaVPN-release-aab-signed` |
| Actions tab > Run workflow | same as push (untick "build_release" for debug only) |
| `git tag v1.0.6 && git push origin v1.0.6` | same, version name = 1.0.6, plus a GitHub Release with APK, AAB and SHA256SUMS |

Download files from the run page under **Artifacts**. The job verifies that the APK and AAB are really signed with your key
(and not the debug key) and prints the certificate SHA-256 in the run summary. Upload the `.aab` to Play Console.
If the secrets are missing, push builds skip the signed step with a warning, and tag builds fail with a clear message.

## 4. Before publishing

- Replace the Yandex `app-ads.txt` line in `backend/api/public/app-ads.txt`.
- Update your Play listing, privacy policy URL (`/privacy.html`) and Data safety form for the new name.
- Test on a real phone: connect, switch server, disconnect, split tunneling.
