This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

The running charging screen shows the remaining duration alongside the estimated finish time
in the local time zone. Clock times use platform time preferences (the device's 12/24-hour
setting on Android and iOS, and the locale's short time format on desktop). The Android
charging notification uses the same clock formatter.

Starting a timer persists a session snapshot (capacity, start/target percentages,
optional preset identity, efficiency and timestamped power segments) in Preferences
DataStore. Later settings edits affect the next session, not one already running.
Updates migrate an existing running timer to a single-segment snapshot at its saved
power and start time before publishing loaded status.

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

### Android releases

See [Internal testing releases](docs/releasing.md) for signing setup, required
GitHub Secrets, the manual Play upload workflow, and the Play Console checklist.
The [privacy policy](docs/privacy.md) is published from `docs` using GitHub Pages.

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
