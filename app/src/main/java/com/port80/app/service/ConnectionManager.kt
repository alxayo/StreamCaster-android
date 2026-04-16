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
 * Manages automatic reconnection after a network drop during a live stream.
 *
 * When the network drops during a stream, this class:
 * 1. Detects the disconnection
 * 2. Waits with exponential backoff (3s, 6s, 12s... up to 60s)
 * 3. Asks the service to reconnect when timer fires or network returns
 * 4. Gives up on auth failures (wrong stream key)
 *
 * Doze-aware: skips timer-based retries when the device is in Doze mode
 * (battery-saving deep sleep). Retries on ConnectivityManager.onAvailable() instead.
 *
 * This class owns scheduling only — the service owns execution and state transitions.
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

    /**
     * Fired when the ConnectionManager wants the service to attempt a reconnect.
     * The service should call [notifyReconnectResult] when the attempt resolves.
     */
    var requestReconnect: (() -> Unit)? = null

    /**
     * Fired when all retry attempts are exhausted.
     * The service should call [terminateService] with the provided reason.
     */
    var onReconnectExhausted: ((StopReason) -> Unit)? = null

    /**
     * Fired when the reconnect state changes (Reconnecting with attempt/delay info).
     * The service sets [_streamState] from this.
     */
    var onStateChanged: ((StreamState) -> Unit)? = null

    private val mutex = Mutex()
    private var retryJob: Job? = null
    private var currentAttempt = 0
    @Volatile
    var isStarted = false
        private set
    @Volatile
    private var attemptInFlight = false
    var isReconnecting: Boolean = false
        private set
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Start monitoring the connection. Call after initial connect succeeds.
     * Registers a network callback to detect connectivity changes.
     */
    fun start() {
        isStarted = true
        isReconnecting = false
        currentAttempt = 0
        attemptInFlight = false
        registerNetworkCallback()
    }

    /**
     * Handle a connection loss. Starts the reconnect loop.
     */
    fun onConnectionLost() {
        if (!isStarted) return

        scope.launch {
            mutex.withLock {
                if (isReconnecting && retryJob?.isActive == true) {
                    RedactingLogger.d(TAG, "Reconnect already scheduled; ignoring duplicate loss signal")
                    return@withLock
                }
                RedactingLogger.w(TAG, "Connection lost — starting reconnect")
                isReconnecting = true
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
                isReconnecting = false
                // Don't emit terminal state — the service owns Stopped transitions
            }
        }
    }

    /**
     * Called by the service after a reconnect attempt resolves.
     * @param success true if the connection was re-established
     */
    fun notifyReconnectResult(success: Boolean) {
        scope.launch {
            mutex.withLock {
                attemptInFlight = false
                if (!isStarted || !isReconnecting) return@withLock

                if (success) {
                    RedactingLogger.i(TAG, "Reconnected successfully!")
                    currentAttempt = 0
                    isReconnecting = false
                    reconnectPolicy.reset()
                    // Don't emit Live — the service's onConnectionSuccess handles that
                } else {
                    currentAttempt++
                    scheduleRetry()
                }
            }
        }
    }

    /**
     * Stop the connection manager. Cancels all retry attempts.
     * Call on explicit user stop or service termination.
     */
    fun stop() {
        isStarted = false
        isReconnecting = false
        attemptInFlight = false
        cancelRetry()
        unregisterNetworkCallback()
        reconnectPolicy.reset()
        RedactingLogger.d(TAG, "ConnectionManager stopped")
    }

    private fun scheduleRetry() {
        if (!reconnectPolicy.shouldRetry(currentAttempt)) {
            RedactingLogger.w(TAG, "Max retry attempts reached ($currentAttempt)")
            isReconnecting = false
            onReconnectExhausted?.invoke(StopReason.ERROR_ENCODER)
            return
        }

        val delayMs = reconnectPolicy.nextDelayMs(currentAttempt)
        onStateChanged?.invoke(StreamState.Reconnecting(currentAttempt, delayMs, reconnectPolicy.maxAttempts))

        retryJob?.cancel()
        retryJob = scope.launch {
            // Check if device is in Doze mode — skip timer retries if so
            if (powerManager.isDeviceIdleMode) {
                RedactingLogger.d(TAG, "Device in Doze — waiting for network callback instead of timer")
                return@launch
            }

            delay(delayMs)
            fireReconnectRequest()
        }
    }

    private suspend fun fireReconnectRequest() {
        mutex.withLock {
            if (!isStarted || !isReconnecting) return
            if (attemptInFlight) {
                RedactingLogger.d(TAG, "Reconnect attempt already in flight — skipping")
                return
            }

            RedactingLogger.i(TAG, "Requesting reconnect attempt ${currentAttempt + 1}")
            attemptInFlight = true
        }
        // Fire outside the lock — the service may synchronously call notifyReconnectResult
        requestReconnect?.invoke()
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

                    // Only trigger reconnect when actively reconnecting, not while Live
                    if (isStarted && isReconnecting) {
                        RedactingLogger.d(TAG, "Network available — attempting immediate reconnect")
                        scope.launch { fireReconnectRequest() }
                    }
                }

                override fun onLost(network: Network) {
                    val now = System.currentTimeMillis()
                    if (now - lastEventTime < NETWORK_EVENT_DEBOUNCE_MS) return
                    lastEventTime = now

                    if (isStarted && !isReconnecting) {
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
