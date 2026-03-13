package com.assistant.personal.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.assistant.personal.R
import com.assistant.personal.storage.CommandStorage

class SettingsActivity : AppCompatActivity() {

    private lateinit var commandStorage: CommandStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        commandStorage = CommandStorage(this)
        loadSettings()
        setupSaveButton()
    }

    private fun loadSettings() {
        val settings = commandStorage.loadSettings()
        findViewById<EditText>(R.id.et_name)?.setText(settings.assistantName)
        findViewById<Switch>(R.id.switch_dark)?.isChecked = settings.darkMode
        findViewById<SeekBar>(R.id.seekbar_speed)?.progress = (settings.voiceSpeed * 10).toInt()
    }

    private fun setupSaveButton() {
        findViewById<Button>(R.id.btn_save_settings)?.setOnClickListener {
            val name = findViewById<EditText>(R.id.et_name)?.text.toString().ifEmpty { "ہوشیار" }
            val dark = findViewById<Switch>(R.id.switch_dark)?.isChecked ?: true
            val speed = (findViewById<SeekBar>(R.id.seekbar_speed)?.progress ?: 10) / 10f

            val settings = CommandStorage.AssistantSettings(
                assistantName = name,
                darkMode = dark,
                voiceSpeed = speed
            )
            commandStorage.saveSettings(settings)
            Toast.makeText(this, "Settings محفوظ ہو گئی", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
