package com.thalzeronlabs.ondevicellm.battery

import android.content.Intent
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryStatusTest {

    private fun battery(level: Int, scale: Int = 100, status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING) =
        Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, level)
            putExtra(BatteryManager.EXTRA_SCALE, scale)
            putExtra(BatteryManager.EXTRA_STATUS, status)
        }

    @Test
    fun `reads the level as a percentage`() {
        assertEquals(BatteryStatus(15, charging = false), BatteryStatus.from(battery(30, scale = 200), powerSaveMode = false))
    }

    @Test
    fun `low means 20 percent or less and not charging`() {
        assertTrue(BatteryStatus.from(battery(20), false)!!.isLow)
        assertFalse(BatteryStatus.from(battery(21), false)!!.isLow)
        assertFalse(BatteryStatus.from(battery(5, status = BatteryManager.BATTERY_STATUS_CHARGING), false)!!.isLow)
        assertFalse(BatteryStatus.from(battery(5, status = BatteryManager.BATTERY_STATUS_FULL), false)!!.charging.not())
    }

    @Test
    fun `Android's Battery Saver counts as low at any level`() {
        assertTrue(BatteryStatus.from(battery(90), powerSaveMode = true)!!.isLow)
    }

    @Test
    fun `an unknown level is never low by itself`() {
        val unknown = BatteryStatus.from(Intent(Intent.ACTION_BATTERY_CHANGED), false)!!
        assertEquals(-1, unknown.percent)
        assertFalse(unknown.isLow)
        assertNull(BatteryStatus.from(null, false))
    }
}
