package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ChargingNotificationTextTest {
    @Test
    fun progressIncludesCurrentAndTargetPercent() {
        val status = ChargingStatus(
            targetPercent = 80f,
            calculation = ChargingCalculation(timeRemainingMinutes = 95)
        )

        assertEquals(
            "45% → 80% • 11.0 kW • 1h 35m left • ETA 17:30",
            buildChargingNotificationText(status, 45, "11.0 kW", "17:30")
        )
    }

    @Test
    fun minutesOnlyWithoutEta() {
        val status = ChargingStatus(
            targetPercent = 90f,
            calculation = ChargingCalculation(timeRemainingMinutes = 5)
        )

        assertEquals(
            "89% → 90% • 7.0 kW • 5m left",
            buildChargingNotificationText(status, 89, "7.0 kW", null)
        )
    }

    @Test
    fun wholeHourKeepsZeroMinutes() {
        val status = ChargingStatus(
            targetPercent = 80f,
            calculation = ChargingCalculation(timeRemainingMinutes = 60)
        )

        assertEquals(
            "60% → 80% • 7.0 kW • 1h 0m left",
            buildChargingNotificationText(status, 60, "7.0 kW", null)
        )
    }

    @Test
    fun zeroRemainingReachesAnyTarget() {
        for (target in listOf(1, 50, 80, 100)) {
            val status = ChargingStatus(
                targetPercent = target.toFloat(),
                calculation = ChargingCalculation(timeRemainingMinutes = 0)
            )

            assertEquals(
                "Target reached · $target% • 7.0 kW",
                buildChargingNotificationText(status, target, "7.0 kW", null)
            )
        }
    }

    @Test
    fun completionUsesTargetAndPreservesFormattedEta() {
        val status = ChargingStatus(
            targetPercent = 80f,
            calculation = ChargingCalculation(timeRemainingMinutes = 0)
        )

        assertEquals(
            "Target reached · 80% • 7.0 kW • ETA 5:30 PM",
            buildChargingNotificationText(status, 79, "7.0 kW", "5:30 PM")
        )
    }

    @Test
    fun positiveRemainingDoesNotShowCompletionAtOneHundredPercent() {
        val status = ChargingStatus(
            targetPercent = 100f,
            calculation = ChargingCalculation(timeRemainingMinutes = 1)
        )

        assertEquals(
            "100% → 100% • 7.0 kW • 1m left",
            buildChargingNotificationText(status, 100, "7.0 kW", null)
        )
    }
}
