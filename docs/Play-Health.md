# Play Console health declarations: checklist (S-04)

| | |
| --- | --- |
| Task | S-04 (TODO) |
| Status | Draft, 2026-11-18. Owner fills the forms in Play Console (needs S-08); Claude prepared the answers and the checklist. |
| Data used | Steps only, read-only: `android.permission.health.READ_STEPS` (PRD L-15, Design §1) |

Sources read 2026-11-18:
- https://developer.android.com/health-and-fitness/health-connect/publish
- https://support.google.com/googleplay/android-developer/answer/12991134 (Health Connect policy FAQ)
- https://support.google.com/googleplay/android-developer/answer/14738291 (how to fill the Health apps form; listed by search, not opened: ⚠ read before submitting)

## 1. What is required

| # | Item | Needed for XNK Fit? | Where |
| --- | --- | --- | --- |
| 1 | **Health apps declaration form** | **Yes, always.** Every app on any track (closed test included) must file it, even one with no health features. XNK Fit has fitness and nutrition features. | Play Console → Policy → App content → Health apps |
| 2 | **Health Connect questions** inside that form (one explanation per data type) | Yes, because of steps (L-15). The old separate Health Connect request form is retired; it is the same form now. | Same screen, second step |
| 3 | **Data safety** section | Yes. Health and fitness info (and Drive backup, photos) must be described consistently with the form. | App content → Data safety |
| 4 | **Privacy policy** on the store page | Yes. It must be the **same** text the user gets from the privacy link inside Health Connect. | Store listing + in-app link |
| 5 | Re-file on change | Adding or dropping a data type means filing again. A new app version may be re-reviewed. | Each release |

If the declaration is missing, users who try to link Health Connect get a "can't access" dialog after release, and changes to the app cannot go to review.

## 2. Draft answers (English, for the form)

**Health features to tick**
- Activity and fitness
- Nutrition and weight management

Do **not** tick any Medical feature. XNK Fit is a general fitness and meal-planning app. It does not diagnose, treat or monitor disease; the screening only gives a lighter plan and tells the user to see a doctor (PR-5, Rules).

**Health Connect data type explanation: Steps (read only)**

> XNK Fit shows the user's daily step count and weekly average on the Hari ini and Latihan screens so the user can see how active they are next to their workouts. Steps are read only, on the phone, and only after the user allows it. They are not stored on any server, not shared, not used for ads, and not turned into calories or points. If the user declines, or Health Connect is not installed, the steps card is hidden and the rest of the app works unchanged.

**Do not request:** any other Health Connect data type (weight, nutrition, exercise sessions, heart rate). Weight and food are entered by the user inside the app. If a later phase wants one, add it here first, with its own justification.

## 3. Checklist

Owner (Play Console, needs S-08):
1. [ ] Create the app and open Policy → App content.
2. [ ] Health apps: tick Activity and fitness, Nutrition and weight management; paste the Steps text above.
3. [ ] Data safety: health and fitness info collected on device; Drive backup is user-initiated to the user's own Drive; photos stay on the phone unless the user turns photo backup on (PV-3). Final wording comes from B-20.
4. [ ] Privacy policy URL on the store page (text from B-20 / PV-4).
5. [ ] Re-check the form text against the manifest before each release.

Claude (code tasks, later):
- B-09: manifest has `READ_STEPS` and nothing else from Health Connect; the screen explains why before the system dialog; the card hides itself when Health Connect is missing (L-15).
- B-09 (done): `PrivacyPolicyActivity` (intent `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`) and the `ViewPermissionUsageActivity` alias (`VIEW_PERMISSION_USAGE`, Android 14+) show the same `PrivacyScreen` as Pengaturan. ⚠ Not tried on a device.
- B-14: the same privacy policy opens from Settings and from Health Connect's privacy link (⚠ the exact manifest entry for this is in https://developer.android.com/health-and-fitness/health-connect/get-started; read it in B-09).
- B-20: wording of Data safety and policy matches this file.

## 4. Rules from the policy that XNK Fit must keep

- No selling or transferring health data; no ads or credit/insurance use; sharing only with explicit consent. (PR-3: no backend, so only the user's own Drive.)
- The app has a visible icon and screens (not headless).
- Not aimed only at children (O-5 already stops under 13).
- Ask for the minimum: steps only; justify each type in detail.
- Reasons the policy lists for rejection: weak justification, mismatch with approved uses, bulk permissions, thin detail on data use. The text in §2 answers each.

## 5. Timing and open points (⚠)

- Review time is **not stated** on the pages read. Plan it into R-01 and R-02: file the form when the first closed-test build is uploaded, not at release.
- Google's October 2025 policy preview said the Health and Medical functionality policy would add EU medical-device guidance. The pages read do not show it; ⚠ re-read the live policy before R-02 and check that "bukan nasihat medis" wording still meets it.
- Whether steps for "fitness and coaching" needs any extra in-app disclosure beyond the privacy policy: ⚠ confirm in answer 14738291.
