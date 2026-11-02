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
nonblank stored names, non-empty positive power options and ordered
segments, finite positive capacity/power, percentages in `[0,100]`, and
efficiency in `(0,1]`. Numeric values must also be representable by the app's
Float-based calculator. An end timestamp cannot precede the last segment.
Emoji, including joined sequences, are preserved without normalization.
Names are restored as existing stored names, using the same grandfathering rule
as storage updates: legacy overlong, padded or case-colliding names are preserved
verbatim rather than making an old installation impossible to back up. The
current create/rename policy (trimmed, case-insensitively unique, at most 32
Unicode code points) still applies to new names entered in the UI.

Restore is disabled while charging and checked again under the repository mutex.
Restore reproduces the file's nonempty preset set, ordering and stored names
exactly, independently of destination presets. An included `custom` identity may
have been renamed; if absent, it is not retained or recreated. This follows the
owner's explicit replace-all decision and supersedes the original protected-Custom
wording in issue #42. An unknown selected preset falls back to the first imported
preset in the app's normal ordering (`position`, name, id).
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

## Database alignment and compatibility

The small serializable backup models are the versioned file contract, not another
storage layer. SQLDelight generates its database models from SQL; tying JSON to
those generated classes would couple old files to schema changes and SQLite
implementation details. These are the intentional mappings:

| SQL data | JSON representation |
|---|---|
| `preset` columns | `presets[]`: same identity, stored name, capacity, power, target and position |
| `power_option.preset_id`, `position`, `power_kw` | Parent preset, `powerOptions[]` array order and values |
| `charge_session` settings, identity, end values and flags | `sessions[]` fields, including `presetEdited`; snake_case becomes camelCase |
| `power_segment.session_id`, `started_at`, `power_kw` | Parent session and `segments[]` timestamps/power |
| `charge_session.is_running` | Excluded; always false on import |
| `storage_migration` | Excluded; device-local marker, current-session pointer cleared |
| Active DataStore preferences/draft | Typed `preferences` fields; obsolete migration/session keys excluded |

Additive optional JSON fields must have defaults so existing version-1 files
remain readable. Required shape or meaning changes need a new format version and
an explicit old-version reader/upgrader; never silently reinterpret old fields.
There is no invented version-0 migration. The fixed first-version compatibility
fixture must keep working when a future reader is added. Newer versions fail
with a specific error even if their payload shape has changed.

## Resource use

All document I/O runs on Android's IO dispatcher; encode/decode/validation and SQL
work run off the UI thread. The reader decodes directly into typed models rather
than retaining a second full JSON object tree. It first reads a small version
header and ignores unknown fields; numeric/Boolean serializers reject quoted
values rather than coercing wrong types. A nesting-depth limit of 32 also applies
to unknown fields, preventing deeply nested documents exhausting the parser stack.

ID checks are hash-based and ordered-segment validation allocates no pair list.
Export uses four bulk queries regardless of history/preset count. Import uses
bulk deletes without reading all destination child rows, and inserts preset
positions directly rather than recalculating the maximum position for every row.
The large-history regression checks those query counts with 2,000 sessions.

The 16 MiB limit is a **file-size** cap, not a promise of 16 MiB peak heap usage:
the bounded UTF-8 bytes, decoded text and typed models still coexist during
parsing; export also constructs its typed snapshot and output bytes. This is
an explicit user-triggered operation, not a continuously running background task.
