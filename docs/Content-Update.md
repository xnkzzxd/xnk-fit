# Content update host (S-06)

| | |
| --- | --- |
| Task | S-06 (TODO), Design §16 item 2 |
| Status | Draft, 2026-11-18. Decision proposed; owner confirms. |
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
- Owner confirms the repo name and whether a second host is wanted from day one.
- `SHA256withECDSA` on API 26 (B-04).
