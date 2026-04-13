package com.port80.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Range
import android.util.Size
import com.port80.app.data.model.CameraFacing
import com.port80.app.data.model.CameraInfo
import com.port80.app.data.model.Resolution
import com.port80.app.data.model.VideoCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries camera and encoder capabilities using Camera2 API and MediaCodecList.
 * This class is READ-ONLY — it never opens the camera or starts encoding.
 *
 * How it works:
 * 1. Camera2 API tells us what resolutions/fps the camera hardware supports.
 * 2. [MediaCodecList] tells us what the device's video encoders can handle.
 * 3. We intersect both sets to find configurations that actually work end-to-end.
 *
 * Codec-aware: all resolution/fps/bitrate queries accept a [VideoCodec] parameter.
 */
@Singleton
class Camera2CapabilityQuery @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceCapabilityQuery {

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val standardResolutions = listOf(
        Resolution(1920, 1080),
        Resolution(1280, 720),
        Resolution(854, 480),
        Resolution(640, 360)
    )

    private val standardFpsValues = listOf(24, 25, 30, 60)

    // -----------------------------------------------------------------------
    // DeviceCapabilityQuery implementation
    // -----------------------------------------------------------------------

    override fun getCameraIds(): List<String> {
        return try {
            cameraManager.cameraIdList.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getAvailableCameras(): List<CameraInfo> {
        val allIds = getCameraIds()
        if (allIds.isEmpty()) return emptyList()

        // Collect physical camera IDs that belong to a logical multi-camera (API 28+)
        // so we can avoid duplicating them alongside their logical parent.
        val physicalIdsOwnedByLogical = mutableSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            for (id in allIds) {
                val chars = getCameraCharacteristicsSafe(id) ?: continue
                val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                val isLogical = capabilities?.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                ) == true
                if (isLogical) {
                    physicalIdsOwnedByLogical.addAll(chars.physicalCameraIds)
                }
            }
        }

        val cameras = allIds.mapNotNull { id ->
            // Skip physical sub-cameras that are represented by a logical parent
            if (id in physicalIdsOwnedByLogical) return@mapNotNull null
            buildCameraInfo(id)
        }

        // Sort: back cameras (by focal length ascending) → front → external
        val sorted = cameras.sortedWith(
            compareBy<CameraInfo> { facingOrder(it.facing) }
                .thenBy { it.focalLength ?: Float.MAX_VALUE }
        )

        return deriveLabels(sorted)
    }

    override fun getRearCameras(): List<CameraInfo> {
        return getAvailableCameras().filter { it.facing == CameraFacing.BACK }
    }

    override fun getSupportedResolutions(cameraId: String, codec: VideoCodec): List<Resolution> {
        val cameraSizes = getCameraOutputSizes(cameraId)
        if (cameraSizes.isEmpty()) return emptyList()

        val encoderCaps = findEncoderCapabilities(codec)

        return standardResolutions.filter { resolution ->
            val cameraSupports = cameraSizes.any { size ->
                size.width == resolution.width && size.height == resolution.height
            }
            val encoderSupports = encoderCaps?.let { caps ->
                caps.videoCapabilities.isSizeSupported(resolution.width, resolution.height)
            } ?: true

            cameraSupports && encoderSupports
        }
    }

    override fun getSupportedFps(cameraId: String, resolution: Resolution, codec: VideoCodec): List<Int> {
        val fpsRanges = getCameraFpsRanges(cameraId)
        if (fpsRanges.isEmpty()) return listOf(30)

        val encoderCaps = findEncoderCapabilities(codec)
        val maxEncoderFps = encoderCaps?.videoCapabilities
            ?.getSupportedFrameRatesFor(resolution.width, resolution.height)
            ?.upper?.toInt()
            ?: Int.MAX_VALUE

        return standardFpsValues.filter { fps ->
            val cameraSupports = fpsRanges.any { range ->
                fps >= range.lower && fps <= range.upper
            }
            val encoderSupports = fps <= maxEncoderFps
            cameraSupports && encoderSupports
        }.sorted()
    }

    override fun hasFrontCamera(): Boolean {
        return getCameraIds().any { id ->
            getCameraFacing(id) == CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    override fun hasBackCamera(): Boolean {
        return getCameraIds().any { id ->
            getCameraFacing(id) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    override fun getMaxVideoBitrateKbps(codec: VideoCodec): Int {
        val encoderCaps = findEncoderCapabilities(codec)
        return encoderCaps?.videoCapabilities
            ?.bitrateRange
            ?.upper
            ?.let { it / 1000 }
            ?: DEFAULT_MAX_BITRATE_KBPS
    }

    override fun getSupportedVideoCodecs(): List<VideoCodec> {
        return VideoCodec.entries.filter { codec ->
            findEncoderCapabilities(codec) != null
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers — Camera2 queries
    // -----------------------------------------------------------------------

    private fun getCameraOutputSizes(cameraId: String): List<Size> {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            configMap?.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList()
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getCameraFpsRanges(cameraId: String): List<Range<Int>> {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList()
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getCameraFacing(cameraId: String): Int? {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            chars.get(CameraCharacteristics.LENS_FACING)
        } catch (e: Exception) {
            null
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers — Camera enumeration
    // -----------------------------------------------------------------------

    private fun getCameraCharacteristicsSafe(cameraId: String): CameraCharacteristics? {
        return try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildCameraInfo(cameraId: String): CameraInfo? {
        val chars = getCameraCharacteristicsSafe(cameraId) ?: return null
        val lensFacing = chars.get(CameraCharacteristics.LENS_FACING) ?: return null

        val facing = when (lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
            CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraFacing.EXTERNAL
            else -> CameraFacing.EXTERNAL
        }

        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val primaryFocalLength = focalLengths?.firstOrNull()

        val isLogical = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            capabilities?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
            ) == true
        } else {
            false
        }

        return CameraInfo(
            id = cameraId,
            facing = facing,
            label = "", // placeholder — resolved by deriveLabels() below
            focalLength = primaryFocalLength,
            isLogicalMultiCamera = isLogical
        )
    }

    private fun facingOrder(facing: CameraFacing): Int = when (facing) {
        CameraFacing.BACK -> 0
        CameraFacing.FRONT -> 1
        CameraFacing.EXTERNAL -> 2
    }

    /**
     * Assign human-readable labels using relative focal length comparison.
     *
     * Raw focal lengths are physical mm (not 35mm-equivalent), so we compare
     * lenses within the same facing group relative to each other rather than
     * using absolute thresholds.
     */
    private fun deriveLabels(cameras: List<CameraInfo>): List<CameraInfo> {
        // Group by facing so rear labels are derived independently of front
        val grouped = cameras.groupBy { it.facing }
        return grouped.flatMap { (facing, group) ->
            when {
                facing == CameraFacing.FRONT -> group.map { it.copy(label = "Front") }
                facing == CameraFacing.EXTERNAL -> group.map { it.copy(label = "External") }
                group.size == 1 -> group.map { it.copy(label = "Back") }
                else -> labelRearCameras(group)
            }
        }
    }

    /**
     * Label multiple rear cameras by comparing their focal lengths relative to
     * the group. Sorted shortest→longest focal length coming in.
     */
    private fun labelRearCameras(rearCameras: List<CameraInfo>): List<CameraInfo> {
        // If no focal length data at all, fall back to numbered labels
        if (rearCameras.all { it.focalLength == null }) {
            return rearCameras.mapIndexed { i, cam ->
                cam.copy(label = "Back ${i + 1}")
            }
        }

        val sorted = rearCameras.sortedBy { it.focalLength ?: Float.MAX_VALUE }
        val medianFocal = sorted.mapNotNull { it.focalLength }
            .let { fls -> fls[fls.size / 2] }

        return sorted.map { cam ->
            val fl = cam.focalLength
            val label = when {
                fl == null -> "Back"
                sorted.size == 2 -> {
                    if (cam == sorted.first()) "Wide" else "Telephoto"
                }
                fl < medianFocal * 0.7f -> "Ultra-Wide"
                fl > medianFocal * 3.0f -> "Super Telephoto"
                fl > medianFocal * 1.5f -> "Telephoto"
                else -> "Wide"
            }
            cam.copy(label = label)
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers — MediaCodec queries
    // -----------------------------------------------------------------------

    /** Map our VideoCodec to the Android MIME type for encoder lookup. */
    private fun codecToMimeType(codec: VideoCodec): String = when (codec) {
        VideoCodec.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
        VideoCodec.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
        VideoCodec.AV1 -> MediaFormat.MIMETYPE_VIDEO_AV1
    }

    /**
     * Find the hardware encoder capabilities for the given codec.
     * Returns null if no hardware encoder is found (e.g., AV1 on older devices).
     */
    private fun findEncoderCapabilities(codec: VideoCodec): MediaCodecInfo.CodecCapabilities? {
        return try {
            val mimeType = codecToMimeType(codec)
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val encoderInfo = codecList.codecInfos.firstOrNull { info ->
                info.isEncoder && info.supportedTypes.any { type ->
                    type.equals(mimeType, ignoreCase = true)
                }
            }
            encoderInfo?.getCapabilitiesForType(mimeType)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val DEFAULT_MAX_BITRATE_KBPS = 8000
    }
}
