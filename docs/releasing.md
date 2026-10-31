# Android internal testing releases

The manual **Release Android to internal testing** Actions workflow builds a
minified, resource-shrunk AAB and publishes it to Google Play's `internal` track.
The application ID is `info.anodsplace.evtimer`. Version code and version name
remain timestamp-based; do not reuse an old AAB or start two builds in the same
minute. Release builds disable the Gradle configuration cache so the timestamp
is regenerated and signing credentials are not persisted in that cache.

## One-time setup

1. Create the app in Play Console with the application ID above. Enroll in Play
   App Signing. Generate and securely back up a separate upload keystore outside
   this repository; never commit the keystore or its passwords.
2. Enable GitHub Pages in repository **Settings > Pages > Deploy from a branch**,
   select `main` and `/docs`, and save. After deployment, verify that
   <https://anod.github.io/charging-timer-ev/privacy.html> is publicly accessible.
   Enter that URL in Play Console's privacy policy field.
3. Complete the store listing and App content sections: Data safety (the app
   collects/shares no user data with the developer; local preferences, presets and
   charging history may be included in the user's Android/Google backup or device
   transfer, managed by the OS and inaccessible to the developer),
   ads declaration (no ads), app access (no login), content rating questionnaire,
   and target audience. Answer based on the actual build being submitted.
4. Declare the `specialUse` foreground service in Play Console. Explain the
   user-started EV charging estimate timer, ongoing progress notification, and
   stop action; it does not connect to or control a vehicle. Supply the required
   short demonstration video showing starting a timer, backgrounding the app,
   its notification, and stopping it. Approval is Google's decision.
5. Build a signed release AAB using the local setup below. Upload the **first
   AAB manually** in Play Console's internal testing track (the publishing API
   cannot create the app). Complete required Console setup and publish this
   first release before using the workflow.
6. Enable the Google Play Android Developer API in the service account's Google
   Cloud project. Create a dedicated service account and invite its email in
   Play Console **Users and permissions**, scoped to this app, with app viewing
   and **Release apps to testing tracks** permissions. Create a JSON key and
   store it only as the `PLAY_SERVICE_ACCOUNT_JSON` GitHub Actions secret.
7. Create the repository Actions secrets below under **Settings > Secrets and
   variables > Actions**. Use the same upload key as the first manual release.
8. Configure an internal tester email list or Google Group in Play Console
   (up to 100 testers), enable it for the track, and share the Console-generated
   opt-in link. Testers must opt in with an eligible Google account.

## Required GitHub Actions secrets

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded upload keystore file (not the Play app-signing key). |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_ALIAS` | Upload key alias in the keystore. |
| `ANDROID_KEY_PASSWORD` | Upload key password. |
| `PLAY_SERVICE_ACCOUNT_JSON` | Full JSON key for the Play service account. |

Encode a keystore locally with `base64 -w 0 upload.jks` on Linux, or
`[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\secure\upload.jks'))`
in PowerShell. Transfer the result directly to the secret; do not save it in
the repository, logs, or an issue. Keep service-account JSON out of the repo too.

## Local builds

With no signing values, `./gradlew :androidApp:bundleRelease` builds an unsigned,
minified AAB for verification. Debug builds need no release secrets.
On Windows, use `.\gradlew.bat` instead of `./gradlew`.

For signed builds set all four environment variables in the build process:

| Environment variable | Alternative Gradle property |
| --- | --- |
| `ANDROID_KEYSTORE_PATH` | `releaseStoreFile` |
| `ANDROID_KEYSTORE_PASSWORD` | `releaseStorePassword` |
| `ANDROID_KEY_ALIAS` | `releaseKeyAlias` |
| `ANDROID_KEY_PASSWORD` | `releaseKeyPassword` |

Use an absolute keystore path. Properties take precedence over environment
variables and may be supplied in your user-level Gradle properties file, never
the committed `gradle.properties`. Partial signing configuration fails explicitly.
Run `./gradlew :androidApp:bundleRelease --no-configuration-cache`.
The AAB is at `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
The optimized Android default rules and library consumer rules are used for R8;
no blanket Koin, Compose, or DataStore keep rules are added.

## Publishing subsequent builds

After merging the workflow into `main`, open **Actions > Release Android to
internal testing > Run workflow** and select `main` (or a reviewed release ref).
The workflow requires all five secrets, builds the signed AAB, retains it and
the R8 mapping as a 30-day artifact, and uploads a completed internal release.
Archive the mapping securely for longer-term crash decoding.
No release runs automatically on a push or pull request. Never dispatch an
untrusted ref with release secrets.

If Play reports that the app is still a draft, finish the first manual release
and outstanding Console declarations before retrying. Failed uploads retain
the build artifact for diagnosis; do not upload an already-used version code.

Before calling a release ready, install the minified build from the Play opt-in
link on a device: open the app, change settings, restart and verify persistence,
start a timer, background the app, inspect progress and the stop action, then
stop it. Also check notification permission denial. This device smoke test and
Console declarations cannot be verified by a local unsigned bundle build.

Internal testing is not production eligibility. New personal developer accounts
may need a closed test with at least 12 testers for 14 continuous days before
applying for production access; follow the requirements shown in Play Console.
