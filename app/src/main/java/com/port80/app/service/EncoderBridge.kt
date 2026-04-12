package com.port80.app.service

import com.pedro.library.view.OpenGlView
import com.port80.app.data.model.StreamProtocol

/**
 * Abstraction layer over RootEncoder's camera-encoder classes.
 * This interface lets us test the service without a real camera/encoder
 * and allows protocol-specific implementations (RTMP vs SRT).
 */
interface EncoderBridge {
    /** Start showing camera preview on the given OpenGlView surface. */
    fun startPreview(openGlView: OpenGlView)

    /** Stop the camera preview (streaming continues without display). */
    fun stopPreview()

    /** Configure encoders and connect to the streaming server. */
    fun connect(params: ConnectionParams, config: EncoderConfig)

    /** Disconnect from the streaming server. */
    fun disconnect()

    /** Switch between front and back camera. */
    fun switchCamera()

    /** Change video bitrate on the fly without restarting the encoder. */
    fun setVideoBitrateOnFly(bitrateKbps: Int)

    /** Release all encoder and camera resources. Call this on service destroy. */
    fun release()

    /** Check if we're currently streaming. */
    fun isStreaming(): Boolean

    /** Factory for creating the correct EncoderBridge per protocol. */
    fun interface Factory {
        fun create(
            connectChecker: com.pedro.common.ConnectChecker,
            protocol: StreamProtocol
        ): EncoderBridge
    }
}
