# Presets

Configuration remains the place to edit capacity, power and target, and to add presets
with the preset-row **+**. Working changes show an asterisk on the selected preset chip.
Tap that chip to Update the saved preset or Reset the working changes. This applies to
Default too; changes are not silently written over saved presets.

The expanded **Charging limit** card configures the percentage interval and power
cap used for planning. Its defaults are 80% to 100% with a 22 kW cap. The 22 kW
default is an owner-selected, configurable planning approximation, not a universal
AC-speed limit or an established fleet average. Choose values appropriate to the
vehicle and charging setup. Within `[start%, end%)`, estimates use the lower of the
selected charging power and cap; outside that interval they use the selected power.
The cap applies to remaining-time and end-time estimates, time to 80%, timeline
predictions, notifications and alarm scheduling, and the configuration estimate.
The same capped nominal energy is used for charging calibration.

Taper settings are saved with each preset, including Default and Custom. Updating
a preset saves its percentage bounds and cap together with its other settings.
Starting a timer copies these values into its session snapshot, so later preset
edits do not change a running estimate or the saved history. Existing presets and
Custom receive the defaults during the SQLDelight schema migration.

Settings uses one compact row per preset. Drag its handle to reorder, tap the edit
(pencil) button to rename/add emoji, or tap the trash icon for confirmed deletion. Reordering is previewed
during the drag and saved once on drop; cancelling the gesture leaves the saved order
unchanged. Screen readers also expose Move up/Move down actions on each row.
Default is an ordinary preset: it can be renamed, moved or deleted. At least one preset
must remain, so deletion is disabled for the last preset. Deleting the selected preset
selects the first remaining one, clears the deleted preset's draft and preserves Start.
Names can include emoji and must be unique (case-insensitive), with a maximum of 32 Unicode
code points. Default is not a reserved name after it has been renamed or deleted.

In Configuration's expanded Charging Power header, tap the pencil to remove unwanted
chips, then the checkmark to return to selection. The **+** is hidden while removing.
At least one option must remain. Removing the
selected power selects the first remaining option. The existing **+** adds power options.
Power changes follow the same working-draft/Update/Reset behavior as other charging values.

Presets, power options and ordering live in SQLDelight `charging.db`. Schema migration
3 to 4 preserves the previous order (Default first, then names alphabetically), and
the later taper migration adds the per-preset bounds and cap with their defaults.
These are only initial migration values, not a permanent constraint. Existing Android
cloud backup and device-transfer rules include the database and DataStore
selection/draft preferences.
A deleted Default is not recreated on restart or restore unless all preset data is missing.
