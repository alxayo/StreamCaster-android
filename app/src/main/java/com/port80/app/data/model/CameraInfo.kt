package com.port80.app.data.model

/**
 * Metadata about a single camera on the device.
 *
 * Populated from Camera2 [android.hardware.camera2.CameraCharacteristics]
 * without opening the camera. Used by settings UI (camera picker) and by
 * [com.port80.app.service.StreamingService] to select a specific lens.
 */
data class CameraInfo(
    /** Camera2 camera ID (e.g., "0", "2"). */
    val id: String,
    /** Lens facing direction. */
    val facing: CameraFacing,
    /** Human-readable label (e.g., "Wide", "Ultra-Wide", "Telephoto"). */
    val label: String,
    /** Primary focal length in mm, or null if unavailable. */
    val focalLength: Float?,
    /** True if this is a logical multi-camera (API 28+) that groups physical lenses. */
    val isLogicalMultiCamera: Boolean
)

/**
 * Camera lens facing direction.
 * Maps directly to [android.hardware.camera2.CameraCharacteristics.LENS_FACING].
 */
enum class CameraFacing {
    FRONT,
    BACK,
    EXTERNAL
}
