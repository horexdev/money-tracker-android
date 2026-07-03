# Settings Preferences Domain

`core:preferences` owns the local settings contract for Android features.
The active profile pointer and legacy device-level UI mirror live in Preferences DataStore.
Profile-owned settings live in Room `local_profiles` and are exposed through `RoomSettingsRepository`.

## Stored Values

- DataStore:
  - `active_profile_id`: the local Room `local_profiles.id` selected on this device.
  - `theme`, `hide_amounts`, `animate_numbers`: mirrored UI values for compatibility with existing app-level observers.
- Room `local_profiles`:
  - `language_code`: one of the 17 supported app language codes.
  - `display_currencies_csv`: up to 3 ISO currency codes.
  - notification flags: budget alerts, recurring reminders, weekly summary, goal milestones.
  - UI preferences: stats chart style, nullable animate numbers, theme, hide amounts.
  - profile timestamps.

Settings must not contain transactions, accounts, categories, balances, exchange rates,
Telegram fields, source database IDs, `legacy_*` fields, `initData`, bot/chat metadata,
or any other financial records.

## Runtime Flow

`AppPreferencesRepository.preferences` exposes a `Flow<AppPreferences>` and maps unreadable
preference files to defaults. The app container wires `DataStoreActiveProfileIdStore` into
`LocalProfileRepository`, so first launch and process death restore read the same active
profile pointer through DataStore.

`RoomSettingsRepository.getSettings()` ensures an active profile exists, derives the base
currency from the default account, and returns the current language, display currency,
notification, chart style, animation, theme, and privacy settings. `updateSettings()`
updates only the requested settings and mirrors UI preferences back to DataStore.
The manual online-rate action uses the profile base currency and asks `core:currency` for
active profile currencies before calling the network update service, so unused catalog
currencies are not refreshed.

Android does not carry over server/admin or Telegram-specific source fields in this model.
