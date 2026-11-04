---
title: EV Charging Timer privacy policy
---

# EV Charging Timer privacy policy

EV Charging Timer (`info.anodsplace.evtimer`) estimates charging progress and
remaining time from values you enter. It does not connect to or control your car.

## Local data and Android backup

The app does not collect, transmit, sell, or share personal data. It has no
accounts, advertising, analytics, tracking, or remote server connection.
Battery capacity, charging power, charge targets, and timer state are stored
locally on your device, along with presets and charging history.
If enabled in your Android settings, these records and simple app preferences
can be included in your Google/Android cloud backup or transferred to a new device.
Android and your backup provider manage that backup; the developer has no access
to it. Notification-dismissal state, caches and device-local installation markers
are excluded. Restored running timers are saved as interrupted history and do not
automatically resume charging notifications.
You can delete local data by clearing the app's storage in Android settings or
uninstalling the app.

Manual backup files are created only when you request an export in Settings and
are saved where you choose using Android's system document picker. They contain
presets, charging history and simple preferences as plain text JSON, **not
encrypted**. The app never uploads them. Anyone with access to the file can read
it, so choose a trusted location and delete files you no longer need. Restore
reads only the document you choose and asks before replacing local data.

## Browser storage and notifications

The web app stores charging data and preferences locally in the browser's
IndexedDB for this site. It downloads app assets from GitHub Pages over HTTPS
and caches the app shell for offline use; it does not upload charging data.
GitHub Pages processes ordinary website requests under GitHub's privacy policy.
The privacy policy page is fetched online and is not part of the offline cache.

Browser notifications are optional and foreground-only. No background alarms
or notifications are promised when the page is hidden, suspended or closed.
Estimates catch up from saved timestamps when the app is reopened.
Browser storage eviction, private browsing or clearing site data can lose local
records. Manual backup export downloads an unencrypted JSON file only on request;
restore reads the file you select and asks before replacing local data.
The developer cannot access browser records or backup files.

## Android notifications and background operation

Notification permission lets the app show charging estimates, remaining time,
and a stop action outside the app. A foreground service keeps a user-started
timer running with a visible ongoing notification while the app is in the
background. These permissions do not give the app access to your vehicle,
location, contacts, or other apps' notifications.

After a device restart or app update, the app restores a running timer's
notification (unless you dismissed it) and its future completion and optional
80% reminders. If the completion estimate has already passed, it shows the
completion notification instead; this may repeat after another restart or update.
The receive-boot permission enables this recovery on the same installation;
timers restored from a backup or device transfer remain interrupted.

The app does not currently request exact-alarm permission. Timer and notification
features are local; they do not upload charging information.

## Google Play

Google Play distributes the app and operates under Google's own privacy policy.
The app itself has no network upload of usage or charging information. Android
may back up app data to the user's backup provider as described above; this is
separate from data collection by the developer.

## Changes and contact

Updates to this policy will be published on this page as the app changes.
For privacy questions, contact the maintainer through the
[project's GitHub issues](https://github.com/anod/charging-timer-ev/issues).
