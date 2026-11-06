# Performance baseline

Relates to [#66](https://github.com/anod/charging-timer-ev/issues/66)

This is a measurement-only baseline for the app revision listed below. It does not repeat the security audit for #66; that work and its remediation were reviewed separately. No app or production source files were changed to collect these measurements.

## Environment

| Area | Environment |
|---|---|
| Measured source revision | `a005d386fd3a2965b8baaf5f3d57f775cf485327` |
| Measurement date | 2026-11-06 |
| Android device | Pixel 6a; Android 17 / API 37; 1080 × 2400 at 420 dpi; approximately 60 Hz (frame interval about 16.75 ms) |
| Android app | Version `2026.1106.1957`, target SDK 37; only the isolated package `info.anodsplace.evtimer.themecheck` was installed |
| Android build | Release variant with R8, resource shrinking, and the committed Baseline Profile assets. A session-only Gradle init script added the isolated application ID suffix and avoided the owner's release signing configuration; a temporary local test key was used. The profile assets were present, but on-device profile compilation was not forced or verified. No no-profile comparison was made. |
| Device state | Screen on; 80% battery and connected to power; thermal status 0; auto brightness (reported setting 29); font scale 1.0; dark mode; window and transition animation scales 1.0; animator scale unset; 30-second screen timeout; stay-awake while connected to power |
| JVM measurements | Windows host, project-configured JVM, temporary JUnit harness, in-memory SQLDelight/JDBC database, and a temporary DataStore preferences file. CPU/RAM model was not recorded; timings are host-specific. |

The connected device used wireless ADB. Public commands below use `$DEVICE` as a placeholder and intentionally omit transport identifiers. Host command wall time was not used for device timing.

## Method

### Android

The app was installed as the isolated release package. Cold launch was measured after force-stopping the package; warm launch used the already-running activity without force-stop. Each launch used `am start -W`; its device-reported `TotalTime` and `WaitTime` are launch-ready/first-display proxies, not independent visual time-to-first-frame measurements.

```powershell
$ADB = 'C:\Android\platform-tools\adb.exe'
$DEVICE = '<verified ADB transport>'
$PACKAGE = 'info.anodsplace.evtimer.themecheck'
$COMPONENT = "$PACKAGE/info.anodsplace.evtimer.MainActivity"

# Repeat the force-stop/start pair 10 times for cold starts.
& $ADB -s $DEVICE shell am force-stop $PACKAGE
& $ADB -s $DEVICE shell am start -W -n $COMPONENT

# Repeat 10 times without force-stop for warm/hot starts.
& $ADB -s $DEVICE shell am start -W -n $COMPONENT
```

For each UI flow, `dumpsys gfxinfo` was reset immediately before the scripted interaction and `framestats` was collected immediately after. One sequence was captured for each flow; frame counts are app frames in that sample, not repetitions of the flow.

```powershell
& $ADB -s $DEVICE shell dumpsys gfxinfo $PACKAGE reset
# Run one flow below using the app's UI.
& $ADB -s $DEVICE shell dumpsys gfxinfo $PACKAGE framestats
& $ADB -s $DEVICE shell dumpsys meminfo $PACKAGE
```

The navigation flow started on Settings and traversed Configuration, Settings, Presets, History, then back to Configuration. Other flows covered a roughly 20-second active Running screen, a live power change from 11.0 to 7.0 kW, creating and selecting a synthetic preset, opening the synthetic session's history summary, and stopping the timer. A separate manual backup round-trip exported a JSON file through the app UI and restored that same file; it contained one preset and one synthetic session and was 1,585 bytes. The device-side file and isolated package were removed after the test.

Memory was sampled with `dumpsys meminfo` after launch and after 185.24 seconds with the timer active. Foreground-service presence was checked, but notification/service update cost was not independently timed.

### Shared calculator, storage, and backup logic

A temporary JUnit harness, not committed to the product source, ran through the repository's `gradle-gated.ps1` wrapper. Its session-only init script injected the harness into `:shared:jvmTest` and applied the isolated release application ID suffix. The effective Gradle tasks were `:androidApp:assembleRelease` and `:shared:jvmTest --tests info.anodsplace.evtimer.data.PerformanceBaselineHarnessTest`.

The reproducible invocation shape is:

```powershell
$env:PERF_HARNESS_DIR = '<session-only harness directory>'
$env:PERF_BASELINE_RESULTS = '<private CSV output path>'
pwsh -NoProfile -File '<gradle-gated.ps1>' `
  -Label issue-66-performance-baseline `
  -WorkDir '<worktree>' `
  -GradleArgs ':androidApp:assembleRelease :shared:jvmTest --tests info.anodsplace.evtimer.data.PerformanceBaselineHarnessTest -I <session-only-isolation-init.gradle>'
```

The calculator grid comprised 240 snapshots: capacities 40/60/75/100 kWh, powers 3.6/7.2/11/22/150 kW, initial charge 10/25/50/75%, and targets 80/90/100%. Each operation had three warm-up batches and 10 measured samples, each sample covering all 240 inputs 200 times (48,000 calls/sample). Results are nanoseconds per call.

Storage and backup tests used synthetic histories of 100, 1,000, and 10,000 sessions, one preset, and one segment per session. SQLDelight used an in-memory JDBC database; each fixture used a temporary DataStore file. Each operation had one pipeline warm-up and 10 measured samples. `restore/insert` restores into an empty fixture; `history query` reads the complete history; `history calibration` reads the aggregate; `export`, `encode`, and `decode` time their named operations; `round-trip restore` restores the decoded backup into a fresh fixture. Times are milliseconds per operation. Median is the average of the middle two samples; p90 is the nearest-rank 90th percentile. The full ten-sample values are retained in the private measurement CSV.

## Results

### Startup

All 10 cold starts reported `COLD`; all 10 warm starts reported `HOT`. Values below are milliseconds from device-reported `am start -W` fields.

| Start | Metric | 10 samples | Median | p90 | Min–max |
|---|---|---|---:|---:|---:|
| Cold | `TotalTime` | 91, 92, 97, 91, 93, 89, 95, 89, 88, 92 | 91.5 | 95 | 88–97 |
| Cold | `WaitTime` | 94, 94, 100, 93, 96, 91, 97, 91, 90, 93 | 93.5 | 97 | 90–100 |
| Warm/hot | `TotalTime` | 28, 28, 25, 21, 24, 25, 21, 25, 29, 26 | 25 | 28 | 21–29 |
| Warm/hot | `WaitTime` | 30, 29, 26, 23, 26, 28, 25, 27, 30, 28 | 27.5 | 30 | 23–30 |

### UI frame timing

Frame-time percentiles and “Janky frames” are from `dumpsys gfxinfo`. The modern and legacy jank counters are both shown because they disagreed sharply on the Running sample. A zero missed-vsync count does not establish that animation was visually smooth.

| Flow (one sample) | Frames | Modern janky frames | Legacy janky frames | p50 / p90 / p95 / p99 frame time | Missed vsyncs |
|---|---:|---:|---:|---:|---:|
| Configuration and navigation sequence | 299 | 6 (2.01%) | 7 (2.34%) | 5 / 12 / 13 / 34 ms | 0 |
| Running screen, active updates (~20 s) | 1,204 | 1 (0.08%) | 1,169 (97.09%) | 25 / 27 / 28 / 29 ms | 0 |
| Active power change, 11.0 → 7.0 kW | 1,034 | 3 (0.29%) | 14 (1.35%) | 15 / 15 / 16 / 25 ms | 0 |
| Create synthetic preset | 165 | 6 (3.64%) | 5 (3.03%) | 6 / 14 / 18 / 40 ms | 1 |
| Select preset | 106 | 1 (0.94%) | 0 (0%) | 7 / 8 / 8 / 9 ms | 0 |
| Open synthetic history summary | 43 | 1 (2.33%) | 1 (2.33%) | 12 / 13 / 13 / 18 ms | 0 |
| Stop timer and show summary | 63 | 0 (0%) | 0 (0%) | 13 / 15 / 15 / 16 ms | 0 |

The backup export/restore sample contained only two app frames because file-picker work ran in system UI. It is not a meaningful app jank sample, and no UI export/restore latency is claimed. The manual UI round-trip itself succeeded.

### Memory and service

`dumpsys meminfo` values are KB. Heap entries are the PSS column; Graphics is the corresponding app-summary value reported by `dumpsys meminfo`.

| Sample | Total PSS | Dalvik heap PSS | Native heap PSS | Graphics |
|---|---:|---:|---:|---:|
| After launch | 61,016 | 3,132 | 8,834 | 34,616 |
| Active timer after 185.24 s | 148,043 | 9,034 | 20,585 | 98,700 |

The foreground service was active during the timer sample. The increase is an observation from one session, dominated by the Graphics app-summary value; there is no prior baseline or memory SLO to classify it as a regression.

### Calculator, storage, and backup

Each row has 10 measured samples. Calculator values are ns/op; storage/backup values are ms/op.

| Operation | Median | p90 | Min–max |
|---|---:|---:|---:|
| `percentAt` (240-input grid) | 161.66 ns | 214.10 ns | 153.27–367.95 ns |
| `minutesToReach` (240-input grid) | 185.05 ns | 220.47 ns | 162.50–237.96 ns |
| `predict` (240-input grid) | 171.85 ns | 192.19 ns | 149.42–208.75 ns |
| `estimateMilestone` (240-input grid) | 181.06 ns | 223.79 ns | 156.90–267.52 ns |

| Sessions | Operation | Median | p90 | Min–max |
|---:|---|---:|---:|---:|
| 100 | Restore/insert | 10.32 ms | 13.07 ms | 8.31–18.07 ms |
| 100 | History query | 1.96 ms | 2.42 ms | 1.40–10.55 ms |
| 100 | History calibration aggregate | 1.96 ms | 2.79 ms | 1.29–3.24 ms |
| 100 | Export | 1.04 ms | 1.76 ms | 0.80–2.19 ms |
| 100 | Encode | 0.90 ms | 1.82 ms | 0.70–2.10 ms |
| 100 | Decode | 2.41 ms | 3.72 ms | 1.30–5.78 ms |
| 100 | Round-trip restore | 10.86 ms | 11.79 ms | 8.75–12.52 ms |
| 1,000 | Restore/insert | 27.37 ms | 29.08 ms | 24.45–31.69 ms |
| 1,000 | History query | 4.66 ms | 7.69 ms | 3.86–8.45 ms |
| 1,000 | History calibration aggregate | 5.08 ms | 5.60 ms | 4.63–5.81 ms |
| 1,000 | Export | 4.72 ms | 5.63 ms | 3.67–6.57 ms |
| 1,000 | Encode | 3.12 ms | 4.20 ms | 2.29–4.34 ms |
| 1,000 | Decode | 7.03 ms | 7.92 ms | 5.59–8.80 ms |
| 1,000 | Round-trip restore | 27.20 ms | 32.13 ms | 26.83–33.23 ms |
| 10,000 | Restore/insert | 174.72 ms | 179.72 ms | 160.65–195.20 ms |
| 10,000 | History query | 22.29 ms | 24.20 ms | 20.72–24.25 ms |
| 10,000 | History calibration aggregate | 25.07 ms | 27.64 ms | 22.45–32.16 ms |
| 10,000 | Export | 22.61 ms | 26.14 ms | 20.68–27.73 ms |
| 10,000 | Encode | 18.33 ms | 21.73 ms | 17.10–23.35 ms |
| 10,000 | Decode | 49.48 ms | 51.91 ms | 45.30–55.28 ms |
| 10,000 | Round-trip restore | 188.48 ms | 206.84 ms | 178.04–211.26 ms |

These are shared-logic timings on a Windows JVM with in-memory SQLite, not Android device storage or end-to-end UI timings. They exclude physical disk behavior, Android database driver differences, and user-perceived file-picker latency.

## Findings and follow-ups

**Measured regressions:** None established. No prior comparable baseline or explicit performance SLO exists, so the measurements cannot show a regression against an expectation.

**Measured observations / candidates, not confirmed bottlenecks:**

- `dumpsys gfxinfo` reports 1 modern janky frame versus 1,169 legacy janky frames on the same Running-screen capture. This counter disagreement makes the jank rate inconclusive; it is not evidence that 97.09% of frames were visibly janky.
- The Running timer sample reports 98,700 KB in the Graphics app-summary field after about three minutes. A single sample without a historical baseline is not a memory regression.
- With 10,000 synthetic sessions, the JVM harness measured a 188.48 ms median round-trip restore and 49.48 ms median decode. This is the largest measured storage/backup cost, but it does not establish a user-facing slowdown on device.
- Startup proxies were 91.5 ms median cold and 25 ms median warm/hot `TotalTime` on this device and build. No independent visual frame timestamp was captured.

**Recommended follow-up issues (not created):**

1. **P1 — Resolve Running-screen frame and graphics-memory uncertainty.** Capture Android FrameTimeline/Perfetto traces and repeat the active sample to correlate frame deadlines, actual presentation, and graphics allocations.
2. **P2 — Establish performance SLOs and exercise persistent Android storage at scale.** Repeat history/backup measurements using the production Android storage driver, representative devices, and synthetic large histories; define acceptable UI-thread and restore budgets.
3. **P2 — Measure remaining platforms.** Run equivalent representative workflows on supported iOS hardware, desktop, and web/PWA. These were not measured in this pass to avoid using additional host resources and because the authorized device was Android-only; follow-up needs those platform environments and representative test data.

## Limitations

- One Pixel 6a and one Android release-like build were measured. Results do not represent other Android devices, OS versions, display refresh rates, or device load states.
- The Baseline Profile asset was packaged but its on-device compilation state was not verified, and there was no without-profile comparison.
- `am start -W` is a device-reported launch proxy, not a visual first-frame measurement. Frame metrics are single scripted samples per flow.
- Running-screen modern and legacy jank counters disagree substantially. A Perfetto/FrameTimeline capture is needed before drawing a jank conclusion.
- Android UI backup export/restore latency was not measured; the file picker dominates the app's two-frame `gfxinfo` sample. JVM format/logic timings are reported separately.
- iOS, desktop UI, and web/PWA were not measured in this pass. No production data, secrets, or third-party targets were used.
