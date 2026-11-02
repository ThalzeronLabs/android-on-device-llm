package com.thalzeronlabs.ondevicellm.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** What the battery saver needs to know. */
data class BatteryStatus(
    /** 0..100, or -1 when the phone does not say. */
    val percent: Int,
    val charging: Boolean,
    /** Android's own Battery Saver is on. */
    val powerSaveMode: Boolean = false,
) {
    /** Low enough that a long answer (which keeps every CPU core busy) is worth a warning. */
    val isLow: Boolean get() = powerSaveMode || (!charging && percent in 0..LOW_PERCENT)

    companion object {
        const val LOW_PERCENT = 20

        /** Reads an `ACTION_BATTERY_CHANGED` intent; null when there is nothing to read. */
        fun from(intent: Intent?, powerSaveMode: Boolean): BatteryStatus? {
            if (intent == null) return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryStatus(percent, charging, powerSaveMode)
        }
    }
}

/**
 * The battery state, updated as it changes. Uses the sticky `ACTION_BATTERY_CHANGED` broadcast (no permission
 * needed) and Android's Battery Saver switch.
 */
fun batteryStatusFlow(context: Context): Flow<BatteryStatus> = callbackFlow {
    val app = context.applicationContext
    val power = app.getSystemService(PowerManager::class.java)
    var lastBattery: Intent? = null
    fun publish() {
        BatteryStatus.from(lastBattery, power?.isPowerSaveMode == true)?.let { trySend(it) }
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) lastBattery = intent
            publish()
        }
    }
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
        addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
    }
    lastBattery = ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    publish()
    awaitClose { app.unregisterReceiver(receiver) }
}.distinctUntilChanged()
