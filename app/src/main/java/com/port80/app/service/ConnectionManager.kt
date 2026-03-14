package com.port80.app.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import com.port80.app.data.model.StopReason
import com.port80.app.data.model.StreamState
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages RTMP connection lifecycle and automatic reconnection.
 *
 * When the network drops during a stream, this class:
 * 1. Detects the disconnection
 * 2. Waits with exponential backoff (3s, 6s, 12s... up to 60s)
 * 3. Retries connecting when network is available
 * 4. Gives up on auth failures (wrong stream key)
 *
 * Doze-aware: skips timer-based retries when the device is in Doze mode
 * (battery-saving deep sleep). Retries on ConnectivityManager.onAvailable() instead.
 */
class ConnectionManager(
    private val connectivityManager: ConnectivityManager,
    private val powerManager: PowerManager,
    private val reconnectPolicy: ReconnectPolicy,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val NETWORK_EVENT_DEBOUNCE_MS = 500L
    }

    // Callback invoked when we need to actually connect/disconnect
    var onConnect: (suspend () -> Boolean)? = null
    var onDisconnect: (suspend () -> Unit)? = null
    var onStateChange: ((StreamState) -> Unit)? = null

    private val mutex = Mutex()
    private var retryJob: Job? = null
    private var currentAttempt = 0
    @Volatile
    private var isStarted = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Start managing the connection. Call after initial connect succeeds.
     */
    fun start() {
        isStarted = true
        currentAttempt = 0
        registerNetworkCallback()
    }

    /**
     * Handle a connection loss. Starts the reconnect loop.
     */
    fun onConnectionLost() {
        if (!isStarted) return

        scope.launch {
            mutex.withLock {
                RedactingLogger.w(TAG, "Connection lost — starting reconnect")
                currentAttempt = 0
                scheduleRetry()
            }
        }
    }

    /**
     * Handle an auth failure. No retries — auth errors are permanent.
     */
    fun onAuthFailure() {
        scope.launch {
            mutex.withLock {
                RedactingLogger.e(TAG, "Auth failure — stopping retries")
                cancelRetry()
                onStateChange?.invoke(StreamState.Stopped(StopReason.ERROR_AUTH))
            }
        }
    }

    /**
     * Stop the connection manager. Cancels all retry attempts.
     * Call on explicit user stop.
     */
    fun stop() {
        isStarted = false
        cancelRetry()
        unregisterNetworkCallback()
        reconnectPolicy.reset()
        RedactingLogger.d(TAG, "ConnectionManager stopped")
    }

    private fun scheduleRetry() {
        if (!reconnectPolicy.shouldRetry(currentAttempt)) {
            RedactingLogger.w(TAG, "Max retry attempts reached")
            onStateChange?.invoke(StreamState.Stopped(StopReason.ERROR_ENCODER))
            return
        }

        val delayMs = reconnectPolicy.nextDelayMs(currentAttempt)
        onStateChange?.invoke(StreamState.Reconnecting(currentAttempt, delayMs))

        retryJob?.cancel()
        retryJob = scope.launch {
            // Check if device is in Doze mode — skip timer retries if so
            if (powerManager.isDeviceIdleMode) {
                RedactingLogger.d(TAG, "Device in Doze — waiting for network callback instead of timer")
                return@launch
            }

            delay(delayMs)
            attemptReconnect()
        }
    }

    private suspend fun attemptReconnect() {
        mutex.withLock {
            if (!isStarted) return

            RedactingLogger.i(TAG, "Reconnect attempt ${currentAttempt + 1}")
            val success = onConnect?.invoke() ?: false

            if (success) {
                RedactingLogger.i(TAG, "Reconnected successfully!")
                currentAttempt = 0
                reconnectPolicy.reset()
                onStateChange?.invoke(StreamState.Live())
            } else {
                currentAttempt++
                scheduleRetry()
            }
        }
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                private var lastEventTime = 0L

                override fun onAvailable(network: Network) {
                    // Debounce rapid network events (e.g., WiFi→cellular handoff)
                    val now = System.currentTimeMillis()
                    if (now - lastEventTime < NETWORK_EVENT_DEBOUNCE_MS) return
                    lastEventTime = now

                    if (isStarted) {
                        RedactingLogger.d(TAG, "Network available — attempting immediate reconnect")
                        scope.launch { attemptReconnect() }
                    }
                }

                override fun onLost(network: Network) {
                    val now = System.currentTimeMillis()
                    if (now - lastEventTime < NETWORK_EVENT_DEBOUNCE_MS) return
                    lastEventTime = now

                    if (isStarted) {
                        onConnectionLost()
                    }
                }
            }

            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
        networkCallback = null
    }
}
