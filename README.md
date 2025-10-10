# EV Charging Timer

A Kotlin Multiplatform application for managing EV charging sessions with a beautiful Compose UI.

## Features

- **Battery Capacity Configuration**: Set your battery capacity in kWh (20-120 kWh range)
- **Charging Power Selection**: Choose from predefined charging powers (3.6, 7, 11, 22 kW) or add custom values
- **Charging Range**: Set start and max battery percentages with intuitive sliders
- **Real-time Charging Simulation**: 
  - Live charging progress with wobble animation
  - Estimated completion time
  - Current battery percentage
  - Charging speed display
- **Persistent Settings**: Your configuration is saved and restored between sessions
- **Material 3 Design**: Adaptive theming with light and dark mode support

## Project Structure

```
charging-timer-ev/
├── shared/           # Shared Kotlin code
│   ├── data/        # Data models, calculations, and persistence
│   └── ui/          # Compose UI components
└── desktopApp/      # Desktop application
```

## Building and Running

### Desktop App

```bash
./gradlew :desktopApp:run
```

### Build All

```bash
./gradlew build
```

## Technical Details

- **Kotlin Version**: 1.9.22
- **Compose Version**: 1.5.12
- **Gradle Version**: 8.5
- **Target JVM**: 17

## Screens

### Configuration Screen
The configuration screen allows you to set up your charging parameters:
- Battery capacity slider (20-120 kWh)
- Charging power chips (3.6, 7, 11, 22 kW) with a '+' button to add custom power values
- Start % slider (0-100%)
- Max % slider (0-100%)
- Start charging button (enabled only when start % < max %)

### Running Timer Screen
Once charging starts, you'll see:
- **Animated Wobble Indicator**: A circular indicator showing the current charge percentage with a wobbling rotation and pulsing scale animation
- **Charging Statistics Card** displaying:
  - Charging speed (kW)
  - Current percentage (updates every second)
  - Target percentage  
  - Time remaining (formatted as hours and minutes)
- **Stop Charging** button to end the session

## How It Works

### Charging Calculations
The app uses realistic EV charging calculations:
- **Energy Required** = (Battery Capacity × Percentage to Charge) / 100
- **Time Required** = Energy Required / Charging Power
- **Current Percentage** = Start % + (Progress × Percentage Range)

### Persistence
Settings are persisted using:
- Desktop: Properties file in user home directory (`.charging-timer-settings.properties`)

The following settings are saved:
- Battery capacity
- Selected charging power
- Available power options
- Start and max percentages

## Architecture

The app follows clean architecture principles:

1. **Data Layer** (`shared/data`):
   - `Models.kt`: Data classes for settings, state, and calculations
   - `ChargingCalculator.kt`: Business logic for charging calculations
   - `ChargingRepository.kt`: Interface for persistence
   - `ChargingRepository.desktop.kt`: Desktop-specific implementation
   - `ChargingViewModel.kt`: State management and user actions

2. **UI Layer** (`shared/ui`):
   - `Theme.kt`: Material 3 color schemes for light and dark themes
   - `ConfigurationScreen.kt`: Initial setup screen
   - `RunningTimerScreen.kt`: Active charging screen with animations
   - `ChargingTimerApp.kt`: Main app composable

3. **Platform Layer** (`desktopApp`):
   - `Main.kt`: Desktop application entry point

## Future Enhancements

Potential areas for expansion:
- Android app with notification support (requires access to Google Maven repository)
- iOS support
- Charging history and statistics
- Multiple charging profiles
- Battery health tracking
