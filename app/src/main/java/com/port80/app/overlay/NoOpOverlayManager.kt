package com.port80.app.overlay

/**
 * Default overlay manager that does nothing.
 * This is a placeholder — future versions will implement actual overlays
 * (text, timestamps, watermarks) using RootEncoder's GlStreamInterface.
 */
class NoOpOverlayManager : OverlayManager {
    override fun onDrawFrame() {
        // Intentionally empty — no overlays to render yet
    }

    override fun release() {
        // Nothing to clean up
    }
}
