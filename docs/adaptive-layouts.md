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
- Settings uses Navigation 3's list-detail scene strategy at the expanded
  breakpoint (840 dp or wider and at least 480 dp high). The Settings list stays
  in the first pane while Charging Power, Presets, History, Licenses, and a
  History summary appear in the detail pane. With no selected detail, the pane
  shows a localized prompt. Detail headers omit the back arrow in this layout;
  system Back or Escape returns to the list, then exits Settings.
- Below that breakpoint, Settings and its destinations keep the existing
  single-pane push navigation and back-arrow headers. Their content remains
  centered in a maximum 720 dp page.
- Timer content is capped at 1120 dp in the two-column layout. Smaller and
  short layouts use a maximum 720 dp content width.

Resizing and fold/unfold change layout only. The Navigation 3 back stack remains
the source of the selected detail, and entry state, dialogs, drafts, and the
running timer are retained across the layout change. History summaries remain
reachable from History in either layout. The animated background remains
exclusive to Running.
