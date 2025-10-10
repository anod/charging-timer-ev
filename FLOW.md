# Application Flow

## Screen Flow Diagram

```
┌─────────────────────────────┐
│  Configuration Screen       │
├─────────────────────────────┤
│                             │
│  EV Charging Timer          │
│                             │
│  ┌──────────────────────┐   │
│  │ Battery: 60 kWh      │   │
│  │ ──────────o──────    │   │
│  └──────────────────────┘   │
│                             │
│  ┌──────────────────────┐   │
│  │ Power: 7 kW          │   │
│  │ [3.6] [7] [11] [22] [+]│   │
│  └──────────────────────┘   │
│                             │
│  ┌──────────────────────┐   │
│  │ Start %: 20%         │   │
│  │ ─o───────────────    │   │
│  └──────────────────────┘   │
│                             │
│  ┌──────────────────────┐   │
│  │ Max %: 80%           │   │
│  │ ────────────────o─   │   │
│  └──────────────────────┘   │
│                             │
│  [   Start Charging   ]     │
│                             │
└─────────────────────────────┘
              │
              │ (on start)
              ▼
┌─────────────────────────────┐
│  Running Timer Screen       │
├─────────────────────────────┤
│                             │
│  Charging in Progress       │
│                             │
│      ╭───────────╮          │
│     ╱   45%      ╲         │
│    │  of 80%      │        │
│     ╲            ╱         │
│      ╰───────────╯          │
│   (wobble animation)        │
│                             │
│  ┌──────────────────────┐   │
│  │ Speed:      7 kW     │   │
│  │ Current:    45%      │   │
│  │ Target:     80%      │   │
│  │ Time Left:  2h 15m   │   │
│  └──────────────────────┘   │
│                             │
│  [   Stop Charging   ]      │
│                             │
└─────────────────────────────┘
              │
              │ (on stop)
              ▼
      (back to config)
```

## User Journey

### 1. Initial Setup
1. User opens the app
2. Configuration screen loads with saved settings (or defaults)
3. User adjusts:
   - Battery capacity slider
   - Selects or adds charging power
   - Sets start percentage
   - Sets target (max) percentage

### 2. Starting Charge
1. User clicks "Start Charging"
2. System captures current time
3. Calculates total charging duration
4. Transitions to Running Timer Screen
5. Starts real-time updates (every 1 second)

### 3. Charging Progress
1. Wobble animation continuously runs
2. Current percentage updates based on elapsed time
3. Time remaining decreases
4. When complete (time = 0), shows "Complete"

### 4. Stopping Charge
1. User clicks "Stop Charging"
2. System resets charging state
3. Returns to Configuration screen
4. All settings are preserved

## Data Flow

```
┌──────────────┐
│ User Input   │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ ViewModel    │◄─────────┐
│ (State Mgmt) │          │
└──────┬───────┘          │
       │                  │
       ├─────────────►┌───┴────────┐
       │              │ Repository │
       │              │ (Persist)  │
       │              └────────────┘
       │
       ▼
┌──────────────┐
│ Calculator   │
│ (Logic)      │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ UI Update    │
│ (Recompose)  │
└──────────────┘
```

## Key Interactions

### Adding Custom Power
1. Click '+' chip
2. Dialog appears
3. Enter power value (kW)
4. Click "Add"
5. New chip appears
6. Value saved to persistence

### Wobble Animation
- Continuous rotation: -5° ↔ +5° (1 second cycle)
- Continuous scale: 0.95x ↔ 1.05x (1.5 second cycle)
- Both animations run simultaneously
- Uses Material motion easing curves

### Calculation Updates
- Timer: Updates every 1000ms
- Formula: `current% = start% + (elapsed / total) * (max% - start%)`
- Time remaining: `total - elapsed` (clamped at 0)

## Persistence Behavior

### On App Start
1. Load settings from storage
2. Apply to UI
3. Display configuration screen

### On Setting Change
1. Update ViewModel state
2. Trigger UI recompose
3. Save to repository (async)
4. Recalculate if needed

### On App Close
- Settings already saved
- No data loss
- Next launch restores state
