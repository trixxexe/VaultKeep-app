package com.example.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event notifier for immediate vault locking events (e.g. from Quick Settings Tile or Shortcuts).
 */
object VaultLockNotifier {
    private val _lockEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val lockEvents: SharedFlow<Unit> = _lockEvents.asSharedFlow()

    fun notifyLocked() {
        _lockEvents.tryEmit(Unit)
    }
}
