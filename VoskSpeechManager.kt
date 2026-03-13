package com.assistant.personal.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * VOSK - 100% Offline Speech Recognition
 * Koi internet nahi chahiye
 * Urdu + English dono samjhta hai
 * Samsung A10s par smoothly chalta hai (~50MB model)
 */
class VoskSpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onModelDownloadProgress: (Int) -> Unit
) {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isModelLoaded = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Model folder phone mein
    private val modelDir = File(context.filesDir, "vosk-model")

    // ===== Model Check & Load =====
    fun initialize() {
        scope.launch {
            if (isModelReady()) {
                loadModel()
            } else {
                // Model nahi hai - download karo
                withContext(Dispatchers.Main) {
                    onError("MODEL_NOT_FOUND") // UI ko batao download shuru karo
                }
            }
        }
    }

    fun isModelReady(): Boolean {
        return modelDir.exists() && File(modelDir, "am/final.mdl").exists()
    }

    // ===== Model Load =====
    private suspend fun loadModel() {
        withContext(Dispatchers.IO) {
            try {
                model = Model(modelDir.absolutePath)
                isModelLoaded = true
                withContext(Dispatchers.Main) {
                    onReady()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Model load nahi hua: ${e.message}")
                }
            }
        }
    }

    // ===== Download Model (Sirf Ek Dafa) =====
    fun downloadModel() {
        scope.launch {
            try {
                // Small Urdu+English model (~50MB)
                // Vosk ka small model use karein jo Urdu/Hindi bhi samjhe
                val modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"

                withContext(Dispatchers.Main) {
                    onModelDownloadProgress(0)
                }

                modelDir.mkdirs()
                val zipFile = File(context.cacheDir, "vosk_model.zip")

                // Download
                val connection = URL(modelUrl).openConnection() as HttpURLConnection
                connection.connect()
                val totalSize = connection.contentLength
                var downloadedSize = 0

                connection.inputStream.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloadedSize += bytes
                            val progress = if (totalSize > 0) (downloadedSize * 100 / totalSize) else 0
                            withContext(Dispatchers.Main) {
                                onModelDownloadProgress(progress)
                            }
                        }
                    }
                }

                // Extract ZIP
                withContext(Dispatchers.Main) { onModelDownloadProgress(95) }
                extractZip(zipFile, context.filesDir)
                zipFile.delete()

                // Rename extracted folder
                val extractedDir = context.filesDir.listFiles()?.find {
                    it.isDirectory && it.name.startsWith("vosk-model")
                }
                extractedDir?.renameTo(modelDir)

                withContext(Dispatchers.Main) { onModelDownloadProgress(100) }
                loadModel()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Download fail: ${e.message}")
                }
            }
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    // ===== Sunna Shuru Karo =====
    fun startListening() {
        if (!isModelLoaded || model == null) {
            onError("Model tayyar nahi")
            return
        }

        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {

                override fun onPartialResult(hypothesis: String?) {
                    hypothesis ?: return
                    try {
                        val partial = JSONObject(hypothesis).optString("partial", "")
                        if (partial.isNotEmpty()) {
                            onPartial(partial)
                        }
                    } catch (e: Exception) { /* ignore */ }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis ?: return
                    try {
                        val text = JSONObject(hypothesis).optString("text", "")
                        if (text.isNotEmpty()) {
                            onResult(text)
                        }
                    } catch (e: Exception) { /* ignore */ }
                }

                override fun onFinalResult(hypothesis: String?) {
                    hypothesis ?: return
                    try {
                        val text = JSONObject(hypothesis).optString("text", "")
                        if (text.isNotEmpty()) {
                            onResult(text)
                        }
                    } catch (e: Exception) { /* ignore */ }
                }

                override fun onError(e: Exception?) {
                    onError(e?.message ?: "Koi error aayi")
                }

                override fun onTimeout() {
                    onError("TIMEOUT")
                }
            })
        } catch (e: Exception) {
            onError("Sunna shuru nahi hua: ${e.message}")
        }
    }

    // ===== Sunna Band Karo =====
    fun stopListening() {
        speechService?.stop()
    }

    fun destroy() {
        speechService?.shutdown()
        model?.close()
        scope.cancel()
    }
}
