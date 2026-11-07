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
| Android Chrome | Pixel 6a / Chrome 156 regular-tab light/dark system-bar checks passed locally; installed WebAPK not device-verified | Install app / Add to Home screen |
| iOS Safari | Expected on current Wasm GC-capable Safari (iOS 18.2+); **not verified on a physical device** | Manual Safari Add to Home Screen |
| Firefox desktop | Local production PWA tests cover the original waiting-worker transition, refresh updates, multi-window isolation and offline persistence | No desktop install flow promised |
| Other/in-app browsers | Not verified; use a supported full browser | Not promised |

This matrix describes the initial verification scope, not a guarantee for every
browser/OS combination. Permissions, storage policies and installation support
are browser-controlled.

## Theme and Android system bars

The document declares separate light/dark `theme-color` media queries and paints
the page background in the same colors as the shared Compose theme, including
before Wasm starts. Changing the system theme changes these document colors
without reloading or changing timer/storage state.

The manifest keeps light defaults and supplies dark overrides using the
[draft `color_scheme_dark` member](https://www.w3.org/TR/appmanifest/#color_scheme_dark-member).
Browsers without support ignore that member. This is progressive enhancement,
**not a verified fix for Android WebAPK launch or navigation bars**:
Chromium's [manifest parser](https://github.com/chromium/chromium/blob/f88ff908527c8f3a8068f0fe91889675741ba0d6/third_party/blink/renderer/modules/manifest/manifest_parser.cc)
currently reads only the top-level color values (checked November 2026).
Its Android shell has dark-color fields, but those fields alone do not demonstrate
manifest support. Existing WebAPKs also retain installed metadata until the browser
updates them; refreshing the page is not a WebAPK metadata update.

The document theme color can affect browser/status-bar chrome; Android and the
browser retain final control. Installed-PWA navigation bars may use the installed
manifest theme color and launch backgrounds may use its background color, leaving
these areas light on engines that ignore the dark override. Real Android
before/after light/dark captures, including cold launch, are required to confirm
behavior for a particular browser/version; desktop phone emulation is not proof.

On a Pixel 6a with regular Chrome `156.0.8078.81`, November 2026 local
before/after captures verified the gesture-navigation background changing from
white in both themes to the app background (`#FBFDF8` light, `#191C1A` dark).
The status bar already followed Chrome's theme in the baseline and retained
Chrome's own tint (`#F8FAF3` light, `#0B0F0C` dark), not the exact app color.
These checks used the browser's **System default** theme, real Android night-mode
changes and a localhost production shell with a matching offline-ready worker;
no media emulation or installed-PWA launch was involved. They do not establish
WebAPK splash behavior, three-button navigation or iOS device support.

[Chrome's edge-to-edge opt-in](https://developer.chrome.com/docs/css-ui/edge-to-edge)
(`viewport-fit=cover`) can let the page paint behind a transparent gesture bar,
but does not recolor an installed WebAPK splash or guarantee three-button bar
colors. It also requires safe-area handling for the full Compose canvas, dialogs,
backing text editors and bottom snackbar/controls. It is not enabled here: an
unverified viewport change would risk obscuring controls and changing iOS layout.

For unsupported WebAPKs, the safe choices are to retain light launch defaults
until native dark-manifest support ships, or separately validate an Android
edge-to-edge layout to address the gesture bar only. A permanently dark manifest
regresses light mode; switching manifest URLs/colors at runtime cannot reliably
change already-installed cold-launch metadata and is not used.

Local checks: run `npm test` in `webApp` to check the document/manifest against
`Theme.kt`, then `npm run test:theme` after building the production distribution.
The browser check covers pre-Wasm page paint, light-dark-light media changes,
manifest parsing and unchanged phone/desktop canvas geometry. A supplied prior
HTML snapshot is served from a copied shell with its worker inventory regenerated;
captures require offline-ready state to reject fixture-induced error snackbars.
Its screenshots
are browser viewports only, not Android system-bar evidence.

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
shell. **Refresh this window** to apply a fully downloaded update. An update
discovered and installed within 15 seconds of the refresh's setup also applies,
with at most one internal reload. Slower downloads show **Update ready** for the
next refresh. Update and offline-error feedback uses an indefinite Material 3
snackbar in a dedicated bottom layout slot, not an overlay on timer/navigation
controls. **Refresh** waits for pending timer/storage operations before invoking
normal browser refresh; a failed storage operation prevents that refresh.
**Details** opens Settings with persistent recovery and backup guidance. Dismissing
a snackbar does not hide the detailed status in Settings. Other open timer windows are not reloaded and keep their matching
shell assets, including lazy-loaded resources. No charging storage is cleared or
migrated. Offline or failed downloads leave the last complete shell usable.
Firefox can label a worker-served refresh as a normal navigation; a per-window
history-entry marker recognizes the departing/reloaded document without treating
a new tab or Back/Forward as a refresh. Session Storage also bounds the internal
reload to one per refresh flow; if it is unavailable, automatic reloading is disabled.

Shell inventories and document-to-version bindings live in Cache Storage, separate
from charging data. Bindings are persisted before activating a waiting worker;
new document navigations select the current build. Old shells are retained while
controlled windows exist and reclaimed on a later client-free activation. New
shells use a separate namespace so a legacy activation cannot delete an installing
upgrade. Cleanup only considers predecessors recorded at install start, never a
later worker's installing/waiting cache. On the
first upgrade from the original worker, the inventory is reconstructed and
authenticated against the original byte-derived cache version using a frozen
copy of that worker's template. Missing/corrupt legacy inventories reject the
upgrade rather than blessing changed bytes. Missing legacy assets fail explicitly rather than
being repaired using a different deployment. Subsequent builds retain the original
generated SHA-256 inventory for verification and same-version repair. Privacy
paths remain network-only for every build.

The original browser adapter cannot activate a waiting worker or internally reload.
The first upgraded worker therefore activates automatically **after** downloading
and verifying its entire shell and pinning existing documents. Those documents
stay on their old shell until a navigation/refresh. If that initial legacy refresh
itself discovers the upgrade, one further refresh is necessary; forcing a reload
of an unidentified legacy window would risk interrupting another active timer.
After this compatibility transition, updates discovered during refresh use the
bounded internal reload above. Closed-document bindings are pruned during
navigations and activation requests, so a long-lived window does not cause
unbounded binding growth.

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

The app uses stable Compose Multiplatform `1.12.2` with its release-table
Material3 component `1.12.0-alpha03`, and Android compile SDK 37. The owner
accepted the known [CMP-9368 dialog accessibility limitation](https://github.com/JetBrains/compose-multiplatform-core/commit/d97ec456148a188550f4a45243e3544a6ad47321):
opening a dialog replaces the main semantics owner, and closing it may leave
screen readers with a phantom dialog or an empty accessibility tree even though
the screen paints correctly. This is a real accessibility limitation, not a
successful restoration. Upgrade once a stable release contains the upstream fix.

The trial of `1.13.0-beta02` restored the semantics tree but exposed a browser
text-selection synchronization crash. The trial was removed rather than
patching the prerelease dependency. Stable Compose also omits navigation-event
dispatch for Escape from its focused backing editor. The owner-approved
PWA-only adapter forwards unhandled editor Escape to the existing dispatcher;
shared/native dialogs and their dismissal/busy guards remain unchanged.
IME/composing, handled, repeated and canvas events are excluded. Its explicit
navigationevent-compose `1.1.0` declaration matches the existing transitive
dependency in Compose `1.12.2`; no dependency version is upgraded.
When Tab removes a backing editor and drops focus onto the page body, the
web adapter returns focus to the Compose canvas after Compose's own focus moves
have settled. Pointer input, a real next focus target, hidden pages and unfocused
windows cancel or prevent this recovery. Escape still uses the existing canvas
path; the adapter does not handle body or canvas Escape.

Browser tests record CMP-9368 explicitly after proving painted dismissal and
correct persistence for typed/emoji Save, Cancel, outside-click and Escape cases.
Typing errors, failed dismissal, changed data on cancellation and navigation
errors remain failures. No unrelated mouse click is used to repair accessibility.
Subsequent independent workflows may reload the page to establish a fresh
semantics owner; this is not reported as automatic accessibility restoration.

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
