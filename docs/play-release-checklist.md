# Play Release Checklist

Last reviewed: 2026-07-03.

This checklist prepares the first Google Play release of Money Tracker Android.
It is based on the current repository state and must be rechecked for every
release candidate before Play Console submission.

## Current Release Assumptions

- App package: `dev.horex.moneytracker`.
- Current release version: `versionCode = 1`, `versionName = 0.1.0`.
- Android target: `targetSdk = 36`, `minSdk = 26`.
- Release signing is configured outside the repository; see
  `docs/release-builds.md`.
- The app is offline-first and does not declare `INTERNET`.
- The app stores financial records on the device only.
- The Room database is encrypted with SQLCipher and a device-bound Android
  Keystore key; the database key is not portable and must not be reused for
  export files.
- Support-facing user limitations and backup/import FAQ are tracked in
  `docs/support-and-known-limitations.md`.
- Android backup is disabled for app-private data with `android:allowBackup="false"`.
- The app does not use Telegram auth, Telegram sessions, server sync, ads,
  analytics, crash reporting, remote push, or payments in the current manifest
  and dependency graph.
- The only declared permission is `android.permission.POST_NOTIFICATIONS`.
- Background work uses WorkManager and does not require exact alarm
  permissions.

If any of these assumptions changes, update this checklist, the privacy policy,
and the Play Data safety answers before uploading the app bundle.

## Store Listing Draft

Use this as the first-pass Play Console copy. Before release, verify every
feature claim against the shipped build and remove any feature that is not
available in the release candidate.

### Main Listing

- App name: `Money Tracker`
- Short description: `Track accounts, budgets and goals offline on your device.`
- Category: `Finance`
- Ads declaration: `No ads`
- App access: `No sign-in required`
- Content rating notes: personal finance utility, no gambling, no user-generated
  public content, no commerce or payment processing in the app.
- Target audience: adults and general users who manage personal finances; do
  not target children.

### Full Description

```text
Money Tracker is an offline personal finance tracker for accounts, spending,
budgets, recurring reminders, savings goals and financial summaries.

Your records stay on your device. Money Tracker does not require an account,
does not sync to a server and does not use ads or analytics. Local financial
data is stored in an encrypted on-device database.

Use Money Tracker to:
- keep accounts and balances organized;
- record expenses, income, adjustments and transfers;
- monitor budgets and recurring activity;
- follow savings goals;
- review local summaries of your finances;
- keep optional local notification reminders under your control.

Money Tracker is designed for private, local finance tracking. If you delete the
app or clear its app data, the local records stored by the app are removed from
the device.
```

### Screenshot And Asset Checklist

- App icon matches the launcher icon in the release build.
- Feature graphic is prepared for the Main store listing.
- Phone screenshots show real in-app screens from the release candidate:
  accounts, transaction entry/history, budgets or summaries, settings/privacy,
  and notification controls if enabled.
- Screenshots do not show real personal financial records, real names, Telegram
  identifiers, phone numbers, email addresses, account numbers, or source
  database identifiers.
- Short captions, if used, match shipped behavior and avoid claims about server
  sync, bank connectivity, investment advice, credit scoring, or payment
  processing.
- Russian listing copy is prepared if the release targets Russian-speaking
  users; otherwise the default listing language remains the single source of
  truth.

## Privacy Policy Draft

Publish the final policy at an active HTTPS URL and link it from both Play
Console and the app. Legal review owns the final wording.

```text
Money Tracker stores personal finance records locally on your device. The app
does not require an account, does not send your records to a Money Tracker
server and does not use advertising, analytics or remote push services.

Data you enter may include account names, balances, transactions, categories,
budgets, recurring transaction settings, savings goals, display preferences and
local notification preferences. This data is processed on your device for app
functionality.

The local app database is encrypted on the device. App-private Android backup is
disabled. If you create or import a portable backup, you control where that file
is stored or shared outside the app.

Money Tracker may ask for notification permission on Android 13 and later so it
can show optional local reminders and finance alerts. Denying this permission
does not block the main finance tracking features.

Money Tracker does not import or store Telegram IDs, Telegram usernames,
Telegram first or last names, initData, bot/chat metadata, legacy identifiers or
source database IDs from the previous Mini App.

To delete local data, delete it inside the app where available, clear the app's
storage in Android settings or uninstall the app. Because Money Tracker does not
store account data on a server, there is no server-side account to delete.

If you contact support by email or another external channel, the contact details
and message contents are handled outside the app for support purposes.
```

## Data Safety Draft

Google Play defines collection as transmitting user data off the device. Under
the current offline implementation, user-entered finance data is processed
locally and is not collected or shared by the app.

Use this draft only while the current assumptions remain true.

| Play Console area | Draft answer |
| --- | --- |
| Does the app collect user data? | No. Local account, transaction, budget, goal, settings and notification preference data is processed on device and not transmitted off device by the app. |
| Does the app share user data? | No. The app does not transfer user data to third parties. User-initiated export or Android share flows must be reviewed separately before release. |
| Data types | Do not mark Financial info, Personal info, App activity, Device IDs, Files and docs, or Diagnostics as collected unless a future SDK or feature transmits them off device. |
| Purposes | App functionality only for local processing. No analytics, advertising, fraud prevention, personalization, account management, developer communications, or payments collection in the current app. |
| Security practices | State that data is not collected. Privacy policy should still explain local SQLCipher encryption and disabled app-private backup. |
| Data deletion | No server-side account data. Local data can be deleted by in-app delete flows where present, Android storage clear, or uninstall. If Play requires an account deletion answer, answer that accounts are not created in the app. |
| Privacy policy URL | Required before submission; must match this Data safety draft. |

Before submitting the form, inspect the final merged dependency graph and
release manifest. Adding analytics, crash reporting, cloud backup, server sync,
file upload, ads, sign-in, support SDKs, or telemetry changes the Data safety
answers.

## Permissions Explanation

Current manifest permission:

| Permission | Why it exists | User impact | Play release note |
| --- | --- | --- | --- |
| `POST_NOTIFICATIONS` | Optional local notifications for budget alerts, recurring reminders, savings goal milestones and weekly summaries. | On Android 13 and later, users can deny the runtime permission. Denial only prevents notification delivery; finance tracking remains usable. | Explain as local reminders only. It is not remote push, marketing, tracking, or server messaging. |

Current manifest exclusions:

- No `INTERNET`; the release should not claim sync, online accounts, remote
  backup, or cloud services.
- No contacts, location, camera, microphone, calendar, SMS, call log, photos,
  videos, files/media, nearby devices, health, biometric, accessibility, VPN, or
  exact alarm permission.
- No high-risk Play Permissions Declaration Form is expected from the current
  manifest, but the final Play Console review can only be confirmed after the
  release app bundle is uploaded.

Permission prompts must be tied to visible user value. If future slices add new
permissions, request them only when the related feature is used, explain why the
permission is needed, and keep the app usable when non-critical permission is
denied.

## Release Readiness Checklist

### Code And Build

- [ ] Release branch policy is followed: normal work merged through `develop`,
  release branch remains `main`.
- [ ] `versionCode` is greater than any previously uploaded Play artifact.
- [ ] `versionName` matches release notes and internal release name.
- [ ] Package name is still `dev.horex.moneytracker`.
- [ ] `targetSdk` satisfies the current Google Play target API requirement
  (`targetSdk = 36` currently exceeds the documented Android 15/API 35 minimum
  for new apps and updates; recheck the Play Console requirement before
  submission).
- [ ] Release signing values are provided through environment variables or
  `GRADLE_USER_HOME/money-tracker-signing.properties`.
- [ ] No keystore, signing password, generated bundle, mapping file, or filled
  signing properties file is tracked in git.
- [ ] Release artifact is built as an Android App Bundle for Play:

```powershell
.\gradlew.bat bundleRelease
```

### Local Validation

- [ ] Required Gradle validation passes:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleDebug
```

- [ ] If Gradle modules or dependencies changed, also run:

```powershell
.\gradlew.bat projects
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
```

- [ ] `git diff --check` passes before the release PR is opened.
- [ ] Release build is installed on a clean device or emulator through an
  internal test artifact.
- [ ] Fresh install, app restart, process death, Android storage clear, and
  uninstall behavior are checked.
- [ ] Notification permission is tested on Android 13+ for allow, deny, and
  denied-before-upgrade states.
- [ ] Offline behavior is checked with network disabled.
- [ ] Backup/import flows, when enabled, do not persist Telegram fields,
  `legacy_*` fields, source database IDs, `initData`, or bot/chat metadata.

### Play Console App Content

- [ ] Privacy policy URL is live, public, HTTPS, and matches the release build.
- [ ] Privacy policy link is reachable from inside the app before production
  rollout.
- [ ] Data safety form matches the final release manifest and SDK behavior.
- [ ] Ads declaration is `No` unless an ad SDK is added.
- [ ] App access says no sign-in is required, unless future auth changes this.
- [ ] Target audience and content declaration does not target children.
- [ ] Content rating questionnaire is completed for a personal finance utility.
- [ ] Permissions declaration is reviewed after uploading the AAB.
- [ ] Data deletion/account deletion answers explain that the app has no
  server-side account and local data is removed by app deletion/storage clear.
- [ ] Pricing and countries are selected intentionally.

### Store Listing

- [ ] App name, short description, full description, category, contact email,
  support URL, and privacy policy URL are filled.
- [ ] Support URL content matches `docs/support-and-known-limitations.md`.
- [ ] Feature graphic and screenshots meet Play asset requirements.
- [ ] Screenshots are from the release candidate and contain only sample data.
- [ ] Listing text does not claim online sync, bank integration, investment
  advice, credit scoring, payment processing, Telegram login, or server backup.
- [ ] Localized listing copy is reviewed for every locale included in Play
  Console.
- [ ] Release notes fit Play limits and describe shipped changes only.

### Release Track And Rollout

- [ ] Play App Signing is configured for the app before the first release.
- [ ] Internal testing release is created first and installed by testers.
- [ ] Pre-review and Play Console warnings are reviewed before production.
- [ ] Managed publishing decision is explicit before sending changes for review.
- [ ] Production rollout starts with a limited percentage when available for an
  update; first production release behavior is confirmed in Play Console.
- [ ] Android vitals, crashes, ANRs, user reviews, and Play policy messages are
  monitored after rollout.
- [ ] Rollback or hotfix owner is assigned before production rollout.

## Source Links

- Google Play Data safety form:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play User Data policy:
  https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play app review preparation:
  https://support.google.com/googleplay/android-developer/answer/9859455
- Google Play target API requirements:
  https://support.google.com/googleplay/android-developer/answer/11926878
- Google Play release preparation:
  https://support.google.com/googleplay/android-developer/answer/9859348
- Google Play preview assets:
  https://support.google.com/googleplay/android-developer/answer/9866151
- Android runtime permissions:
  https://developer.android.com/training/permissions/requesting
- Android notification runtime permission:
  https://developer.android.com/develop/ui/compose/notifications/notification-permission
