# Button roles

App actions use the shared composables in `AppButtons.kt`. Filled, outlined, and destructive action buttons share a 56dp minimum height, the app's small shape, and Material 3 label typography; text and icon actions keep a 48dp minimum touch target.

| Role | Appearance and use | Current locations |
|---|---|---|
| Primary | Filled primary action for the main positive or forward action. | Configuration: Start Timer; Running: Apply; End summary: Done; Charging Power settings: Add custom power; browser backup: Export. |
| Secondary | Outlined action paired with a primary action or used for a non-destructive alternative. | End summary: Share; browser backup: Import. |
| Destructive | Filled error-colored action for irreversible removal or replacement. | Preset deletion, history deletion, learned-estimate reset, and backup restore confirmation on Android and web. |
| Text | Low-emphasis action, especially for dialogs, inline editing, and compact adjustments. | Other dialog and sheet actions, browser retry/notification permission, car-reading edit, and percentage +/- controls. |
| Icon | Icon-only action with a minimum 48dp target and its existing accessibility label. | Settings navigation, preset info/delete, history delete, and charging-power removal. The Running speed affordance uses the same unfilled icon treatment inside its 48dp-or-larger labeled row action. |

Use the text role for dialog and sheet dismissal or confirmation actions; use the destructive role when confirmation commits a destructive operation. Preserve each action's existing callback and accessibility semantics when changing its visual role.

Configuration card expand/collapse controls remain Material chips and a card-header button rather than action buttons; both targets are at least 48dp.
