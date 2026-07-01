# Localization

## Supported Locale Set

Android uses the same migration locale set as the source Mini App i18n config:

`en`, `ru`, `uk`, `be`, `kk`, `uz`, `es`, `de`, `it`, `fr`, `pt`, `nl`, `ar`, `tr`, `ko`, `ms`, `id`.

`app/src/main/res/xml/locales_config.xml` is the source of truth for the supported app locale list. English lives in the default `values/` resources. Russian lives in `values-ru/`. The full 17-language translation pass is tracked separately by MT-G01; until then untranslated locales fall back to English.

## Rules

- User-facing UI text must be stored in Android resources, not hardcoded in Compose or Kotlin defaults.
- Feature modules own their feature-specific strings under their own `src/main/res` tree.
- App-shell navigation labels, route placeholder titles, and app-wide strings live in `app/src/main/res`.
- When adding a new default string in `values/strings.xml`, add the matching Russian key in `values-ru/strings.xml` in the same change.
