package com.port80.app.util

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration

/**
 * Helper for locking/unlocking screen orientation.
 * Called from MainActivity.onCreate() BEFORE setContentView().
 *
 * During streaming, rotating the device would destroy and recreate the Activity,
 * which could interrupt the camera preview and cause visual glitches.
 * Locking orientation prevents this.
 */
object OrientationHelper {
    /**
     * Lock the screen to a specific orientation.
     * @param activity The activity to lock
     * @param orientation One of ActivityInfo.SCREEN_ORIENTATION_* constants
     */
    fun lock(activity: Activity, orientation: Int) {
        activity.requestedOrientation = orientation
    }

    /**
     * Unlock the screen to allow rotation.
     * Only call this when no stream is active.
     */
    fun unlock(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /**
     * Lock to the current physical orientation of the device.
     * Useful when starting a stream — locks to whatever orientation
     * the user is currently holding the device in.
     */
    fun lockToCurrentOrientation(activity: Activity) {
        val orientation = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                if (isReverseRotation(activity)) {
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            else ->
                if (isReverseRotation(activity)) {
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
        }
        activity.requestedOrientation = orientation
    }

    /**
     * Checks if the device is in a reverse rotation (180° or 270°).
     */
    private fun isReverseRotation(activity: Activity): Boolean {
        @Suppress("DEPRECATION")
        val rotation = activity.windowManager.defaultDisplay.rotation
        return rotation == android.view.Surface.ROTATION_180 ||
            rotation == android.view.Surface.ROTATION_270
    }
}
