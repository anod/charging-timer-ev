# Localization

The app is currently English-only. Shared Compose UI text lives in
`shared/src/commonMain/composeResources/values/strings.xml`; Android notification and
Live Update text lives in `androidApp/src/main/res/values/strings.xml`. Notification
formatting is assembled in Android using resource-backed formatters, while the shared
notification logic only decides which formatted parts are needed.

Shared UI number formatting uses the active platform locale, and clock estimates use the
platform's locale and time preferences. Power is consistently shown with one decimal
place. Add translations as resource qualifiers when they are ready; do not add a per-app
Android `localeConfig` until a second language is shipped.

The built-in preset keeps its existing stored name (`Default`) and identity (`custom`).
The UI resolves that identity to the localized `default_preset` resource only for display.
The reserved-name validation continues to reserve the stored English name, so neither
translation nor UI display changes preset data, history snapshots, or backups.

CI runs `scripts/check_hardcoded_ui_strings.py` to catch common hardcoded text patterns in
shared Compose UI, shared notification formatting, and Android app code. Developer
messages, logs, preference keys, and Compose previews remain outside the check.
