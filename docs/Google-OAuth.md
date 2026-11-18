# Google sign-in for Drive backup: checklist (S-03)

| | |
| --- | --- |
| Task | S-03 (TODO) |
| Status | Draft, 2026-11-18. Owner does the console steps; Claude only prepared this list. |
| Scope used | `https://www.googleapis.com/auth/drive.appdata` only (Design §1, B-1) |

## 1. Finding that changes the plan

Google's Drive scope table lists `drive.appdata` (also `drive.appfolder`) and `drive.file` as **non-sensitive**. Only `drive` and the `drive.readonly` / `drive.metadata` family are restricted.
Source: https://developers.google.com/workspace/drive/api/guides/api-specific-auth (read 2026-11-18).

Google's verification help says apps that request **sensitive or restricted** scopes must complete OAuth app verification; for apps that use only non-sensitive scopes verification is not mandatory, and a lighter **brand verification** exists if the owner wants the app name and logo on the consent screen.
Source: https://support.google.com/cloud/answer/13463073 (read 2026-11-18).

So for XNK Fit: **no sensitive-scope review and no security assessment are expected.** PRD §14 item 4 assumed a review; it is corrected there. Brand verification is optional and its timing is not published in the pages read (⚠ see §5).

Rule for the code (B-16): request `drive.appdata` and nothing else. Adding any other Drive scope later (for example `drive.file` for photo export) must be re-checked against this file first.

## 2. Owner checklist (Google Cloud Console)

1. [ ] Create a Google Cloud project for XNK Fit (use an account the owner keeps for the long term, not a personal throwaway).
2. [ ] Enable the **Google Drive API** in that project.
3. [ ] OAuth consent screen: user type **External**; app name, support email, developer contact email.
4. [ ] Add only the scope `drive.appdata`. Confirm the console shows it as non-sensitive.
5. [ ] Privacy policy URL and (if asked) home page URL: the policy text comes from B-20 / PV-4. It must say the backup goes only to the user's own Drive, that XNK Fit has no server, and how to delete it.
6. [ ] Create an **OAuth client of type Android** (package name + SHA-1). No client secret is used or stored.
7. [ ] Register **every** signing SHA-1: debug key, upload key, and the **Play App Signing** key (copy it from Play Console → App integrity once S-08 exists). Missing the Play signing key is the usual reason sign-in works in a local build and fails from the store.
8. [ ] Publishing status: while testing, add the 12 closed-test accounts (R-01) as test users. Before production, press **Publish app** so any user can sign in.
9. [ ] Optional: submit brand verification (name and logo on the consent screen).

## 3. What Claude must not do

- No client IDs, keys or SHA values in the repo notes beyond the package name (the repo will be public like XNK; rule PR-3, no secrets).
- No Cloud project, console change or submission on the owner's behalf.

## 4. Timing

| Step | Needs | Rough wait |
| --- | --- | --- |
| Steps 1–7 | S-08 only for the Play signing SHA-1 | Same day |
| Test users (step 8) | Closed-test list | Same day |
| Publish app (step 8) | Privacy policy URL live | Immediate for non-sensitive scopes (⚠ confirm) |
| Brand verification (step 9) | Domain and logo | ⚠ not published in the pages read |

Do steps 1–7 before B-16 starts so the backup code can be tried against a real client; do step 8 before R-01.

## 5. Still to verify (⚠)

- Exact behaviour of an app left in **Testing**: test user cap and whether refresh tokens expire after 7 days. The pages read did not state it. If tokens do expire, the closed test (14 days) would force re-sign-in; the backup screen must then show "masuk lagi", not fail silently.
- Whether **Publish app** with only a non-sensitive scope shows the "unverified app" screen to users.
- Brand verification review time.

Close these by reading Google's "OAuth 2.0 policies" and "Unverified apps" pages, then update §4 and this section.
