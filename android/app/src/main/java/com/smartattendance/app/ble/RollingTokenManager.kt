package com.smartattendance.app.ble

import java.nio.ByteBuffer

class RollingTokenManager(
    private val rotationIntervalMs: Long = BleConstants.ROLLING_TOKEN_INTERVAL_MS,
) {
    fun currentWindow(nowMillis: Long = System.currentTimeMillis()): Long {
        return nowMillis / rotationIntervalMs
    }

    fun currentToken(nowMillis: Long = System.currentTimeMillis()): ByteArray {
        return ByteBuffer.allocate(BleConstants.ROLLING_TOKEN_BYTES)
            .putLong(currentWindow(nowMillis))
            .array()
    }

    fun hasWindowChanged(
        previousWindow: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        return currentWindow(nowMillis) != previousWindow
    }

    fun nextRotationDelay(nowMillis: Long = System.currentTimeMillis()): Long {
        val elapsedInWindow = nowMillis % rotationIntervalMs
        val remaining = rotationIntervalMs - elapsedInWindow
        return if (remaining <= 0) rotationIntervalMs else remaining
    }
}
