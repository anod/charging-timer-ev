# Code Highlights

## Key Features Implementation

### 1. Charging Calculations
Realistic EV charging physics with time-based progress:

```kotlin
fun calculateChargingTime(
    batteryCapacity: Float,
    startPercent: Float,
    maxPercent: Float,
    chargingPower: Float
): Int {
    val percentToCharge = maxPercent - startPercent
    val energyNeeded = (batteryCapacity * percentToCharge) / 100f
    val hours = energyNeeded / chargingPower
    return (hours * 60).roundToInt() // Convert to minutes
}

fun calculateCurrentPercent(
    startTime: Long,
    startPercent: Float,
    maxPercent: Float,
    totalMinutes: Int
): Float {
    val now = Clock.System.now().toEpochMilliseconds()
    val elapsed = ((now - startTime) / 1000 / 60).toInt()
    
    if (elapsed <= 0) return startPercent
    if (elapsed >= totalMinutes) return maxPercent
    
    val percentPerMinute = (maxPercent - startPercent) / totalMinutes
    return startPercent + (percentPerMinute * elapsed)
}
```

### 2. Wobble Animation
Smooth, continuous animation combining rotation and scale:

```kotlin
@Composable
fun WobbleChargingIndicator(
    currentPercent: Float,
    maxPercent: Float
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Wobble rotation: -5° to +5°
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Pulsing scale: 0.95x to 1.05x
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Surface(
        modifier = Modifier
            .size(200.dp)
            .rotate(rotation),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        // Display current percentage
        Text(
            text = "${currentPercent.roundToInt()}%",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
```

### 3. Power Selection Chips
Material 3 FilterChips with custom power dialog:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingPowerChips(
    powers: List<Float>,
    selectedPower: Float,
    onPowerSelected: (Float) -> Unit,
    onAddCustom: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        powers.forEach { power ->
            FilterChip(
                selected = power == selectedPower,
                onClick = { onPowerSelected(power) },
                label = { Text("${power} kW") }
            )
        }
        
        FilterChip(
            selected = false,
            onClick = onAddCustom,
            label = { Text("+") }
        )
    }
}
```

### 4. State Management
Clean ViewModel with reactive state:

```kotlin
class ChargingViewModel(
    private val repository: ChargingRepository
) {
    var settings by mutableStateOf(ChargingSettings())
        private set
    
    var state by mutableStateOf(ChargingState())
        private set
    
    var calculation by mutableStateOf(
        ChargingCalculator.getChargingCalculation(settings, state)
    )
        private set
    
    fun startCharging() {
        val now = Clock.System.now().toEpochMilliseconds()
        val totalMinutes = ChargingCalculator.calculateChargingTime(
            settings.batteryCapacity,
            settings.startPercent,
            settings.maxPercent,
            settings.chargingPower
        )
        state = ChargingState(
            isRunning = true,
            startTime = now,
            currentPercent = settings.startPercent,
            estimatedEndTime = ChargingCalculator.estimateEndTime(now, totalMinutes)
        )
        updateCalculation()
    }
    
    fun stopCharging() {
        state = ChargingState(isRunning = false)
        updateCalculation()
    }
}
```

### 5. Real-Time Updates
LaunchedEffect for periodic calculation updates:

```kotlin
@Composable
fun RunningTimerScreen(
    viewModel: ChargingViewModel,
    onStopCharging: () -> Unit
) {
    // Update every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            viewModel.updateCalculation()
        }
    }
    
    // UI renders current state
    val calculation = viewModel.calculation
    Text("Time Remaining: ${formatTime(calculation.timeRemainingMinutes)}")
}
```

### 6. Material 3 Theming
Adaptive color schemes for light and dark modes:

```kotlin
private val LightColors = lightColorScheme(
    primary = Color(0xFF006D3B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8FF7B6),
    onPrimaryContainer = Color(0xFF00210F),
    // ... more colors
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF73DA9C),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF00522B),
    onPrimaryContainer = Color(0xFF8FF7B6),
    // ... more colors
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
```

### 7. Persistence
Platform-specific repository implementation:

```kotlin
// Desktop implementation
actual class ChargingRepositoryImpl : ChargingRepository {
    private val settingsFile = File(
        System.getProperty("user.home"), 
        ".charging-timer-settings.properties"
    )
    
    override fun saveSettings(settings: ChargingSettings) {
        scope.launch {
            val props = Properties()
            props.setProperty("battery_capacity", settings.batteryCapacity.toString())
            props.setProperty("charging_power", settings.chargingPower.toString())
            props.setProperty("start_percent", settings.startPercent.toString())
            props.setProperty("max_percent", settings.maxPercent.toString())
            
            settingsFile.outputStream().use {
                props.store(it, "Charging Timer Settings")
            }
        }
    }
    
    override fun loadSettings(onLoaded: (ChargingSettings) -> Unit) {
        // Load from properties file
    }
}
```

## Project Statistics

- **Total Files**: 12 Kotlin source files
- **Lines of Code**: ~1,300
- **Modules**: 2 (shared, desktopApp)
- **Screens**: 2 (Configuration, Running Timer)
- **Animations**: 2 (rotation wobble, scale pulse)
- **Persistence**: 5 settings persisted
- **Build Time**: ~15 seconds (clean build)

## Technology Choices

### Why Kotlin Multiplatform?
- Share business logic across platforms
- Type-safe, null-safe language
- Excellent IDE support
- Growing ecosystem

### Why Compose?
- Declarative UI
- Less boilerplate than XML/views
- Hot reload for faster development
- Material 3 out of the box
- Powerful animation APIs

### Why Material 3?
- Modern design language
- Adaptive theming
- Accessibility built-in
- Rich component library
- Dynamic color support

## Build Configuration

```kotlin
// Root build.gradle.kts
plugins {
    kotlin("multiplatform") version "1.9.22" apply false
    kotlin("jvm") version "1.9.22" apply false
    id("org.jetbrains.compose") version "1.5.12" apply false
}

// shared/build.gradle.kts
kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
    }
}
```

## Future Enhancements

### Android Module (when Google Maven is accessible)
```kotlin
// Android-specific features
class ChargingNotificationService : Service() {
    fun showChargingNotification(percent: Int, timeRemaining: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EV Charging")
            .setContentText("$percent% • $timeRemaining remaining")
            .setSmallIcon(R.drawable.ic_charging)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
}
```

### iOS Support
```kotlin
// iOS-specific repository
actual class ChargingRepositoryImpl : ChargingRepository {
    override fun saveSettings(settings: ChargingSettings) {
        NSUserDefaults.standardUserDefaults.apply {
            setFloat(settings.batteryCapacity, "battery_capacity")
            setFloat(settings.chargingPower, "charging_power")
            // ... more settings
        }
    }
}
```

### Charging History
```kotlin
data class ChargingSession(
    val startTime: Long,
    val endTime: Long,
    val energyAdded: Float,
    val percentageGained: Float
)

fun saveSession(session: ChargingSession) {
    // Store in database or file
}
```
