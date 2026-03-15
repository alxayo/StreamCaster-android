package com.port80.app.ui.components

import android.view.SurfaceHolder
import com.pedro.library.view.OpenGlView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Camera preview composable that displays the live camera feed.
 *
 * This wraps a native Android SurfaceView inside Compose using AndroidView.
 * RootEncoder's RtmpCamera2 renders directly onto this SurfaceView.
 *
 * The SurfaceView lifecycle is managed via SurfaceHolder.Callback:
 * - surfaceCreated: Notify the ViewModel that the surface is ready for preview
 * - surfaceDestroyed: Notify the ViewModel to detach preview (streaming continues)
 *
 * @param modifier Layout modifier for sizing/positioning
 * @param onSurfaceReady Called when the SurfaceView is created and ready for camera preview
 * @param onSurfaceDestroyed Called when the SurfaceView is being destroyed
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceReady: (OpenGlView) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    // AndroidView bridges traditional Android Views into Compose.
    // OpenGlView (from RootEncoder) extends SurfaceView and is the required
    // surface type for the RtmpCamera2(OpenGlView, ConnectChecker) constructor,
    // which renders the live camera preview directly onto the view.
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { context ->
            val openGlView = OpenGlView(context)
            openGlView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    // Pass the OpenGlView itself (not just the holder) so RtmpCamera2
                    // can be constructed with it for on-screen rendering.
                    onSurfaceReady(openGlView)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    // Surface resized — RootEncoder handles this internally.
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    // Surface is being destroyed — detach preview.
                    // The stream continues without preview when app is backgrounded.
                    onSurfaceDestroyed()
                }
            })
            openGlView
        }
    )
}

/**
 * Placeholder shown when no camera is available or preview hasn't started yet.
 */
@Composable
fun CameraPreviewPlaceholder(
    modifier: Modifier = Modifier,
    message: String = "Camera preview will appear here"
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}
