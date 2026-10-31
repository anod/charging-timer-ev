This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

The running charging screen shows the remaining duration alongside the estimated finish time
in the local time zone. Clock times use platform time preferences (the device's 12/24-hour
setting on Android and iOS, and the locale's short time format on desktop). The Android
charging notification uses the same clock formatter.

The running screen also shows elapsed time as a bar from session start to ETA,
with ticks predicting the battery percentage in one and two hours from now.
Predictions are capped at the session target; ticks strictly after ETA are hidden.
When neither tick remains, the bar shows "Target reached by" the ETA instead.
The bar and predictions update on the service ticker and after charging-speed changes.

The Running screen's decorative background has three gently drifting, low-alpha
theme-coloured waves at fixed fractions of the screen height. Their water lines
never follow charge percentage. Paths are cached per canvas size; animation state
is read only while drawing horizontal translations. Disabling system animations
leaves static waves through Compose's motion-duration scale, without a new setting.
The battery, prediction bar, stats and controls are unchanged.
The wave background fills the window, including behind the gesture/navigation bar;
system-bar insets apply only to Running's foreground content. Configuration retains
its original system-bar padding.

Starting a timer persists a session snapshot (capacity, start/target percentages,
optional preset identity and edited flag, efficiency and timestamped power segments) in SQLDelight
2.4.1 SQLite storage. Presets and ordered power options, sessions and their power
segments are structured tables; completed sessions retain estimated end percentage,
optional actual percentage and review status. DataStore keeps selected preset, start
percentage, unsaved named-preset edits and simple preferences. Later settings edits affect the next session, not
one already running. A transactional, idempotent migration imports legacy settings
as the Default preset and preserves every segment of an existing running snapshot
before publishing loaded status. Its database marker commits before legacy-key
cleanup, so an interrupted migration never imports twice.
Android's app-update receiver waits for that loaded status and restarts the charging
foreground service only when the migrated session is still running.

Config starts with the built-in Default preset, seeded from existing manual settings.
Default always appears first and cannot be renamed or deleted. Selecting a named preset
applies capacity, charging power, target and its power options, but never the session's
start percentage. Named-preset edits stay selected with a clear `*` marker.
Tap the selected edited chip to open its anchored Update preset / Reset menu.
Update preset commits edits; Reset reapplies saved values. Matching saved values clears
the marker. Default edits save directly. The permanent `+` pill opens Save as new preset
from any preset, creates and selects a named preset, and leaves the original unchanged.
No banner or conditional button shifts the settings when selecting or editing presets.
Names accept emoji and
must not be blank; advanced name rules and preset management are separate features.
Selecting another preset discards the previous working edits.

The shared Add Custom Power dialog offers AC quick-picks (2.3, 3.6, 7, 11 and
22 kW) and DC quick-picks (50, 100 and 150 kW). A pick only replaces the editable
field; manual entry and Cancel remain available, and only Add confirms a speed.
Suggestions do not populate saved, restored or new-install power lists. Config's
existing add path sorts/deduplicates the options and selects the explicitly added
speed. The dialog body scrolls when space is limited, including with the keyboard open.

Android uses AndroidSqliteDriver, iOS NativeSqliteDriver, and desktop JdbcSqliteDriver
with `charging.db` in the platform app-data directory. SQLDelight migration verification
is enabled against a committed v1 schema database; JVM repository tests use an
in-memory SQLite database. Invalid stored
settings use defaults with a warning; malformed active snapshots are logged and
cleared before loaded state. SQLDelight works with the AGP 9.5 multiplatform Android
library plugin and Kotlin 2.4.21; no Room fallback is needed.

`ChargingCalculator.percentAt(snapshot, now)`, `minutesToReach(snapshot, now, percent)`
and `predict(snapshot, now, plusMinutes)` are pure, millisecond-based calculations.
Progress and predictions stop at the snapshot target; time-to-reach accepts any
percentage from 0 to 100 and assumes continued charging at the last segment's power.
Efficiency is an energy multiplier (1.0 preserves the existing estimates). Remaining
minutes are rounded up only for display, while ETA retains millisecond precision.
The injected clock drives updates at the next elapsed-minute, displayed remaining-minute,
or half-percent boundary (covering rounded screen and truncated notification percentages),
at most 60 seconds apart. Invalid persisted settings use validated defaults with a warning;
an invalid snapshot is logged and cleared before loaded status is published.

Navigation uses multiplatform Navigation 3 with a ViewModel-owned back stack and
entry-scoped Koin screen ViewModels. Android Back and desktop Escape pop the current
screen without stopping an active timer; Start Timer returns to that timer.
Restored active sessions open Running. Config and Running remain state/callback
composables so their previews do not require Koin.
Desktop Escape is handled at the Window, independent of focused controls or cleared
focus. Android focus/traversal is not modified. Its regression test injects Compose key input
directly (without sending OS-level keys): `.\gradlew.bat :shared:jvmTest --tests '*DesktopNavigationTest*'`.

EndSummary, History, Settings and PresetEditor destinations are declared for the
subsequent features, but are not exposed until their screens exist. Stop still returns
to Config; issue #23 will change the stopped destination in `NavigationViewModel` to
EndSummary. No placeholder screens or new navigation chrome are shown.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/androidApp](./androidApp/src/main) contains the Android application: the entry point (`MainActivity`, `MainApplication`),
  the charging foreground service, the manifest and Android resources.

* [/desktopApp](./desktopApp/src/main/kotlin) contains the Desktop (JVM) application entry point and packaging configuration.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/lib](./lib) is a git submodule with shared multiplatform helper libraries (`viewmodel`, `permissions`).
  Clone with `--recurse-submodules` or run `git submodule update --init` before building.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :androidApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :androidApp:assembleDebug
  ```

The Android charging notification shows current-to-target progress (for example, `45% → 80%`).
When no time remains, it shows `Target reached · 80%` for the configured target.
Charging speed, remaining duration while charging, and the ETA clock time are also displayed.
On Android 16 (API 36) and newer, it requests a Live Update with a current-battery-percent
status-bar chip, a battery tracker, and progress segments/points at charging speed changes.
Changes within the same whole battery percent share a point/segment boundary to avoid
zero-length segments. Live Update content is public on the lock screen.
Android 13–15 retain the expanded text notification and standard progress bar.
If Live Updates are disabled, starting a session offers a link to the app's system
Live Update settings alongside the notification-permission flow; the ordinary notification
continues to work without promotion. Dismissing a notification suppresses further posts
for that session, including after reopening the app. It does not stop the saved timer;
use the notification's Stop action or stop it in the app. Background foreground-service
restoration is retried when the app becomes visible if Android denies a background start.

At the target ETA, a separate `charging_complete` high-importance notification repeats
notification sound and vibration until handled. It respects silent/vibrate mode, channel
settings and Do Not Disturb; it does not bypass DND. Both **Stop charging** and **Dismiss**
cancel the alert's sound/vibration; Dismiss leaves the saved timer running, while Stop
ends only the app's timer (the app cannot control the vehicle). The same choices appear
in an Android in-app dialog when the activity is resumed. All alert text uses Android
string resources, like the existing Live Update settings dialog.

The shared `ChargingCompletionPlanner` plans from the snapshot-derived ETA. Android
uses a wakeup `setExactAndAllowWhileIdle` alarm when exact-alarm access is available,
otherwise `setAndAllowWhileIdle` (which may be delayed by minutes). There is no
`USE_EXACT_ALARM` permission. Every session ETA change, including a speed change,
replaces the alarm; stopping cancels it. Dismissing the Live Update does not cancel
completion scheduling. Device-local completion delivery/acknowledgment preferences
are excluded by the cloud backup and device-transfer allow-lists, and receivers validate
the loaded running session and ETA before alerting. Restored interrupted history cannot
produce a stale alert. App updates and exact-alarm grants re-evaluate scheduling.
Boot rescheduling is not implemented here: #32 can call
`ChargingCompletionController.sync(force = true)` after session loading.

### Android releases

See [Internal testing releases](docs/releasing.md) for signing setup, required
GitHub Secrets, the manual Play upload workflow, and the Play Console checklist.
The [privacy policy](docs/privacy.md) is published from `docs` using GitHub Pages.

### Backup & restore

Android Auto Backup and Google's device-to-device migration can include presets,
power options, charging history, unsaved preset edits and simple preferences, subject to the user's
backup settings and transport availability. Both modes explicitly allow only
`databases/charging.db`, its rollback journal (`charging.db-journal`), defensive
WAL recovery file (`charging.db-wal`) and
`files/.charging_settings.preferences_pb` (including the leading dot). Other files,
notification-dismissal preferences, installation/restore markers, caches and
no-backup files are excluded. Cloud backup has a 25 MB per-app quota; Android's
device-transfer limit is 2 GB. This is not an app-managed export or a guarantee
that Google will retain or restore a particular backup.

The Android database requests **DELETE**, not WAL, journaling. Committed rows live in
the main database; including the rollback journal also permits recovery if the
system stops the writer during an uncommitted transaction. Android stops the app
while performing Auto Backup; no custom checkpoint or live-file copying is needed.
If the framework refuses DELETE, a warning is logged instead of blocking startup;
the allow-list also includes WAL to retain committed rows in that fallback.
SQLite rebuilds the disposable shared-memory (`-shm`) file.
Older schemas migrate on open, including the committed v1 backup baseline.
Schema v3 adds a session `preset_edited` flag (false for existing sessions) and renames
the built-in preset to Default while keeping its internal `custom` id unchanged.
Already-recorded session names are not renamed. Snapshot
identity, edited flag and values remain independent of later preset edits or deletion.
Working edits use separate keys in the already allow-listed DataStore file, not the
saved SQL preset. They survive process death and restore with their selected preset.
Missing, mismatched or malformed draft state logs a warning and selects Default.

Restored running timers **never resume automatically**. A no-backup installation
marker detects a database arriving on another installation; the backup agent also
records restore completion, including preferences-only restores or restores over
an existing installation. Before loaded status, the timer becomes an interrupted
history entry with its original snapshot, no fabricated end percentage, and a
restore-detection timestamp. Start a new timer explicitly to continue charging.
Missing or stale selected-preset preferences fall back to Default and valid defaults.
Unreadable/corrupt preferences are logged and replaced with defaults without
discarding a valid restored database.
Normal updates retain the local marker and continue the local running session.
An unmarked database from an older development build is conservatively treated
as restored once; current released legacy DataStore-only timers still migrate.

JVM tests cover schema migration, committed-file and hot-journal recovery,
inconsistent database/preferences and stopped restore state. Pixel restore
simulation uses app-local file swaps, not backup-transport switching or uninstall.
Actual Google cloud/D2D transport execution and iOS backup behavior require
separate validation; the developer cannot access the user's Google backup.
JVM and iOS currently use `LocalStorageRestoreState`, which never interrupts;
iCloud backup/transfer behavior is unvalidated.

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :desktopApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :desktopApp:run
  ```

To run it with [Compose Hot Reload](https://github.com/JetBrains/compose-hot-reload), use the `:desktopApp:hotRun` task instead.

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

To stamp the build-time version into [Config.xcconfig](./iosApp/Configuration/Config.xcconfig), run `./gradlew updateIosVersion`.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
and [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform).

If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
