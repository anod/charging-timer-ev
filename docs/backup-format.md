# Manual backup format

Android Settings > Backup & restore and the staged browser storage screen can
export or restore the same UTF-8 JSON document.
The Android-only section reuses Settings' tonal group and transparent icon rows,
with an inset divider between Export and Restore. History and preset-management
navigation remain in the Charging group; no animated background runs in Settings.
It is a logical export, not a SQLite database copy. It works independently of
database schema versions, journaling, Google accounts, installation source and
signing keys. The file is plain text, not encrypted. Store it somewhere private.

## Version 2

The root object has these required fields:

| Field | Meaning |
|---|---|
| `format` | Exactly `ev-charging-timer-backup` |
| `formatVersion` | Integer `2` |
| `exportedAt` | ISO-8601 instant, generated in UTC |
| `appVersion` | Exporting app's build version |
| `preferences` | Simple preferences and optional working draft |
| `presets` | Non-empty array of presets |
| `sessions` | Array of finished history entries |

Unknown object keys are ignored. A newer format version is rejected with a
specific message. Version 1 remains readable: its original efficiency limit
`(0,1]` is validated before upgrading to version 2, and learning consent is
always set to false, even if an unknown consent field was present in that file.
Version 1 segment factors remain null. Version 2 persists explicit consent and
an optional frozen factor per segment. Snapshot and segment factors are in
`(0,1]`; a null segment factor uses the unchanged snapshot efficiency for legacy
sessions. No SQLite migration is required to read a JSON backup.

Example (additional sessions and presets omitted):

```json
{
  "format": "ev-charging-timer-backup",
  "formatVersion": 2,
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
    "calibrationEnabled": false,
    "segments": [{"startedAt": 1789990000000, "powerKw": 7.0, "efficiency": null}]
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
and edited flags default to `false`. Version 2's `calibrationEnabled` also
defaults to `false`; acknowledgment (`reviewed`) never implies learning consent.
Timestamps are epoch milliseconds.
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
snapshot efficiency in `(0,1]`; nullable segment efficiency, when present, is
also in `(0,1]`.
Segment bounds are enforced at the Kotlin model and backup-import boundaries,
not by a database CHECK; the session efficiency CHECK remains in place.
Numeric values must also be representable by the app's
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
This normalization never changes calibration consent. Interrupted sessions,
including imported unfinished timers, cannot contribute to learned estimates.
Per-preset, per-speed reset clears consent for matching single-speed rows across
all capacities, retaining car readings and snapshots. Version 2 exports/restores
these flags and every segment factor. A later explicitly opted-in reading save
can teach again. Factors and eligible-reading counts are derived from history,
not exported as an accumulator.

Picker cancellation does nothing. Read/parse/validation errors leave data
unchanged. Export uses `openOutputStream(uri, "wt")`, so replacing an existing
file cannot leave trailing bytes. No storage permissions or persisted URI grants
are used. Desktop and iOS share the codec/importer but have no picker or Settings
section yet.

`ManualBackupTest` includes SQLite table/column and DataStore key coverage guards.
New persisted fields must be covered by this format or explicitly excluded with
a reason. Android Auto Backup and device-transfer rules are unchanged.

## Browser storage (stack layer 2)

The portable `:storage` module owns the existing v2 codec and settings model.
`:shared` re-exports these types; Android/iOS/JVM still use the same SQLDelight
and DataStore implementation. No native migration or backup-rule change is needed.
The browser does not use a SQLDelight worker or pretend synchronous browser I/O.
`BrowserStorage` exposes suspending load/update/import/export operations over
`BrowserDocumentStore`; a later runtime adapter can map the existing repository
interfaces onto this API without blocking the browser thread.

IndexedDB database `ev-charging-timer`, version 1, store `state`, key `app` holds
a revision token and an opaque JSON string. The internal envelope contains
`schemaVersion: 1`, the v2 backup document, and an optional `activeSession`.
The active session is preserved by load/update but excluded from manual export;
it prevents restore. This layer never starts or reconciles a timer. Only the
backup/storage screen is enabled until the timer/runtime stack layer.

Preferences, drafts, preset order/options, all history fields, learning consent
and segment efficiencies commit together in a strict-durability IndexedDB
transaction. Writes resolve on transaction completion, not request success.
Every update reads the committed record and checks its revision again inside
the write transaction. Concurrent tab writes fail explicitly rather than
overwriting unseen edits. The screen's counts refresh on load/import, not
automatically across tabs. There is no memory or localStorage fallback.
This intentionally simple backend rewrites the complete state document for each
mutation. The codec runs on the browser main thread; large imports/exports can
briefly pause rendering. The 16 MiB file limit is not a peak-memory or latency
guarantee. There is no worker-thread or incremental-storage implementation.
Unavailable/denied/blocked storage, quota errors, corrupt/unsupported internal
documents and conflicting revisions are errors, never default-data recovery.
Fresh defaults are published only after their first IndexedDB commit succeeds.

Browser import uses the unchanged reader, 16 MiB byte limit and strict UTF-8
decoding. Cancel leaves data alone. Replacement requires confirmation and
normalizes unfinished history, newest review and missing selection exactly as
native restore does. Unlike SQL + DataStore, the browser replacement is one
atomic transaction. Failed validation or a failed/aborted commit leaves the
previous record intact. JavaScript never parses the backup payload, preserving
Kotlin Long IDs/timestamps beyond JavaScript's safe integer range.

Export creates a JSON Blob and requests a download only after successful
load/encoding. Browsers cannot confirm a user's final file save through the
anchor-download API: the UI explicitly says **Download requested**, not saved.
Check the browser's downloads and permissions. Picker and download initiation
errors are surfaced; downloaded files are unencrypted.

IndexedDB is scoped to the site's origin/browser profile. Clearing site data,
private-browsing teardown, browser eviction or changing hosting origin can lose
data. It is not Android Auto Backup, cloud sync, or guaranteed archival storage;
export private backups before moving sites/devices. No service worker, offline
cache, notifications or deployment is included in this layer.

## Database alignment and compatibility

The small serializable backup models are the versioned file contract, not another
storage layer. SQLDelight generates its database models from SQL; tying JSON to
those generated classes would couple old files to schema changes and SQLite
implementation details. These are the intentional mappings:

| SQL data | JSON representation |
|---|---|
| `preset` columns | `presets[]`: same identity, stored name, capacity, power, target and position |
| `power_option.preset_id`, `position`, `power_kw` | Parent preset, `powerOptions[]` array order and values |
| `charge_session` settings, identity, end values and flags | `sessions[]` fields, including `presetEdited` and `calibrationEnabled`; snake_case becomes camelCase |
| `power_segment.session_id`, `started_at`, `power_kw`, nullable `efficiency` | Parent session and `segments[]` timestamp, power and frozen factor |
| `charge_session.is_running` | Excluded; always false on import |
| `storage_migration` | Excluded; device-local marker, current-session pointer cleared |
| Active DataStore preferences/draft | Typed `preferences` fields; obsolete migration/session keys excluded |

Additive optional JSON fields must have defaults so supported older files
remain readable. Version 1's explicit reader/upgrader preserves old bounds and
uses the snapshot-factor fallback for every segment.
Required shape or meaning changes need a new format version and
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
