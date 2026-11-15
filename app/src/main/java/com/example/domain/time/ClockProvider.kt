package com.example.domain.time

import android.os.SystemClock
import java.time.ZoneId

interface ClockProvider {
    fun wallClockMs(): Long
    fun elapsedRealtimeMs(): Long
    fun zoneId(): ZoneId
}

class SystemClockProvider : ClockProvider {
    override fun wallClockMs(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}
