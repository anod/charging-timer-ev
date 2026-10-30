This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

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
