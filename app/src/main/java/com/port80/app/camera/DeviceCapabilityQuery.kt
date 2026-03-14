package com.port80.app.camera

import com.port80.app.data.model.Resolution

/**
 * Interface for querying what the device's camera and encoder can do.
 * This is read-only — it never opens the camera or starts encoding.
 * Used by settings screens to show only options the device supports.
 */
interface DeviceCapabilityQuery {
    /** Get list of camera IDs (e.g., "0" for back, "1" for front). */
    fun getCameraIds(): List<String>

    /** Get resolutions supported by the camera AND the H.264 encoder. */
    fun getSupportedResolutions(cameraId: String): List<Resolution>

    /** Get frame rates supported for the given resolution. */
    fun getSupportedFps(cameraId: String, resolution: Resolution): List<Int>

    /** Check if the device has a front-facing camera. */
    fun hasFrontCamera(): Boolean

    /** Check if the device has a back-facing camera. */
    fun hasBackCamera(): Boolean

    /** Get the maximum video bitrate the H.264 encoder supports, in kbps. */
    fun getMaxVideoBitrateKbps(): Int
}
