# Manual backup format

Android Settings > Backup & restore can export or restore a UTF-8 JSON document.
It is a logical export, not a SQLite database copy. It works independently of
database schema versions, journaling, Google accounts, installation source and
signing keys. The file is plain text, not encrypted. Store it somewhere private.

## Version 1

The root object has these required fields:

| Field | Meaning |
|---|---|
| `format` | Exactly `ev-charging-timer-backup` |
| `formatVersion` | Integer `1` |
| `exportedAt` | ISO-8601 instant, generated in UTC |
| `appVersion` | Exporting app's build version |
| `preferences` | Simple preferences and optional working draft |
| `presets` | Non-empty array of presets |
| `sessions` | Array of finished history entries |

Unknown object keys are ignored. A newer format version is rejected with a
specific message; version 1 is the first released version, so there is no older
released format to upgrade yet. Future version readers must upgrade supported
older versions before import, not rely on SQLite migrations.

Example (additional sessions and presets omitted):

```json
{
  "format": "ev-charging-timer-backup",
  "formatVersion": 1,
  "exportedAt": "2026-11-01T09:30:00Z",
  "appVersion": "2026.1101.0930",
  "preferences": {
    "selectedPresetId": "custom",
    "startPercent": 20.0,
    "notifyAt80": true,
    "draft": null
  },
  "presets": [{
    "id": "custom",
    "name": "Default",
    "batteryCapacity": 60.0,
    "chargingPower": 7.0,
    "targetPercent": 80.0,
    "position": 0,
    "powerOptions": [7.0, 11.0]
  }],
  "sessions": [{
    "id": 12,
    "batteryCapacity": 60.0,
    "startPercent": 20.0,
    "targetPercent": 80.0,
    "efficiency": 0.9,
    "presetId": "custom",
    "presetName": "Default",
    "endedAt": 1790000000000,
    "endPercent": 80.0,
    "actualPercent": null,
    "reviewed": true,
    "interrupted": false,
    "presetEdited": false,
    "segments": [{"startedAt": 1789990000000, "powerKw": 7.0}]
  }]
}
```

`position` retains preset ordering (default `0` for hand-authored version-1 files).
Power options are nested in their preset; array order represents their position.
Import renumbers option positions from zero, preserving order and values rather
than unused gaps. Segments are nested in their session, sorted by strictly
increasing `startedAt`. This representation cannot express an orphan option or
segment: each must have a valid parent with its required identity. Session
`presetId` is deliberately not a foreign key; deleted preset identities remain
valid historical information.

`preferences` requires `selectedPresetId` and `startPercent`. `notifyAt80` defaults
to `true`; `draft` defaults to `null`. A draft contains `presetId`,
`batteryCapacity`, `chargingPower`, `targetPercent` and `powerOptions`, and must
belong to the selected, listed preset. It preserves unsaved working edits, not
another preset. Legacy settings keys are not exported; their migrated values are
already in presets. No active-session preferences, notification dismissal state,
installation markers or migration bookkeeping are included.

Session numeric settings, `id` and `segments` are required. Nullable preset
identity/name and end/actual percentages default to `null`; review, interruption
and edited flags default to `false`. Timestamps are epoch milliseconds.
Exports contain only non-running rows with an end timestamp. A manually authored
row without `endedAt` is imported as interrupted history with unknown end
percentage and a restore timestamp (at least its last segment time), never as a
running session.

## Validation and replacement

The reader limits input to **16 MiB**, including for providers with unknown or
incorrect document sizes. It validates the complete document before import:
required fields/types, format/version, timestamp, unique preset/session IDs,
current preset naming rules (trimmed, non-empty, case-insensitively unique,
at most 32 Unicode code points), non-empty positive power options and ordered
segments, finite positive capacity/power, percentages in `[0,100]`, and
efficiency in `(0,1]`. Numeric values must also be representable by the app's
Float-based calculator. An end timestamp cannot precede the last segment.
Emoji, including joined sequences, are preserved without normalization.

Restore is disabled while charging and checked again under the repository mutex.
After confirmation, all SQL replacement writes execute in one transaction;
any row failure rolls back deletions and inserts. DataStore preferences apply
only after SQL commits. SQL and DataStore cannot share a transaction: a later
preferences write failure is reported, not silently treated as success.
The current-session pointer is cleared. The newest imported history entry is
marked reviewed even if the file says otherwise, preventing a stale charge
summary and completion notification; older review states are retained.

Picker cancellation does nothing. Read/parse/validation errors leave data
unchanged. Export uses `openOutputStream(uri, "wt")`, so replacing an existing
file cannot leave trailing bytes. No storage permissions or persisted URI grants
are used. Desktop and iOS share the codec/importer but have no picker or Settings
section yet.

`ManualBackupTest` includes SQLite table/column and DataStore key coverage guards.
New persisted fields must be covered by this format or explicitly excluded with
a reason. Android Auto Backup and device-transfer rules are unchanged.
