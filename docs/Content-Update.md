# Content update host (S-06)

| | |
| --- | --- |
| Task | S-06 (TODO), Design §16 item 2 |
| Status | 2026-11-18. Owner left the choice to Claude, so this is the decision. Repo name `xnk-fit-content`; no second host on day one. |
| Rules it must keep | PR-3 (no backend of ours), PV-2 (static files only, no user data sent), Content.md (SHA-256 per file, one transaction swap) |

## 1. Decision

**Host the content files on GitHub Pages of the project repo** (a small public repo, for example `xnk-fit-content`, built by a GitHub Actions workflow). The app knows the base URL as one constant and a second, optional mirror URL.

Reasons:
- Free, HTTPS, no server to run (PR-3), and the owner already publishes XNK sites this way.
- Content is small (food table, exercise library, badges, text): a few MB, far below the 1 GB site limit.
- The app downloads only the manifest (about 1 KB) on a check, and a file only when its SHA-256 changed, so bandwidth stays far below the 100 GB/month soft limit for a long time.

Limits quoted from https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits (read 2026-11-18): published site no larger than 1 GB; soft bandwidth limit 100 GB per month; soft limit 10 builds per hour (not for custom Actions workflows). If a site exceeds its quotas GitHub may not be able to serve it or may contact the owner.

**Known risk, accepted for v1:** the same page says Pages is not allowed as free hosting to run an online business or to provide commercial SaaS. XNK Fit is free, has no ads and sells nothing, so static content for a free app fits. If the app ever gets payments or ads, move the host first (§4).

## 2. Options considered

| Option | For | Against | Verdict |
| --- | --- | --- | --- |
| **GitHub Pages (project repo)** | Free, familiar, versioned in git, review before publish | 100 GB/month soft, commercial-use clause | **Chosen** |
| Cloudflare Pages | 20,000 files, 25 MiB per file, 500 builds a month on Free (https://developers.cloudflare.com/pages/platform/limits/, read 2026-11-18) | The pages read say nothing about bandwidth: ⚠ unknown; one more account to keep | Fallback / mirror |
| Own domain `xnkfit.com` | Brand, host can change later without an app update | Not registered (S-01), costs a yearly fee, still needs a host behind it | Later: point it at the host so the URL in the app never has to change |
| Google Drive / Firebase | — | Needs accounts or an SDK, breaks "no backend", not a plain static `GET` | Rejected |
| Bundle only, no updates | Zero network | A wrong number needs a store release | Rejected (PV-2 is a requirement) |

## 3. How the update works (for B-04)

1. On app open, at most once per 24 hours, and only if the last attempt was more than 6 hours ago: `GET <base>/manifest.json`. 8-second timeout; any failure is silent and the bundled or last good content stays.
2. The manifest lists `contentVersion`, `minAppVersion`, `schema`, and per file: path, SHA-256, size.
3. Skip if `contentVersion` is not newer, or the app is older than `minAppVersion` (then show nothing; the next app update brings it).
4. Download only the changed files to a temp folder, check every SHA-256 and size, then swap in **one** transaction. A failed check discards everything.
5. No request body, no cookies, no user id, no custom header. Only the URL is sent (PV-2).

**Signed manifest (proposed addition).** A SHA-256 taken from the same host protects against a bad download, not against a hijacked repo or Pages account. The content steers meal and training numbers (PR-5), so sign the manifest:
- The owner keeps an ECDSA P-256 private key offline; the **public key is in the app**. `manifest.sig` is published next to the manifest. The app refuses an update whose signature fails.
- Use ECDSA, not Ed25519: Android's built-in Ed25519 arrives only with API 33, and the minimum is API 26 (Design §1). ⚠ confirm `SHA256withECDSA` verification on an API 26 emulator in B-04.
- The private key never goes in a repo, a Script/Actions secret or this chat. If it is lost, the app can still run on its last content; a new key needs an app update.

## 4. Moving the host later

- The base URL is a constant plus a mirror list; moving the host means shipping the new URL in an app update, and keeping the old host up until most users have updated.
- Cheaper: register `xnkfit.com` (owner decision, S-01), point it at the host, and put only that domain in the app.

## 5. Privacy and store text

- The check shows the user's IP address to the host, like any web request. State it in the privacy policy and in Data safety as "no user data sent" plus the sentence about the update check (wording in B-20).
- No analytics, no counting of downloads by user.

## 6. Open points (⚠)

- Cloudflare Pages bandwidth terms are not in the pages read.
- `SHA256withECDSA` on API 26: code uses only `KeyFactory("EC")`, `X509EncodedKeySpec`, `Signature("SHA256withECDSA")` and `java.util.Base64`, all in API 26; checked on the JVM only. Needs an emulator or the reference phone (B-04 left it open).

## 7. Owner steps: key and content repo (B-04)

Not done by Claude (Decisions.md rule 3). The app ships with a **placeholder** public key (`ContentConfig.PUBLIC_KEY_BASE64`) whose private half does not exist; `PUBLIC_KEY_IS_PLACEHOLDER = true` keeps the update check off.

1. Make the private key on your own computer, offline: `openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt > xnk-content-key.pem`. Keep it out of every repo, Actions secret and chat (§3).
2. Print the public key for the app: `XNK_CONTENT_SIGNING_KEY="$(cat xnk-content-key.pem)" node tools/content-manifest.mjs pubkey`. Paste the line into `ContentConfig.PUBLIC_KEY_BASE64`, set `PUBLIC_KEY_IS_PLACEHOLDER = false`, and ship an app version.
3. Make the repo `xnk-fit-content` with GitHub Pages and check `ContentConfig.BASE_URL` (it assumes `https://xnkzzxd.github.io/xnk-fit-content/`).
4. To publish: raise `contentVersion` in `content/content-version.json`, then on your computer `XNK_CONTENT_SIGNING_KEY="$(cat xnk-content-key.pem)" node tools/content-manifest.mjs build --src content --out dist --sign`, and push the files of `dist/` (8 content files, `manifest.json`, `manifest.sig`) to the content repo. The signing happens on your computer, not in Actions.

## 8. What B-04 built (details that differ from or add to §3)

- **Files:** `exercises`, `foods-tkpi`, `foods-estimates`, `foods-regional`, `foods-packaged`, `badges`, `meal-packages`, `household` (`*.json`); measures from `household.json` are joined into `food.household` after every foods or household change (`ref` page dropped). The manifest may list only these names; any other name or a path rejects the whole update. `figures/` is not part of the loader (UI tasks).
- **Manifest:** `{schema: 1, contentVersion, minAppVersion, files: [{path, sha256, size}]}`. `minAppVersion` is compared with the app `versionCode`. `contentVersion` is an integer that must grow. The bundled release marker is `content/content-version.json` (same fields, no files).
- **Signature:** `manifest.sig` is base64 of a DER ECDSA signature (`SHA256withECDSA`) over the exact bytes of `manifest.json`. The app checks it before parsing anything.
- **Order of checks:** throttle, fetch both files, signature, shape, `schema`, `minAppVersion`, `contentVersion`, then per changed file: size, SHA-256, full parse. Then one Room transaction. Any failure keeps the old data; nothing is half-applied.
- **Throttle:** state in table `content_meta`. A check completed (genuine manifest, whatever the result) blocks the next for 24 h; any attempt blocks the next for 6 h, so a network failure or rejected update is retried after 6 h. A clock set backwards counts as due.
- **Request:** `GET <base>manifest.json` and `GET <base>manifest.sig`, then one `GET` per changed file. `HttpURLConnection`, https only, no redirect, no body, no cookies, no header added by us (the platform adds Host and User-Agent), 8 s connect, read and total deadline per request, size capped by the manifest.
- **Changed files only:** each installed file's SHA-256 is kept in `content_meta.filesJson`; only files with a different hash are downloaded. The rows of a file are replaced as a set (foods by id prefix: `TKPI_`, `W_`, `REG-`, `PK_`; user foods are never touched).
- **Bundled content:** `./gradlew` copies `content/*.json` byte for byte into `assets/content/` (task `copyContentAssets`, run by `preBuild`). Not minified: the APK deflates the JSON, total APK 1.5 MB against the 20 MB budget, so no size work is needed. A newer APK replaces older content; an older APK never replaces newer content.
- **Off until the owner key is in:** see §7.
