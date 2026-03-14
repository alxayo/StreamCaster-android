package com.port80.app.overlay

/**
 * Interface for rendering overlays on the video stream (text, timestamps, watermarks).
 * This is an architectural hook for future features.
 * The current implementation (NoOpOverlayManager) does nothing.
 */
interface OverlayManager {
    /** Called each frame — subclasses can draw overlays here. */
    fun onDrawFrame()

    /** Clean up any overlay resources. */
    fun release()
}
