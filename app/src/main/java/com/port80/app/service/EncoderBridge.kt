package com.port80.app.service

import com.pedro.library.view.OpenGlView
import com.port80.app.data.model.Resolution

/**
 * Abstraction layer over RootEncoder's RtmpCamera2.
 * This interface lets us test the service without a real camera/encoder.
 *
 * T-007a provides a StubEncoderBridge (for testing).
 * T-007b provides RtmpCamera2EncoderBridge (the real implementation).
 */
interface EncoderBridge {
    /** Start showing camera preview on the given OpenGlView surface. */
    fun startPreview(openGlView: OpenGlView)

    /** Stop the camera preview (streaming continues without display). */
    fun stopPreview()

    /** Connect to the RTMP server and start streaming. */
    fun connect(url: String, streamKey: String)

    /** Disconnect from the RTMP server. */
    fun disconnect()

    /** Switch between front and back camera. */
    fun switchCamera()

    /** Change video bitrate on the fly without restarting the encoder. */
    fun setVideoBitrateOnFly(bitrateKbps: Int)

    /** Release all encoder and camera resources. Call this on service destroy. */
    fun release()

    /** Check if we're currently streaming to the RTMP server. */
    fun isStreaming(): Boolean
}
