# Adaptive layouts

The shared Compose screens classify the available app width as compact below
600 dp, medium from 600 dp, and expanded from 840 dp. Height below 480 dp is
short independently of width. These are window dimensions, not device types,
and are re-evaluated as the window changes.

- Configuration uses its existing scrolling column on compact and medium
  widths, and two card columns on expanded widths unless the window is short.
  The pinned Start action remains visible and is limited to 720 dp.
- Running keeps the battery and timeline above the statistics on compact,
  medium, and short windows. Expanded, non-short windows place them beside the
  statistics; the Stop action remains pinned and is limited to 720 dp.
- Settings, charging powers, presets, history, licenses, and summaries retain
  their existing navigation and scrolling, centered in a maximum 720 dp page.
- Timer content is capped at 1120 dp in the two-column layout. Smaller and
  short layouts use a maximum 720 dp content width.

Resizing changes layout only; it does not change the navigation stack or timer
state. The animated background remains exclusive to Running. Settings
list/detail navigation is a separate follow-up.
