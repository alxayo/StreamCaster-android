package com.port80.app.service

import android.content.Context
import com.pedro.library.view.OpenGlView
import com.port80.app.data.model.StabilizationMode
import com.port80.app.data.model.StreamProtocol

/**
 * Abstraction layer over RootEncoder's camera-encoder classes.
 * This interface lets us test the service without a real camera/encoder
 * and allows protocol-specific implementations (RTMP vs SRT).
 */
interface EncoderBridge {
    /** Start showing camera preview on the given OpenGlView surface. */
    fun startPreview(openGlView: OpenGlView)

    /**
     * Start showing camera preview using a specific camera by Camera2 camera ID.
     * Falls back to the default camera if [cameraId] is invalid.
     */
    fun startPreview(openGlView: OpenGlView, cameraId: String)

    /**
     * Start showing camera preview with explicit surface dimensions.
     * This ensures the preview renders at the correct resolution for the
     * current display orientation, avoiding black bars on rotation.
     */
    fun startPreview(openGlView: OpenGlView, cameraId: String, width: Int, height: Int)

    /** Stop the camera preview (streaming continues without display). */
    fun stopPreview()

    /**
     * Switch to headless background mode while keeping camera capture and
     * encoder alive. Uses RootEncoder's [Camera2Base.replaceView(Context)]
     * which swaps the GL surface for an off-screen GlStreamInterface.
     */
    fun replaceViewWithBackground(context: Context)

    /**
     * Hot-swap back to a visible preview surface during an active stream.
     * Uses RootEncoder's [Camera2Base.replaceView(OpenGlView)] which
     * re-opens the camera on the new surface without interrupting the stream.
     */
    fun replaceView(openGlView: OpenGlView)

    /** Configure encoders and connect to the streaming server. */
    fun connect(params: ConnectionParams, config: EncoderConfig)

    /** Disconnect from the streaming server. */
    fun disconnect()

    /** Switch between front and back camera. */
    fun switchCamera()

    /**
     * Switch to a specific camera by Camera2 camera ID.
     * Returns silently if the ID is invalid or the camera cannot be opened.
     */
    fun switchCamera(cameraId: String)

    /** Change video bitrate on the fly without restarting the encoder. */
    fun setVideoBitrateOnFly(bitrateKbps: Int)

    /**
     * Set the image stabilization mode.
     * Only one mode (EIS or OIS) should be active at a time.
     * Safe to call during preview or streaming.
     */
    fun setStabilizationMode(mode: StabilizationMode)

    /** Release all encoder and camera resources. Call this on service destroy. */
    fun release()

    /** Check if we're currently streaming. */
    fun isStreaming(): Boolean

    /**
     * Register a callback that receives the measured FPS once per second.
     * Must be called after the camera/encoder is created (i.e., after startPreview).
     */
    fun setFpsListener(callback: (Int) -> Unit)

    /** Factory for creating the correct EncoderBridge per protocol. */
    fun interface Factory {
        fun create(
            connectChecker: com.pedro.common.ConnectChecker,
            protocol: StreamProtocol
        ): EncoderBridge
    }
}
