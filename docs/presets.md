# Presets

Open Settings from the Configuration header gear to add, edit, delete or reorder presets.
Names can include emoji and must be unique (case-insensitive), with a maximum of 32 Unicode
code points. The built-in Default preset (internal ID `custom`) always stays first and cannot
be renamed, deleted or moved; its charging values and power options remain editable.

In the preset editor, tap a power chip to remove it or use Add to insert a power option.
At least one option must remain. Removing the selected power selects the first remaining
option. Changes are committed with Save; Cancel leaves the preset unchanged.

Deleting a preset requires confirmation. Deleting the selected preset selects Default and
clears its working draft, while preserving the start percentage. Reordering and editing other
presets do not discard Configuration's unsaved working edits.

Preset values, power options and ordering live in SQLDelight `charging.db`. Schema migration
3 to 4 initializes ordering from the previous alphabetical order. Existing Android cloud
backup and device-transfer rules include this database and the DataStore selection/draft
preferences; no additional backup paths are required.
