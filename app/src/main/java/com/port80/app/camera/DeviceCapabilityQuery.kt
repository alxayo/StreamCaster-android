package com.port80.app.camera

import com.port80.app.data.model.Resolution
import com.port80.app.data.model.VideoCodec

/**
 * Interface for querying what the device's camera and encoder can do.
 * This is read-only — it never opens the camera or starts encoding.
 * Used by settings screens to show only options the device supports.
 */
interface DeviceCapabilityQuery {
    /** Get list of camera IDs (e.g., "0" for back, "1" for front). */
    fun getCameraIds(): List<String>

    /** Get resolutions supported by the camera AND the given video encoder. */
    fun getSupportedResolutions(cameraId: String, codec: VideoCodec = VideoCodec.H264): List<Resolution>

    /** Get frame rates supported for the given resolution and codec. */
    fun getSupportedFps(cameraId: String, resolution: Resolution, codec: VideoCodec = VideoCodec.H264): List<Int>

    /** Check if the device has a front-facing camera. */
    fun hasFrontCamera(): Boolean

    /** Check if the device has a back-facing camera. */
    fun hasBackCamera(): Boolean

    /** Get the maximum video bitrate the encoder supports for the given codec, in kbps. */
    fun getMaxVideoBitrateKbps(codec: VideoCodec = VideoCodec.H264): Int

    /** Get list of video codecs that have hardware encoder support on this device. */
    fun getSupportedVideoCodecs(): List<VideoCodec>
}
