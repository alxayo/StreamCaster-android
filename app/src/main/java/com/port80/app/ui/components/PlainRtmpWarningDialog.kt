package com.port80.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.port80.app.service.TransportSecurity

/**
 * Warning dialog shown when the user tries to connect via plain RTMP (not RTMPS).
 * Requires explicit user consent before proceeding with an unencrypted connection.
 */
@Composable
fun PlainRtmpWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unencrypted Connection") },
        text = { Text(TransportSecurity.getPlainRtmpWarning()) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Connect Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
