package com.port80.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX + ML Kit screen used only for importing endpoint QR codes. It returns
 * raw QR text to the previous navigation screen; parsing happens in the
 * EndpointViewModel so the UI stays focused on camera work.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }

    // The user already chose Scan QR before navigation, so requesting on entry
    // still follows the "permission after explicit action" rule.
    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Endpoint QR") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (cameraGranted) {
                QrCameraPreview(onQrScanned = onQrScanned)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Camera permission is needed to scan endpoint QR codes.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Camera Permission")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Enter Manually")
                    }
                }
            }
        }
    }
}

/** Hosts the Android CameraX PreviewView inside Compose. */
@Composable
private fun QrCameraPreview(onQrScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnQrScanned by rememberUpdatedState(onQrScanned)
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analyzer = remember {
        QrCodeAnalyzer { rawValue -> latestOnQrScanned(rawValue) }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Release CameraX and ML Kit when the user leaves the scanner.
            runCatching { cameraProviderFuture.get().unbindAll() }
            analyzer.close()
        }
    }

    AndroidView(
        factory = { viewContext ->
            val previewView = PreviewView(viewContext)
            val preview = Preview.Builder().build().also { cameraPreview ->
                cameraPreview.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(viewContext), analyzer)
                }

            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                },
                ContextCompat.getMainExecutor(viewContext)
            )
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    CircularProgressIndicator()
}

/**
 * Feeds CameraX frames into ML Kit. The AtomicBoolean prevents repeated QR
 * detections from navigating back multiple times before the screen disposes.
 */
private class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val hasReportedResult = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (hasReportedResult.get()) return@addOnSuccessListener
                val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue }
                if (rawValue != null && hasReportedResult.compareAndSet(false, true)) {
                    onQrCodeScanned(rawValue)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun close() {
        scanner.close()
    }
}