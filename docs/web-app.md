# Installable web app

Open <https://anod.github.io/charging-timer-ev/> over HTTPS after the owner
completes the deployment setup below. Installation is optional: the same timer,
presets, history and manual backup work in a supported browser tab. The app
estimates charging; it does not connect to or control your vehicle.

## Installation

Use a normal (not private/incognito) browser window and allow the first load
to finish. Wait for **Ready for offline use.** before relying on offline access.

- **Desktop Chrome or Edge:** open the URL, click the install icon in the address
  bar (or choose the browser menu's Install app option), and confirm. Launch
  EV Charging Timer from your applications/start menu.
- **Android Chrome:** open the URL, use the browser menu's **Add to Home screen**
  or **Install app**, and confirm. Launch the icon from your home screen.
- **iOS Safari:** open the URL directly in Safari (not an in-app browser), tap
  **Share > Add to Home Screen**, and confirm **Add**. Launch from the home screen.
  Safari may not show an automatic installation prompt.

Labels vary by browser version. If installation is unavailable, use a browser
tab. Installation does not enable background charging alarms.

## Initial browser support

Use current browser releases with WebAssembly GC, IndexedDB and service workers
enabled. Older engines without Wasm GC cannot run this Compose/Wasm app.

| Browser/platform | Initial status | Installation |
| --- | --- | --- |
| Desktop Chromium / Chrome / Edge | Production app, project-path manifest, service worker, offline profile reopen and storage checked in CI Chromium; branded Chrome/Edge not separately device-tested | Browser install UI where supported |
| Android Chrome | Expected on current Wasm GC-capable Chrome; CI phone viewport is not physical-device verification | Install app / Add to Home screen |
| iOS Safari | Expected on current Wasm GC-capable Safari (iOS 18.2+); **not verified on a physical device** | Manual Safari Add to Home Screen |
| Firefox desktop | Current Wasm GC-capable versions may run in a tab; not part of initial automated verification | No desktop install flow promised |
| Other/in-app browsers | Not verified; use a supported full browser | Not promised |

This matrix describes the initial verification scope, not a guarantee for every
browser/OS combination. Permissions, storage policies and installation support
are browser-controlled.

## Limitations and keeping data safe

Notifications are **foreground-only** and require the permission button in
**Settings > Browser notifications and offline use**. The shared **Notify at 80%**
preference controls the milestone independently of browser permission. No
background alarms, push notifications or alerts are promised when
the tab/app is hidden, suspended or closed. Stored epoch timestamps let estimates
catch up when reopened; reopening is not a background alarm.

Offline use requires one complete successful online load and service-worker
setup. If setup fails, reconnect and reopen; do not clear site data to repair it.
The privacy policy remains network-only and is not available from the offline
shell. A downloaded app update applies only after **all** timer tabs and installed
app windows close; reopen afterward. Reloading one tab does not force an update.

Charging data is in the browser's IndexedDB storage for this site, not the native
app's database. Browser storage eviction, clearing site data, or private browsing
can lose it. Installation is not a backup and does not migrate native data.
Export regular backups using **Settings > Charging data > Export backup** and keep the JSON in a trusted
location outside browser storage; imports ask before replacing data. Backup
files are unencrypted and can contain presets, history and preferences.
See [backup format and browser storage](backup-format.md#browser-storage-stack-layer-2).
Browser and native apps use the same Compose Settings, presets, history, charge
summary, car-reading editor, charging-power management and open-source licenses
screens. Settings also links to the same source and privacy information. Browser
Back/Forward follows the Settings hierarchy, restores history summaries by their
saved IDs, and cannot resurrect an ended timer. A timer starting or finishing in
another tab reconciles the current navigation with durable storage.

Preset rename/delete/reorder, recorded car percentages, consent to learned
estimates and per-preset/per-speed learning reset are saved in IndexedDB and
included in portable native-compatible backups. Editing settings does not change
the snapshot of an already running session. Adding a speed selects it; removing
the selected speed selects the first remaining option. The last speed cannot be removed.

**Share** on a summary uses the browser's system share sheet where supported,
otherwise copies the shared, localized summary text to the clipboard. Sharing and
clipboard access depend on HTTPS, browser capabilities and user permissions;
failures are reported rather than silently treated as success. Cancelling a
share sheet is not an error. File imports use the browser picker and exports use
browser downloads; the app can confirm requesting a download, not that the user
saved it.

The browser intentionally does not implement Android foreground services,
background alarms, OS-level Google Auto Backup/device transfer or native document
providers. Use manual backup files to transfer data between native and web.
Browser permission/offline status and backup controls live in Settings rather
than a separate navigation strip, leaving the shared timer screens unobstructed.

## Compose Web accessibility

The shared Compose Multiplatform version is `1.13.0-beta02`, selected to include
the upstream [CMP-9368 dialog accessibility fix](https://github.com/JetBrains/compose-multiplatform-core/commit/d97ec456148a188550f4a45243e3544a6ad47321).
Version `1.12.2` replaced the main semantics owner when a dialog opened and did not
restore it when the dialog closed. The screen painted correctly, but screen
readers retained a phantom dialog or lost the accessibility tree. The fixed
version tracks all active semantics owners.

Browser regressions require automatic restoration after saving or dismissing
text-entry dialogs, including focused text fields and emoji-only input. No mouse
click outside an already dismissed dialog is used to repair accessibility.
The version catalog applies this owner-approved prerelease consistently to
Android, desktop, iOS and PWA shared Compose dependencies.

## GitHub Pages deployment

One-time **owner actions** (this change does not alter settings or secrets):

1. After reviewing and merging the stack bottom-to-top into `main`, open
   **Settings > Pages > Build and deployment > Source** and choose **GitHub Actions**.
   A Pages site has one source: do not also select branch publishing from `/docs`.
2. If the repository restricts allowed Actions, allow the official checkout,
   Java/Node/Gradle setup, artifact upload and `actions/upload-pages-artifact`
   / `actions/deploy-pages` actions used by the workflow. Do not enable global
   write workflow permissions; the deployment job declares its own Pages and
   OIDC permissions. No deployment secret is needed.
3. Review the **github-pages** environment's deployment protection rules so
   only `main` is allowed. Approve a pending deployment if your protection rules
   require approval; retain that protection rather than bypassing it.
4. If the merge-triggered run happened before Pages was enabled, run
   **Actions > Build and deploy PWA > Run workflow**, selecting **main**.
5. Verify <https://anod.github.io/charging-timer-ev/> loads and installation is
   offered where supported. Verify
   <https://anod.github.io/charging-timer-ev/privacy.html> is publicly accessible
   and use this unchanged privacy URL in Play Console.

The workflow builds `:webApp:wasmJsBrowserDistribution`, runs portable/shared
tests, UI-string checks and real Chromium checks. It copies **every** generated
distribution file unchanged into `webApp/build/pages`, including Wasm/JS,
`composeResources`, `manifest.webmanifest`, icons and the generated `sw.js`.
It renders `docs/privacy.md` as `privacy.html` beside the app; the worker excludes
privacy paths from its precache. Relative manifest identity/start URL/scope stay
`./`, so installation and worker scope remain under `/charging-timer-ev/`.

Chromium serves that exact packaged site at the project path, checks
installability and privacy isolation, then exercises offline reopen, safe updates
and failure recovery. Evidence includes a site inventory and phone/desktop
light/dark screenshots. `github-pages` is the deployable Pages tar artifact;
`browser-storage-evidence` retains build, browser and test evidence.

Pull requests targeting **any** stack branch and manual non-main runs build and
verify only; their deploy job is skipped. Only a push to `main` or a manual
dispatch on `main` can deploy the verified artifact. The deployment job uses
`pages: write`, `id-token: write`, the `github-pages` environment and a
non-cancelling deployment concurrency group. Native Android validation remains
in **Build Android App** (manually dispatch it for stacked PR heads).

Until the stack is merged and owner setup succeeds, CI artifact verification
does not prove that the public HTTPS site has been deployed.
