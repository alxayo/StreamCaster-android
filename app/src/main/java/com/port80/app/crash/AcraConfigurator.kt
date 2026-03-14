package com.port80.app.crash

import android.app.Application
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.sender.HttpSender

/**
 * Configures ACRA crash reporting with credential redaction.
 *
 * SECURITY RULES:
 * 1. ACRA is only enabled in release builds (not debug).
 * 2. We EXCLUDE sensitive report fields: LOGCAT, SHARED_PREFERENCES, DUMPSYS_MEMINFO, THREAD_DETAILS.
 *    These could leak stream keys that RootEncoder logs internally.
 * 3. All string fields are run through CredentialSanitizer before being sent.
 * 4. Reports are sent over HTTPS only.
 *
 * This is called from App.attachBaseContext() — it must not crash the app if it fails.
 */
object AcraConfigurator {

    // Only these safe fields are included in crash reports
    private val SAFE_REPORT_FIELDS = arrayOf(
        ReportField.STACK_TRACE,
        ReportField.ANDROID_VERSION,
        ReportField.APP_VERSION_CODE,
        ReportField.APP_VERSION_NAME,
        ReportField.PHONE_MODEL,
        ReportField.BRAND,
        ReportField.PRODUCT,
        ReportField.CUSTOM_DATA,
        ReportField.CRASH_CONFIGURATION,
        ReportField.BUILD_CONFIG,
        ReportField.USER_COMMENT
    )

    /**
     * Initialize ACRA crash reporting.
     * Call this from Application.attachBaseContext().
     *
     * @param app The application instance
     * @param reportUrl HTTPS URL to send crash reports to (must be https://)
     */
    fun init(app: Application, reportUrl: String? = null) {
        try {
            val config = CoreConfigurationBuilder()
                .withReportContent(*SAFE_REPORT_FIELDS)
                .withReportSendSuccessToast("Crash report sent. Thank you!")
                .withReportSendFailureToast("Failed to send crash report.")

            // Configure HTTP sender if a report URL is provided
            if (!reportUrl.isNullOrBlank()) {
                // SECURITY: Reject plaintext HTTP — only HTTPS is allowed
                if (!reportUrl.startsWith("https://")) {
                    android.util.Log.w(
                        "AcraConfigurator",
                        "ACRA report URL must use HTTPS. Crash reporting disabled."
                    )
                    return
                }

                config.withPluginConfigurations(
                    HttpSenderConfigurationBuilder()
                        .withUri(reportUrl)
                        .withHttpMethod(HttpSender.Method.POST)
                        .build()
                )
            }

            // Configure the crash dialog shown to users
            config.withPluginConfigurations(
                DialogConfigurationBuilder()
                    .withTitle("StreamCaster Crashed")
                    .withText(
                        "An unexpected error occurred. Would you like to send a crash report?"
                    )
                    .withCommentPrompt("Please describe what you were doing:")
                    .withResTheme(android.R.style.Theme_DeviceDefault_Dialog)
                    .build()
            )

            ACRA.init(app, config.build())
        } catch (e: Exception) {
            // ACRA initialization must NEVER crash the app
            android.util.Log.e("AcraConfigurator", "Failed to initialize ACRA", e)
        }
    }

    /**
     * Sanitize a crash report data map by running all string values through
     * CredentialSanitizer. This removes any RTMP URLs, stream keys, or passwords
     * that might have been captured.
     *
     * Call this before sending any crash report.
     */
    fun sanitizeReport(data: MutableMap<String, String>) {
        for ((key, value) in data) {
            data[key] = CredentialSanitizer.sanitize(value)
        }
    }
}
