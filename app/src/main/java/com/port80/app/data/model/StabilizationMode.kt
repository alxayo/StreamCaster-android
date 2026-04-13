package com.port80.app.data.model

/**
 * Image stabilization modes supported by the camera.
 *
 * Only one mode should be active at a time — enabling both EIS and OIS
 * simultaneously can produce undesirable artifacts.
 */
enum class StabilizationMode {
    /** No stabilization applied. */
    OFF,

    /** Electronic Image Stabilization — software-based, crops the frame slightly. */
    EIS,

    /** Optical Image Stabilization — hardware lens element movement. Not available on all cameras. */
    OIS
}
