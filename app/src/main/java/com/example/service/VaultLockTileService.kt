package com.example.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.data.VaultPreferences
import com.example.data.VaultRepository

/**
 * Quick Settings Tile to immediately lock VaultKeep from the notification shade.
 * This is strictly a lock-only action (security-positive, can never unlock).
 */
class VaultLockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = VaultPreferences(applicationContext)
        val repo = VaultRepository(applicationContext, prefs)
        repo.lockVault()

        updateTileState()
        Toast.makeText(applicationContext, "VaultKeep: Vault Locked", Toast.LENGTH_SHORT).show()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Lock Vault"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Force lock vault"
        }
        tile.updateTile()
    }
}
