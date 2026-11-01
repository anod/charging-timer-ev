# Presets

Configuration remains the place to edit capacity, power and target, and to add presets
with the preset-row **+**. Working changes show an asterisk on the selected preset chip.
Tap that chip to Update the saved preset or Reset the working changes. This applies to
Default too; changes are not silently written over saved presets.

Settings provides only name/emoji changes, confirmed deletion and Move up/Move down.
Default is an ordinary preset: it can be renamed, moved or deleted. At least one preset
must remain, so deletion is disabled for the last preset. Deleting the selected preset
selects the first remaining one, clears the deleted preset's draft and preserves Start.
Names can include emoji and must be unique (case-insensitive), with a maximum of 32 Unicode
code points. Default is not a reserved name after it has been renamed or deleted.

Under Configuration's Charging Power, choose **Remove power options** to tap unwanted
chips, then **Done** to return to selection. At least one option must remain. Removing the
selected power selects the first remaining option. The existing **+** adds power options.
Power changes follow the same working-draft/Update/Reset behavior as other charging values.

Presets, power options and ordering live in SQLDelight `charging.db`. Schema migration
3 to 4 preserves the previous order (Default first, then names alphabetically); that is
only the initial migrated order, not a permanent constraint. Existing Android cloud backup
and device-transfer rules include the database and DataStore selection/draft preferences.
A deleted Default is not recreated on restart or restore unless all preset data is missing.
