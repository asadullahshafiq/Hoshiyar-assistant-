package com.assistant.personal.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.assistant.personal.R
import com.assistant.personal.storage.CommandStorage

class MainActivity : AppCompatActivity() {

    private lateinit var commandStorage: CommandStorage
    private val PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        commandStorage = CommandStorage(this)
        commandStorage.loadDefaultCommandsIfEmpty()

        setupUI()
        checkPermissions()
        checkDefaultAssistant()
    }

    private fun setupUI() {
        // Assistant ko kholo button
        findViewById<Button>(R.id.btn_open_assistant).setOnClickListener {
            startActivity(Intent(this, AssistActivity::class.java))
        }

        // Commands manage karo
        findViewById<Button>(R.id.btn_manage_commands).setOnClickListener {
            startActivity(Intent(this, CommandManagerActivity::class.java))
        }

        // Default assistant set karo
        findViewById<Button>(R.id.btn_set_default).setOnClickListener {
            openDefaultAssistantSettings()
        }

        // Settings
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Commands count
        updateCommandCount()
    }

    private fun updateCommandCount() {
        val count = commandStorage.loadCommands().size
        findViewById<TextView>(R.id.tv_command_count)?.text = "کل Commands: $count"
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    private fun checkDefaultAssistant() {
        val statusView = findViewById<TextView>(R.id.tv_default_status)
        // Check if we are default assistant
        val assistPackage = Settings.Secure.getString(
            contentResolver,
            "voice_interaction_service"
        )

        if (assistPackage?.contains(packageName) == true) {
            statusView?.text = "✅ Default Assistant Set Hai"
            statusView?.setTextColor(getColor(R.color.green))
        } else {
            statusView?.text = "⚠️ Default Assistant Set Nahi - Neeche Set Karen"
            statusView?.setTextColor(getColor(R.color.orange))
        }
    }

    private fun openDefaultAssistantSettings() {
        AlertDialog.Builder(this)
            .setTitle("Default Assistant Set Karen")
            .setMessage(
                "1. Abhi Settings khulegi\n" +
                "2. 'Default Apps' ya 'Assist & voice input' par tap karen\n" +
                "3. 'Assist app' select karen\n" +
                "4. 'Personal Assistant' choose karen\n\n" +
                "Samsung A10s mein: Settings > Apps > Default Apps > Assist app"
            )
            .setPositiveButton("Settings Kholo") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("Baad Mein", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateCommandCount()
        checkDefaultAssistant()
    }
}
