package com.example.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.MainActivity
import com.example.ResolveApplication
import com.example.domain.model.SessionState

/**
 * Quick Settings Tile (Android 7.0+ / API 24+).
 * Ignored safely on Android 5.1.1–6.0 where OS tile binding does not exist.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ResolveFocusTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        val container = (applicationContext as? ResolveApplication)?.container
        val active = container?.sessionEngine?.activeSession?.value
        val isActive = active != null && !active.isTerminal

        val intent = Intent(this, MainActivity::class.java).apply {
            if (!isActive) {
                action = MainActivity.ACTION_QUICK_LOCK_15M
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTileState() {
        val tile = qsTile ?: return
        val container = (applicationContext as? ResolveApplication)?.container
        val session = container?.sessionEngine?.activeSession?.value
        val isActive = session != null && !session.isTerminal

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isActive) "Resolve Locked" else "Resolve 15m"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (session?.state) {
                SessionState.LOCK_ENFORCEMENT_ACTIVE,
                SessionState.PHONE_CALL_ACTIVE -> "Focus Lock Active"
                SessionState.DELAYED_FREE_USE_ACTIVE -> "Free Use Countdown"
                else -> "Tap for 15m Focus"
            }
        }
        tile.updateTile()
    }
}
