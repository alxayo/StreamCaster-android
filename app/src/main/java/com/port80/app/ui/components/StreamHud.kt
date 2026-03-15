package com.port80.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.port80.app.data.model.StreamStats
import com.port80.app.data.model.ThermalLevel

/**
 * Heads-Up Display overlay for the streaming screen.
 * Shows real-time stats: bitrate, FPS, resolution, duration, thermal badge.
 * Updated at ~1 Hz from StreamStats StateFlow.
 *
 * Positioned at the top of the camera preview with a semi-transparent background.
 */
@Composable
fun StreamHud(
    stats: StreamStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: bitrate, FPS, resolution
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudText("${stats.videoBitrateKbps} kbps")
            HudText("${stats.fps.toInt()} fps")
            HudText(stats.resolution)
        }

        // Center: duration
        HudText(formatDuration(stats.durationMs))

        // Right side: recording indicator and thermal badge
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stats.isRecording) {
                HudBadge("REC", Color.Red)
            }
            if (stats.thermalLevel != ThermalLevel.NORMAL) {
                HudBadge(
                    text = stats.thermalLevel.name,
                    color = when (stats.thermalLevel) {
                        ThermalLevel.MODERATE -> Color.Yellow
                        ThermalLevel.SEVERE -> Color(0xFFFF8800)
                        ThermalLevel.CRITICAL -> Color.Red
                        else -> Color.Green
                    }
                )
            }
        }
    }
}

@Composable
private fun HudText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun HudBadge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 60000) % 60
    val hours = ms / 3600000
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}
