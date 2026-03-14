package com.port80.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Range
import android.util.Size
import com.port80.app.data.model.Resolution
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries camera and encoder capabilities using Camera2 API and MediaCodecList.
 * This class is READ-ONLY — it never opens the camera or starts encoding.
 * Used by settings screens to show only options the device actually supports.
 *
 * How it works:
 * 1. Camera2 API tells us what resolutions/fps the camera hardware supports.
 *    Every Android device exposes camera info via [CameraManager] without needing
 *    to open the camera — we just read [CameraCharacteristics].
 * 2. [MediaCodecList] tells us what the device's H.264 (AVC) video encoder can handle.
 *    A resolution might be supported by the camera but not encodable by the chip.
 * 3. We intersect both sets to find configurations that actually work end-to-end.
 */
@Singleton
class Camera2CapabilityQuery @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceCapabilityQuery {

    /**
     * System service that lists cameras and reads their characteristics.
     * Lazy so we don't hit the system service until first use.
     * This does NOT open any camera — it's purely a metadata query.
     */
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /**
     * Common streaming resolutions we care about, ordered largest-first.
     * We only offer these to the user if both camera and encoder support them.
     * Exotic resolutions (e.g. 4000×3000 sensor native) are filtered out because
     * RTMP servers and viewers expect standard 16:9 sizes.
     */
    private val standardResolutions = listOf(
        Resolution(1920, 1080), // 1080p — Full HD
        Resolution(1280, 720),  // 720p  — HD (most common for streaming)
        Resolution(854, 480),   // 480p  — SD (low-bandwidth fallback)
        Resolution(640, 360)    // 360p  — Minimum quality fallback
    )

    /**
     * Standard frame rates we offer in settings.
     * Not all cameras support all of these — we filter at query time.
     */
    private val standardFpsValues = listOf(24, 25, 30, 60)

    // -----------------------------------------------------------------------
    // DeviceCapabilityQuery implementation
    // -----------------------------------------------------------------------

    override fun getCameraIds(): List<String> {
        return try {
            // cameraIdList returns String[] of all cameras (back, front, external).
            // Typical: "0" = back, "1" = front. External USB cameras get higher IDs.
            cameraManager.cameraIdList.toList()
        } catch (e: Exception) {
            // CameraAccessException if the camera service is unavailable.
            emptyList()
        }
    }

    override fun getSupportedResolutions(cameraId: String): List<Resolution> {
        // Step 1: Ask the camera what output sizes it supports.
        val cameraSizes = getCameraOutputSizes(cameraId)
        if (cameraSizes.isEmpty()) return emptyList()

        // Step 2: Ask the H.264 encoder what resolutions it can encode.
        val encoderCaps = findH264EncoderCapabilities()

        // Step 3: Keep only standard resolutions supported by BOTH camera and encoder.
        return standardResolutions.filter { resolution ->
            val cameraSupports = cameraSizes.any { size ->
                size.width == resolution.width && size.height == resolution.height
            }
            val encoderSupports = encoderCaps?.let { caps ->
                caps.videoCapabilities.isSizeSupported(resolution.width, resolution.height)
            } ?: true // If we can't query the encoder, assume it supports standard sizes.

            cameraSupports && encoderSupports
        }
    }

    override fun getSupportedFps(cameraId: String, resolution: Resolution): List<Int> {
        // Camera2 exposes FPS as ranges (e.g., [15, 30] means 15–30 fps).
        // CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES lists what auto-exposure can sustain.
        val fpsRanges = getCameraFpsRanges(cameraId)
        if (fpsRanges.isEmpty()) return listOf(30) // Safe default

        // Also check that the encoder can handle this fps at the given resolution.
        val encoderCaps = findH264EncoderCapabilities()
        val maxEncoderFps = encoderCaps?.videoCapabilities
            ?.getSupportedFrameRatesFor(resolution.width, resolution.height)
            ?.upper?.toInt()
            ?: Int.MAX_VALUE

        // A standard fps value is "supported" if any camera FPS range includes it
        // (i.e., the value falls within [range.lower, range.upper]) and the
        // encoder can handle it at the chosen resolution.
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

    override fun getMaxVideoBitrateKbps(): Int {
        // The H.264 encoder advertises its supported bitrate range.
        // We return the upper bound in kbps so the settings UI can cap the slider.
        val encoderCaps = findH264EncoderCapabilities()
        return encoderCaps?.videoCapabilities
            ?.bitrateRange
            ?.upper
            ?.let { it / 1000 } // Convert bps → kbps
            ?: DEFAULT_MAX_BITRATE_KBPS
    }

    // -----------------------------------------------------------------------
    // Private helpers — Camera2 queries
    // -----------------------------------------------------------------------

    /**
     * Returns the output sizes the camera sensor supports for H.264-compatible
     * surface types (SurfaceTexture class).
     *
     * Camera2 stores this in [CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP],
     * which lists every (format, width, height) the camera can produce.
     * We query for SurfaceTexture because that's what RootEncoder's preview uses.
     */
    private fun getCameraOutputSizes(cameraId: String): List<Size> {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            // StreamConfigurationMap describes all supported input/output formats.
            val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            // getOutputSizes(SurfaceTexture) returns sizes for preview/recording surfaces.
            configMap?.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList()
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns the FPS ranges the camera's auto-exposure algorithm can sustain.
     *
     * Each [Range] has a lower and upper bound. For example:
     * - [15, 30] means AE can run anywhere from 15 to 30 fps
     * - [30, 30] means AE is locked to exactly 30 fps
     * - [60, 60] means the camera supports 60 fps mode
     */
    private fun getCameraFpsRanges(cameraId: String): List<Range<Int>> {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList()
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns the LENS_FACING value for a camera, or null if it can't be read.
     * Values: LENS_FACING_FRONT (1), LENS_FACING_BACK (0), LENS_FACING_EXTERNAL (2).
     */
    private fun getCameraFacing(cameraId: String): Int? {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            chars.get(CameraCharacteristics.LENS_FACING)
        } catch (e: Exception) {
            null
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers — MediaCodec queries
    // -----------------------------------------------------------------------

    /**
     * Finds the device's H.264 (AVC) hardware encoder and returns its capabilities.
     *
     * [MediaCodecList] enumerates all codecs (hardware and software).
     * We prefer hardware encoders (they're faster and use less battery).
     * [MediaCodecList.REGULAR_CODECS] excludes software-only fallback codecs
     * that ship with the OS — we only want codecs the device natively supports.
     *
     * Returns null if no H.264 encoder is found (extremely rare on real devices).
     */
    private fun findH264EncoderCapabilities(): MediaCodecInfo.CodecCapabilities? {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            // Find the first H.264 encoder. On most devices this is a hardware encoder
            // like "OMX.qcom.video.encoder.avc" (Qualcomm) or "c2.exynos.h264.encoder" (Samsung).
            val encoderInfo = codecList.codecInfos.firstOrNull { info ->
                info.isEncoder && info.supportedTypes.any { type ->
                    type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
                }
            }
            encoderInfo?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Fallback max bitrate if we can't query the encoder: 8 Mbps. */
        private const val DEFAULT_MAX_BITRATE_KBPS = 8000
    }
}
