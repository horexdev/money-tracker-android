# Settings Preferences Foundation

`core:preferences` owns the device-local Preferences DataStore contract for app-level settings.

## Stored Values

- `active_profile_id`: the local Room `local_profiles.id` selected on this device.
- `theme`: app UI preference, one of `system`, `light`, or `dark`.
- `hide_amounts`: privacy display preference.
- `animate_numbers`: nullable UI animation preference; missing means no explicit choice.

The DataStore file must not contain transactions, accounts, categories, balances, exchange rates, Telegram fields, source database IDs, or any other financial records. Profile-owned settings such as language, display currencies, notification flags, and timestamps remain in Room `local_profiles` until the dedicated settings domain task defines the full reconciliation layer.

## Runtime Flow

`AppPreferencesRepository.preferences` exposes a `Flow<AppPreferences>` and maps unreadable preference files to defaults. The app container wires `DataStoreActiveProfileIdStore` into `LocalProfileRepository`, so first launch and process death restore read the same active profile pointer through DataStore.

This replaces the runtime SharedPreferences active-profile pointer from MT-B03 while keeping the old `AndroidActiveProfileIdStore` available for compatibility and focused database tests.
