package com.port80.app.data.qr

import com.port80.app.data.model.EndpointProfile
import com.port80.app.data.model.SrtKeyLength
import com.port80.app.data.model.SrtMode
import com.port80.app.data.model.StreamProtocol
import com.port80.app.data.model.VideoCodec
import org.json.JSONObject
import java.net.URI

/**
 * Parses QR-code text into endpoint data that is safe to show in the existing
 * endpoint editor. The parser is deliberately strict: QR codes are untrusted
 * input, so we only map known fields and reject unsupported versions.
 */
object QrEndpointImportParser {
    private const val SCHEMA_VERSION = 1

    private val allowedJsonKeys = setOf(
        "v",
        "name",
        "url",
        "streamKey",
        "username",
        "password",
        "videoCodec",
        "srtPassphrase",
        "srtKeyLength",
        "srtLatencyMs",
        "srtMode",
        "srtStreamId",
        "isDefault"
    )

    /** Parse either v1 JSON or a bare rtmp/rtmps/srt URL string. */
    fun parse(rawText: String): QrEndpointParseResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return QrEndpointParseResult.Invalid("QR code is empty")
        }

        return if (trimmed.startsWith("{")) {
            parseJson(trimmed)
        } else {
            parsePlainUrl(trimmed)
        }
    }

    /** Build the duplicate comparison key used by EndpointViewModel. */
    fun duplicateKey(candidate: QrEndpointImportCandidate): EndpointDuplicateKey {
        val protocol = StreamProtocol.fromUrl(candidate.url)
        return when (protocol) {
            StreamProtocol.RTMP,
            StreamProtocol.RTMPS -> EndpointDuplicateKey(
                protocol = protocol,
                normalizedUrl = normalizeUrl(candidate.url),
                identity = candidate.streamKey.trim()
            )
            StreamProtocol.SRT -> EndpointDuplicateKey(
                protocol = protocol,
                normalizedUrl = normalizeUrl(candidate.url),
                identity = candidate.srtStreamId?.trim().orEmpty()
            )
        }
    }

    /** Build the same key from a saved profile so duplicates compare equally. */
    fun duplicateKey(profile: EndpointProfile): EndpointDuplicateKey = duplicateKey(
        QrEndpointImportCandidate(
            name = profile.name,
            url = profile.url,
            streamKey = profile.streamKey,
            username = profile.username,
            password = profile.password,
            videoCodec = profile.videoCodec,
            srtPassphrase = profile.srtPassphrase,
            srtKeyLength = profile.srtKeyLength,
            srtLatencyMs = profile.srtLatencyMs,
            srtMode = profile.srtMode,
            srtStreamId = profile.srtStreamId,
            requestedDefault = profile.isDefault
        )
    )

    private fun parseJson(rawJson: String): QrEndpointParseResult {
        return try {
            val json = JSONObject(rawJson)
            val unknownKeys = json.keys().asSequence().filterNot { it in allowedJsonKeys }.toList()
            if (unknownKeys.isNotEmpty()) {
                return QrEndpointParseResult.Invalid("Unsupported QR fields: ${unknownKeys.joinToString()}")
            }

            val version = json.optInt("v", -1)
            if (version != SCHEMA_VERSION) {
                return QrEndpointParseResult.Invalid("Unsupported QR schema version: $version")
            }

            val normalized = normalizeEndpointParts(
                rawUrl = json.optStringOrNull("url"),
                rawStreamKey = json.optStringOrNull("streamKey")
            ) ?: return QrEndpointParseResult.Invalid("Endpoint URL is missing or unsupported")

            val protocol = StreamProtocol.fromUrl(normalized.url)
            val candidate = QrEndpointImportCandidate(
                name = json.optStringOrNull("name") ?: defaultNameFor(protocol),
                url = normalized.url,
                streamKey = if (protocol == StreamProtocol.SRT) "" else normalized.streamKey,
                username = if (protocol == StreamProtocol.SRT) null else json.optStringOrNull("username"),
                password = if (protocol == StreamProtocol.SRT) null else json.optStringOrNull("password"),
                videoCodec = parseVideoCodec(json.optStringOrNull("videoCodec"), protocol),
                srtPassphrase = if (protocol == StreamProtocol.SRT) json.optStringOrNull("srtPassphrase") else null,
                srtKeyLength = SrtKeyLength.fromString(json.optStringOrNull("srtKeyLength")),
                srtLatencyMs = json.optIntOrNull("srtLatencyMs") ?: 120,
                srtMode = SrtMode.fromString(json.optStringOrNull("srtMode")),
                srtStreamId = if (protocol == StreamProtocol.SRT) json.optStringOrNull("srtStreamId") else null,
                requestedDefault = json.optBooleanOrFalse("isDefault")
            )

            validateCandidate(candidate)
        } catch (e: Exception) {
            QrEndpointParseResult.Invalid("QR code is not valid endpoint JSON")
        }
    }

    private fun parsePlainUrl(rawUrl: String): QrEndpointParseResult {
        val normalized = normalizeEndpointParts(rawUrl, rawStreamKey = null)
            ?: return QrEndpointParseResult.Invalid("QR code is not a supported endpoint URL")
        val protocol = StreamProtocol.fromUrl(normalized.url)
        return validateCandidate(
            QrEndpointImportCandidate(
                name = defaultNameFor(protocol),
                url = normalized.url,
                streamKey = if (protocol == StreamProtocol.SRT) "" else normalized.streamKey,
                videoCodec = VideoCodec.H264
            )
        )
    }

    private fun validateCandidate(candidate: QrEndpointImportCandidate): QrEndpointParseResult {
        val protocol = StreamProtocol.fromUrl(candidate.url)
        if (!hasSupportedScheme(candidate.url)) {
            return QrEndpointParseResult.Invalid("Only RTMP, RTMPS, and SRT endpoints are supported")
        }
        if (protocol == StreamProtocol.SRT && !candidate.videoCodec.supportsSrt()) {
            return QrEndpointParseResult.Invalid("SRT endpoints support H.264 and H.265 only")
        }
        return QrEndpointParseResult.Success(candidate)
    }

    private fun normalizeEndpointParts(rawUrl: String?, rawStreamKey: String?): NormalizedEndpointParts? {
        val trimmedUrl = rawUrl?.trim().orEmpty()
        if (!hasSupportedScheme(trimmedUrl)) return null

        val protocol = StreamProtocol.fromUrl(trimmedUrl)
        val normalizedUrl = normalizeUrl(trimmedUrl)
        val providedKey = rawStreamKey?.trim().orEmpty()

        if (protocol == StreamProtocol.SRT || providedKey.isNotBlank()) {
            return NormalizedEndpointParts(url = normalizedUrl, streamKey = providedKey)
        }

        // Common ingest URLs look like rtmp://host/app/STREAM_KEY. When there
        // are at least two path segments, the last segment is treated as the key
        // and everything before it remains the server URL.
        val uri = runCatching { URI(normalizedUrl) }.getOrNull() ?: return NormalizedEndpointParts(normalizedUrl, "")
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return NormalizedEndpointParts(normalizedUrl, "")

        val streamKey = segments.last()
        val basePath = segments.dropLast(1).joinToString(prefix = "/", separator = "/")
        val baseUri = URI(uri.scheme, uri.userInfo, uri.host, uri.port, basePath, uri.query, uri.fragment)
        return NormalizedEndpointParts(url = baseUri.toString().trimEnd('/'), streamKey = streamKey)
    }

    private fun normalizeUrl(rawUrl: String): String {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return rawUrl.trim().trimEnd('/')
        val scheme = uri.scheme?.lowercase() ?: return rawUrl.trim().trimEnd('/')
        val host = uri.host?.lowercase()
        val rebuilt = if (host != null) {
            URI(scheme, uri.userInfo, host, uri.port, uri.path, uri.query, uri.fragment).toString()
        } else {
            rawUrl.trim()
        }
        return rebuilt.trimEnd('/')
    }

    private fun hasSupportedScheme(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("rtmp://") || lower.startsWith("rtmps://") || lower.startsWith("srt://")
    }

    private fun parseVideoCodec(value: String?, protocol: StreamProtocol): VideoCodec {
        val normalized = value.orEmpty().replace(".", "").replace("-", "").replace("_", "").uppercase()
        val codec = when (normalized) {
            "H265", "HEVC" -> VideoCodec.H265
            "AV1" -> VideoCodec.AV1
            else -> VideoCodec.H264
        }
        return if (protocol == StreamProtocol.SRT && !codec.supportsSrt()) VideoCodec.H264 else codec
    }

    private fun defaultNameFor(protocol: StreamProtocol): String = when (protocol) {
        StreamProtocol.RTMP -> "Imported RTMP Endpoint"
        StreamProtocol.RTMPS -> "Imported RTMPS Endpoint"
        StreamProtocol.SRT -> "Imported SRT Endpoint"
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().ifBlank { null }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getInt(key) }.getOrNull()
    }

    private fun JSONObject.optBooleanOrFalse(key: String): Boolean {
        if (!has(key) || isNull(key)) return false
        return runCatching { getBoolean(key) }.getOrDefault(false)
    }

    private data class NormalizedEndpointParts(
        val url: String,
        val streamKey: String
    )
}

/** The safe, parsed endpoint data before it receives a repository ID. */
data class QrEndpointImportCandidate(
    val name: String,
    val url: String,
    val streamKey: String = "",
    val username: String? = null,
    val password: String? = null,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val srtPassphrase: String? = null,
    val srtKeyLength: SrtKeyLength = SrtKeyLength.AES_128,
    val srtLatencyMs: Int = 120,
    val srtMode: SrtMode = SrtMode.CALLER,
    val srtStreamId: String? = null,
    val requestedDefault: Boolean = false
) {
    /** Convert parsed QR data into the normal endpoint model used by the app. */
    fun toProfile(id: String, isDefault: Boolean = false): EndpointProfile = EndpointProfile(
        id = id,
        name = name,
        url = url,
        streamKey = streamKey,
        username = username,
        password = password,
        isDefault = isDefault,
        videoCodec = videoCodec,
        srtPassphrase = srtPassphrase,
        srtKeyLength = srtKeyLength,
        srtLatencyMs = srtLatencyMs,
        srtMode = srtMode,
        srtStreamId = srtStreamId
    )
}

/** Stable value object for duplicate comparisons. */
data class EndpointDuplicateKey(
    val protocol: StreamProtocol,
    val normalizedUrl: String,
    val identity: String
)

/** Result type keeps parser failures explicit instead of throwing into the UI. */
sealed interface QrEndpointParseResult {
    data class Success(val candidate: QrEndpointImportCandidate) : QrEndpointParseResult
    data class Invalid(val reason: String) : QrEndpointParseResult
}
