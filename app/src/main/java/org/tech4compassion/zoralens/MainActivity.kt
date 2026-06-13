package org.tech4compassion.zoralens

import android.app.PendingIntent
import android.util.Log
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.tech4compassion.zoralens.ui.theme.ZoraLensTheme
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import kotlinx.coroutines.withTimeout
import androidx.compose.runtime.mutableStateListOf


import java.io.ByteArrayOutputStream
import org.tech4compassion.zoralens.BuildConfig


// Inside your MainActivity.kt file
private val geminiMainModel = GenerativeModel (
    modelName = "gemini-3.1-flash-lite",
    apiKey = BuildConfig.GEMINI_API_KEY
)

val localLogList = mutableStateListOf<String>()

fun screenLog(tag: String, message: String) {
    Log.d(tag, message) // Keeps your Android Studio Logcat working
    if (localLogList.size > 40) { localLogList.removeAt(0) } // Cap memory
    val timeStamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
    localLogList.add("[$timeStamp] [$tag] $message")
}

fun deleteScreenLog () {
    localLogList.removeAll(localLogList)
}

class MainActivity : ComponentActivity() {
    var triggerScan: () -> Unit = {}
    private var tts: TextToSpeech? = null
    var usbPort: com.hoho.android.usbserial.driver.UsbSerialPort? = null

    fun vibrateOnScan(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Bahasa Indonesia not supported! Install via Settings.")
                }
                tts?.speak("ZoraLens systems ready", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        setContent {
            ZoraLensTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LensScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSpeak = { text -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) },
                        onStopSpeak = { tts?.stop() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            usbPort?.close()
        } catch (e: Exception) {}
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    // Add this inside your MainActivity class
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Bluetooth clickers usually send VOLUME_UP or ENTER
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_ENTER) {
            screenLog("ZoraClick", "Input Trigger Clicked: KeyCode $keyCode")
            triggerScan()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun LensScreen(modifier: Modifier = Modifier, onSpeak: (String) -> Unit, onStopSpeak: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showLogView by remember { mutableStateOf(false) }

    var activeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var description by remember { mutableStateOf("Siap memindai...") }
    var isLoading by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableLongStateOf(0L) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val cooldownMs = 7000L

    val performScan = {
        val clickTime = System.currentTimeMillis()
        if (!isLoading && (clickTime - lastScanTime >= cooldownMs)) {
            lastScanTime = clickTime
            (context as? MainActivity)?.vibrateOnScan(context)
            onSpeak("Scanning")
            isLoading = true
            activeJob = coroutineScope.launch {
                val result = fetchAndDescribe(context)
                description = result
                onSpeak(result)
                isLoading = false
            }
        } else if (clickTime - lastScanTime < cooldownMs) {
            onSpeak("Please wait, the system is preparing")
        }
    }
    LaunchedEffect(Unit) {
        (context as? MainActivity)?.triggerScan = performScan
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }

    val isCurrentlyCoolingDown = currentTime - lastScanTime < cooldownMs

    if (showLogView) {
        // --- NEW LOG MONITOR OVERLAY PAGE ---
        Column(
            modifier = modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "System Monitor", style = MaterialTheme.typography.headlineMedium)


            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { downloadLogsToDocuments(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("DOWNLOAD")
                }
                Button(onClick = { showLogView = false }) {
                    Text("BACK")
                }
                Button(onClick = { deleteScreenLog() }) {
                    Text("CLEAR")
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(15.dp)) {
                    items(localLogList.size) { index ->
                        Text(
                            text = localLogList[index],
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFF00FF00),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = description, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {performScan()},
                modifier = Modifier.fillMaxWidth().height(100.dp)
            ) {
                Text(
                    text = when {
                        isLoading -> "SCANNING..."
                        isCurrentlyCoolingDown -> {
                            val remaining = (cooldownMs - (currentTime - lastScanTime)) / 1000
                            "WAIT (${remaining}s)"
                        }
                        else -> "SCAN ENVIRONMENT"
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    activeJob?.cancel()
                    onStopSpeak()
                    isLoading = false
                    description = "Scan cancelled."
                },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("STOP / CANCEL")
            }
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = { showLogView = true },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text("VIEW SYSTEM LOGS")
            }
        }
    }
}
// END OF COMPOSABLE


suspend fun fetchAndDescribe(context: Context): String {
    return withContext(Dispatchers.IO) {
        val overallStart = System.currentTimeMillis()
        try {
            withTimeout(25000L) {
                // 1. XIAO TO PHONE
                val usbStart = System.currentTimeMillis()
                val rawDownloaded = captureFrameFromUsb(context)
                screenLog("ZoraTime", "1. USB Transfer: ${System.currentTimeMillis() - usbStart}ms")

                if (rawDownloaded == null) return@withTimeout "Gagal Kamera: Koneksi USB terputus."

                // 2. IMAGE PROCESSING
                val procStart = System.currentTimeMillis()
                val matrix = Matrix().apply {
                    postRotate(270f) // Uprighted image
                    postScale(-1f, 1f, rawDownloaded.width / 2f, rawDownloaded.height / 2f)
                }
                val finalBmp = Bitmap.createBitmap(rawDownloaded, 0, 0, rawDownloaded.width, rawDownloaded.height, matrix, true)

                if (finalBmp == null) return@withTimeout "Gagal: Memori penuh."



                val baos = ByteArrayOutputStream()
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 100, baos) // 100 = No lossy compression
                val jpegBytes = baos.toByteArray()
                val imgSizeKb = baos.size() / 1024
                baos.close() // Close it immediately to free memory

                launch (Dispatchers.IO) {
                    saveToGallery(context, jpegBytes)
                }

                rawDownloaded.recycle()
                screenLog("ZoraTime", "2. Image Proc: ${System.currentTimeMillis() - procStart}ms | Size: ${imgSizeKb}KB")

                // 3. ESSENTIAL PROMPT (RESTORED)
                // 3. ESSENTIAL PROMPT
                Log.d("ZoraLens", "Using Model: ${geminiMainModel.modelName}")

                val prompt = """
                ACT AS: Expert blind guide.
                CONTEXT: The user wears a sensor camera on glasses.
                TASK: Briefly and clearly describe the most important thing in front of the user.
                FORMAT: Describe what the user sees. Maximum 60 words. In English
                NOTES: Ignore the blurry camera quality.
                """.trimIndent()

                val aiStart = System.currentTimeMillis()

                // The system PAUSES here until the cloud responds
                val response = geminiMainModel.generateContent(content {
                    image(finalBmp)
                    text(prompt)
                })

                // The system RESUMES here
                val aiEnd = System.currentTimeMillis()
                val pureInferenceTime = aiEnd - aiStart
                val result = response.text ?: "Gagal: AI tidak memberikan respon."

                // CRITICAL TIMING LOG
                screenLog("ZoraTime", "3. Gemini Roundtrip (${geminiMainModel.modelName}): ${pureInferenceTime}ms")
                // 5. FINAL CLEANUP
                finalBmp.recycle()

                screenLog("ZoraTime", "TOTAL ROUNDTRIP: ${System.currentTimeMillis() - overallStart}ms")
                result // This is the final string returned
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        "Gagal: Waktu habis. Koneksi internet mungkin lambat."
    } catch (e: Exception) {
        val errorMsg = e.localizedMessage ?: ""
        Log.e("ZoraError", "Full Error Stack: $errorMsg")

        when {
            errorMsg.contains("Hardware Error:") -> errorMsg
            errorMsg.contains("Camera Error:") -> errorMsg
            errorMsg.contains("429") -> "AI usage limit reached. Please wait."
            errorMsg.contains("Unable to resolve host") -> "Failed: No internet connection."
            else -> "System error: ${e.javaClass.simpleName}. Try again."
        }
    }
    }
}

// This helps the app talk to the XIAO via USB
// This helps the app talk to the XIAO via USB
fun captureFrameFromUsb(context: Context): Bitmap? {
    val port = ensureUsbConnected(context) ?: return null
    return try {
        // Clear lingering bits
        val vacuumBucket = ByteArray(2048)
        var limit = 0
        while (port.read(vacuumBucket, 5) > 0 && limit < 5) { limit++ }
        android.os.SystemClock.sleep(50)

        // Fire request trigger
        port.write("S".toByteArray(), 100)

        val outStream = java.io.ByteArrayOutputStream()
        val tempBuffer = ByteArray(8192)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        var lastDataTime = System.currentTimeMillis()

        // Continuous data capture loop
        // Continuous data capture loop with fail-fast guard
        while (System.currentTimeMillis() - startTime < 8000) {
            val len = port.read(tempBuffer, 100)
            if (len > 0) {
                outStream.write(tempBuffer, 0, len)
                totalRead += len
                lastDataTime = System.currentTimeMillis()
            } else {
                android.os.SystemClock.sleep(10)
                // Fail fast: If zero bytes arrive within 1.5 seconds, drop out immediately
                if (totalRead == 0 && (System.currentTimeMillis() - startTime > 1500)) break
                if (totalRead > 5000 && (System.currentTimeMillis() - lastDataTime > 400)) break
            }
        }

        val allData = outStream.toByteArray()
        if (allData.isEmpty()) {
            screenLog("ZoraSerial", "Error: Zero bytes received from board.")
            throw Exception("Camera Error: Zero bytes received from board")
        }

        var startIndex = -1
        for (i in 0 until allData.size - 1) {
            if ((allData[i].toInt() and 0xFF == 0xFF) && (allData[i+1].toInt() and 0xFF == 0xD8)) {
                startIndex = i
                break
            }
        }

        if (startIndex != -1) {
            val usableSize = allData.size - startIndex
            BitmapFactory.decodeByteArray(allData, startIndex, usableSize)
        } else {
            // Check if the hardware returned a clean text error string instead of binary imagery
            val rawTextString = String(allData, Charsets.UTF_8).trim()
            if (rawTextString.contains("ERR:")) {
                screenLog("ZoraHardwareError", "Hardware fault signaled: $rawTextString")
                throw Exception("Hardware Error: ${rawTextString.replace("_", " ")}")
            } else {
                screenLog("ZoraSerial", "Error: Missing JPEG signature markers.")
                throw Exception("Camera Error: Missing image signatures")
            }
        }
    } catch (e: Exception) {
        screenLog("ZoraCaptureException", "${e.message}")
        throw e // Pass up to the fetchAndDescribe tracking block
    }
}

private fun saveToGallery(context: Context, jpegBytes: ByteArray) {
    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
    val name = "Zora_${timestamp}.jpg"

    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ZoraLens")
        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1) // Hide while writing
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let { targetUri ->
        try {
            resolver.openOutputStream(targetUri)?.use { it.write(jpegBytes) }

            // PUBLISH: Now reveal it to the gallery
            contentValues.clear()
            contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(targetUri, contentValues, null, null)

            Log.d("ZoraSave", "SUCCESS: Image published to Gallery: $name")
        } catch (e: Exception) {
            Log.e("ZoraSave", "FAILED: ${e.message}")
        }
    }
}

fun ensureUsbConnected(context: Context): com.hoho.android.usbserial.driver.UsbSerialPort? {
    val activity = context as? MainActivity ?: return null

    // If already open, just return it
    if (activity.usbPort?.isOpen == true) return activity.usbPort

    val manager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
    val availableDrivers = com.hoho.android.usbserial.driver.UsbSerialProber.getDefaultProber().findAllDrivers(manager)
    if (availableDrivers.isEmpty()) return null

    val driver = availableDrivers[0]
    val device = driver.device

    // Check Permission
    if (!manager.hasPermission(device)) {
        val intent = PendingIntent.getBroadcast(context, 0, Intent("com.android.example.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE)
        manager.requestPermission(device, intent)
        return null
    }

    val connection = manager.openDevice(device) ?: return null
    val port = driver.ports[0]

    try {
        port.open(connection)
        port.setParameters(
            921600,
            8,
            com.hoho.android.usbserial.driver.UsbSerialPort.STOPBITS_1,
            com.hoho.android.usbserial.driver.UsbSerialPort.PARITY_NONE
        )
        port.dtr = true
        port.rts = true
        activity.usbPort = port
        // Give it ONE long breath after the first cold boot
        Thread.sleep(2000)
        return port
    } catch (e: Exception) {
        return null
    }
}

fun downloadLogsToDocuments(context: Context) {
    if (localLogList.isEmpty()) return

    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
    val filename = "ZoraLog_${timestamp}.txt"

    try {
        // Point directly to the standard public Download root directory
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val zoraFolder = java.io.File(downloadsDir, "ZoraLens")

        // Ensure the ZoraLens folder exists inside the Download directory
        if (!zoraFolder.exists()) {
            zoraFolder.mkdirs()
        }

        val logFile = java.io.File(zoraFolder, filename)

        // Write the local terminal text array to disk
        logFile.printWriter().use { writer ->
            localLogList.forEach { logLine ->
                writer.println(logLine)
            }
        }

        screenLog("ZoraLogExport", "SUCCESS: File saved to Download/ZoraLens/$filename")
    } catch (e: Exception) {
        Log.e("ZoraLogExport", "FAILED to write log file: ${e.message}")
        screenLog("ZoraLogExport", "ERROR: Could not write file.")
    }
} // end





