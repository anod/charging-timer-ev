# Localization

The app is currently English-only. Shared Compose UI text lives in
`shared/src/commonMain/composeResources/values/strings.xml`; Android notification and
Live Update text lives in `androidApp/src/main/res/values/strings.xml`. Browser
controls, errors and foreground notification content live in
`webApp/src/commonMain/composeResources/values/strings.xml`. Notification
formatting is assembled in Android using resource-backed formatters, while the shared
notification logic only decides which formatted parts are needed.

Shared UI number formatting uses the active platform locale, and clock estimates use the
platform's locale and time preferences. In-app power labels use one minimum and two
maximum fractional digits to preserve custom-power precision; notification power remains
at one decimal place. Add translations as resource qualifiers when they are ready; do not
add a per-app Android `localeConfig` until a second language is shipped.

Running-screen prediction labels use localized minute/hour durations and percentages.
The existing start-to-finish time bar selects the finest nice interval whose measured
labels fit in at most two non-overlapping rows, including the exact target ETA.
Available width and font size determine density; predictions are not limited to +1h/+2h.
With less than one minute remaining, the existing target finish-time text is shown instead.

The built-in preset keeps its existing stored name (`Default`) and identity (`custom`).
The UI resolves that identity to the localized `default_preset` resource only for display.
The reserved-name validation continues to reserve the stored English name, so neither
translation nor UI display changes preset data, history snapshots, or backups.

CI runs `scripts/check_hardcoded_ui_strings.py` to catch common hardcoded text patterns in
shared Compose UI (including `platformMain`), browser UI, shared notification
formatting, and Android app code. Developer
messages, logs, preference keys, and Compose previews remain outside the check.
