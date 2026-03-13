package com.assistant.personal.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.assistant.personal.R
import com.assistant.personal.actions.ActionExecutor
import com.assistant.personal.storage.CommandStorage
import com.assistant.personal.voice.VoskSpeechManager
import java.util.Locale

/**
 * Main Assistant Screen
 * Home button lamba dabane par khulti hai
 * VOSK - 100% Offline Speech Recognition
 * Fallback: Internet available ho to Google Speech
 */
class AssistActivity : AppCompatActivity() {

    private lateinit var voskManager: VoskSpeechManager
    private var onlineSpeech: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var commandStorage: CommandStorage
    private lateinit var actionExecutor: ActionExecutor

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var ivWave: ImageView
    private lateinit var bgOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView

    private var ttsReady = false
    private var voskReady = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContentView(R.layout.activity_assist)
        initViews()
        initStorage()
        initTTS()
        checkPermissionAndInitVosk()
    }

    private fun initViews() {
        tvStatus    = findViewById(R.id.tv_status)
        tvResult    = findViewById(R.id.tv_result)
        ivWave      = findViewById(R.id.iv_wave)
        bgOverlay   = findViewById(R.id.bg_overlay)
        progressBar = findViewById(R.id.progress_download)
        tvProgress  = findViewById(R.id.tv_progress)

        bgOverlay.setOnClickListener { finishWithAnimation() }
        ivWave.setOnClickListener {
            if (voskReady) startVoskListening()
            else startOnlineListening()
        }
    }

    private fun initStorage() {
        commandStorage = CommandStorage(this)
        commandStorage.loadDefaultCommandsIfEmpty()
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langs = listOf(Locale("ur", "PK"), Locale("hi", "IN"), Locale.ENGLISH)
                for (lang in langs) {
                    val r = tts.setLanguage(lang)
                    if (r != TextToSpeech.LANG_NOT_SUPPORTED && r != TextToSpeech.LANG_MISSING_DATA) break
                }
                tts.setSpeechRate(1.0f)
                ttsReady = true
                actionExecutor = ActionExecutor(this, tts, commandStorage)
            }
        }
    }

    private fun checkPermissionAndInitVosk() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else {
            initVosk()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            initVosk()
        } else {
            tvStatus.text = "Microphone permission chahiye!"
        }
    }

    private fun initVosk() {
        tvStatus.text = "🔄 Tayyar ho raha hun..."

        voskManager = VoskSpeechManager(
            context = this,
            onResult   = { text    -> handler.post { processCommand(text) } },
            onPartial  = { partial -> handler.post { tvResult.text = partial } },
            onError    = { error   -> handler.post { handleVoskError(error) } },
            onReady    = {
                handler.post {
                    voskReady = true
                    hideDownloadUI()
                    tvStatus.text = "🎤 بولیں... (Offline ✅)"
                    startVoskListening()
                }
            },
            onModelDownloadProgress = { progress ->
                handler.post {
                    progressBar.progress = progress
                    tvProgress.text = "Model download: $progress%"
                }
            }
        )
        voskManager.initialize()
    }

    private fun handleVoskError(error: String) {
        when (error) {
            "MODEL_NOT_FOUND" -> showModelDownloadUI()
            "TIMEOUT"         -> startVoskListening()
            else -> {
                tvStatus.text = "⚡ Online mode mein switch..."
                startOnlineListening()
            }
        }
    }

    private fun startVoskListening() {
        tvStatus.text = "🎤 بولیں... (Offline ✅)"
        tvResult.text = ""
        startWaveAnimation()
        voskManager.startListening()
    }

    private fun showModelDownloadUI() {
        progressBar.visibility = View.VISIBLE
        tvProgress.visibility  = View.VISIBLE
        tvStatus.text = "📥 Pehli dafa model download ho raha hai..."
        tvResult.text = "50MB - sirf ek dafa - phir hamesha offline"
        voskManager.downloadModel()
    }

    private fun hideDownloadUI() {
        progressBar.visibility = View.GONE
        tvProgress.visibility  = View.GONE
    }

    // ===== ONLINE FALLBACK =====
    private fun startOnlineListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "❌ Internet nahi, model bhi nahi"
            return
        }
        tvStatus.text = "🎤 بولیں... (Online)"
        startWaveAnimation()

        onlineSpeech = SpeechRecognizer.createSpeechRecognizer(this)
        onlineSpeech?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                processCommand(t)
            }
            override fun onError(e: Int) { stopWaveAnimation(); handler.postDelayed({ startOnlineListening() }, 1500) }
            override fun onPartialResults(p: Bundle?) { tvResult.text = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: "" }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) { val s = 1f+(r/20f).coerceIn(0f,1f); ivWave.scaleX=s; ivWave.scaleY=s }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { stopWaveAnimation() }
            override fun onEvent(t: Int, p: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGES, "ur-PK,en-US,hi-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        onlineSpeech?.startListening(intent)
    }

    // ===== COMMAND PROCESS =====
    private fun processCommand(spokenText: String) {
        if (::voskManager.isInitialized) voskManager.stopListening()
        stopWaveAnimation()
        tvResult.text = "\"$spokenText\""
        tvStatus.text = "✅ سمجھ آ گیا"

        val custom = commandStorage.findCommand(spokenText)
        if (custom != null) {
            actionExecutor.execute(custom, spokenText)
            handler.postDelayed({ finishWithAnimation() }, 1800)
            return
        }

        if (actionExecutor.executeBuiltIn(spokenText)) {
            handler.postDelayed({ finishWithAnimation() }, 1800)
            return
        }

        tvStatus.text = "❓ Command nahi mili"
        if (ttsReady) actionExecutor.speak("Ye command maloom nahi. App mein add kar sakte hain.")
        handler.postDelayed({ if (voskReady) startVoskListening() else startOnlineListening() }, 2500)
    }

    // ===== ANIMATIONS =====
    private fun startWaveAnimation() {
        val anim = ObjectAnimator.ofFloat(ivWave, "alpha", 0.5f, 1f).apply {
            duration = 500; repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        anim.start(); ivWave.tag = anim
    }

    private fun stopWaveAnimation() {
        (ivWave.tag as? ObjectAnimator)?.cancel()
        ivWave.alpha = 1f; ivWave.scaleX = 1f; ivWave.scaleY = 1f
    }

    private fun finishWithAnimation() {
        finish(); overridePendingTransition(0, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voskManager.isInitialized) voskManager.destroy()
        onlineSpeech?.destroy()
        if (::tts.isInitialized) tts.shutdown()
    }

    override fun onBackPressed() = finishWithAnimation()
}
