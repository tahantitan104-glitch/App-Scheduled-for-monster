package com.example.session

interface SessionPlatformBridge {
    fun startMonitorService()
    fun stopMonitorService()
    fun launchLockScreen()
    fun notifyLockStateChanged()
    fun isForegroundServiceBlocked(): Boolean = false
    fun scheduleRecoveryJobIfNeeded() {}
    fun cancelRecoveryJob() {}
}
