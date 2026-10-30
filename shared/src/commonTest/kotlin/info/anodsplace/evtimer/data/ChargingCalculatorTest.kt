package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ChargingCalculatorTest {

    @Test
    fun calculateKwh_typicalValues() {
        // 60 kWh battery at 50% = 30 kWh
        assertEquals(30, ChargingCalculator.calculateKwh(60f, 50f))
        
        // 75 kWh battery at 80% = 60 kWh
        assertEquals(60, ChargingCalculator.calculateKwh(75f, 80f))
        
        // 100 kWh battery at 25% = 25 kWh
        assertEquals(25, ChargingCalculator.calculateKwh(100f, 25f))
    }

    @Test
    fun calculateKwh_boundaryValues() {
        // 0% should return 0
        assertEquals(0, ChargingCalculator.calculateKwh(60f, 0f))
        
        // 100% should return full capacity
        assertEquals(60, ChargingCalculator.calculateKwh(60f, 100f))
        assertEquals(75, ChargingCalculator.calculateKwh(75f, 100f))
    }

    @Test
    fun calculateKwh_smallBatteryCapacity() {
        // Small battery: 20 kWh at 50% = 10 kWh
        assertEquals(10, ChargingCalculator.calculateKwh(20f, 50f))
        
        // Very small battery: 10 kWh at 30% = 3 kWh
        assertEquals(3, ChargingCalculator.calculateKwh(10f, 30f))
    }

    @Test
    fun calculateKwh_largeBatteryCapacity() {
        // Large battery: 120 kWh at 80% = 96 kWh
        assertEquals(96, ChargingCalculator.calculateKwh(120f, 80f))
        
        // Very large battery: 200 kWh at 50% = 100 kWh
        assertEquals(100, ChargingCalculator.calculateKwh(200f, 50f))
    }

    @Test
    fun calculateKwh_rounding() {
        // Test rounding: 60 kWh at 33% = 19.8 -> rounds to 20
        assertEquals(20, ChargingCalculator.calculateKwh(60f, 33f))
        
        // 60 kWh at 32% = 19.2 -> rounds to 19
        assertEquals(19, ChargingCalculator.calculateKwh(60f, 32f))
        
        // 75 kWh at 33% = 24.75 -> rounds to 25
        assertEquals(25, ChargingCalculator.calculateKwh(75f, 33f))
    }

    @Test
    fun calculateKwh_decimalPercentages() {
        // 60 kWh at 50.5% = 30.3 -> rounds to 30
        assertEquals(30, ChargingCalculator.calculateKwh(60f, 50.5f))
        
        // 60 kWh at 50.9% = 30.54 -> rounds to 31
        assertEquals(31, ChargingCalculator.calculateKwh(60f, 50.9f))
    }
}
