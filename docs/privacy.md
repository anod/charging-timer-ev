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

## Notifications and background operation

Notification permission lets the app show charging estimates, remaining time,
and a stop action outside the app. A foreground service keeps a user-started
timer running with a visible ongoing notification while the app is in the
background. These permissions do not give the app access to your vehicle,
location, contacts, or other apps' notifications.

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
