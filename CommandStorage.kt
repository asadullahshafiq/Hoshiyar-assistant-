package com.assistant.personal.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM Encrypted Storage
 * Sari commands Android KeyStore mein safely store hoti hain
 * Koi bhi app ya browser access nahi kar sakta
 */
class CommandStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("assistant_vault", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_ALIAS = "assistant_master_key"
    private val COMMANDS_KEY = "encrypted_commands"
    private val SETTINGS_KEY = "encrypted_settings"

    // ===== Android KeyStore Setup =====
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            keyGenerator.generateKey()
        }

        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    // ===== Encrypt =====
    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    // ===== Decrypt =====
    private fun decrypt(encryptedText: String): String {
        val combined = Base64.decode(encryptedText, Base64.DEFAULT)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    // ===== Command Model =====
    data class CustomCommand(
        val id: String = java.util.UUID.randomUUID().toString(),
        val trigger: String,       // "chai bana do" ya "open camera"
        val action: String,        // ACTION_CALL, ACTION_APP, ACTION_SMS etc.
        val parameter: String = "", // phone number, app name, message etc.
        val language: String = "both", // "urdu", "english", "both"
        val isEnabled: Boolean = true,
        val createdAt: Long = System.currentTimeMillis()
    )

    // ===== Save All Commands =====
    fun saveCommands(commands: List<CustomCommand>) {
        val json = gson.toJson(commands)
        val encrypted = encrypt(json)
        prefs.edit().putString(COMMANDS_KEY, encrypted).apply()
    }

    // ===== Load All Commands =====
    fun loadCommands(): MutableList<CustomCommand> {
        val encrypted = prefs.getString(COMMANDS_KEY, null) ?: return mutableListOf()
        return try {
            val json = decrypt(encrypted)
            val type = object : TypeToken<MutableList<CustomCommand>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    // ===== Add Command =====
    fun addCommand(command: CustomCommand) {
        val commands = loadCommands()
        commands.add(command)
        saveCommands(commands)
    }

    // ===== Update Command =====
    fun updateCommand(command: CustomCommand) {
        val commands = loadCommands()
        val index = commands.indexOfFirst { it.id == command.id }
        if (index >= 0) {
            commands[index] = command
            saveCommands(commands)
        }
    }

    // ===== Delete Command =====
    fun deleteCommand(id: String) {
        val commands = loadCommands()
        commands.removeAll { it.id == id }
        saveCommands(commands)
    }

    // ===== Find Matching Command =====
    fun findCommand(spokenText: String): CustomCommand? {
        val commands = loadCommands().filter { it.isEnabled }
        val spoken = spokenText.lowercase().trim()

        // Exact match pehle
        commands.forEach { cmd ->
            if (spoken.contains(cmd.trigger.lowercase())) {
                return cmd
            }
        }

        // Partial match
        commands.forEach { cmd ->
            val words = cmd.trigger.lowercase().split(" ")
            if (words.all { spoken.contains(it) }) {
                return cmd
            }
        }

        return null
    }

    // ===== Settings =====
    data class AssistantSettings(
        val assistantName: String = "ہوشیار",
        val defaultLanguage: String = "both",
        val voiceSpeed: Float = 1.0f,
        val wakePhraseEnabled: Boolean = false,
        val wakePhrase: String = "ہاں بولو",
        val pinEnabled: Boolean = false,
        val pin: String = "",
        val darkMode: Boolean = true
    )

    fun saveSettings(settings: AssistantSettings) {
        val json = gson.toJson(settings)
        val encrypted = encrypt(json)
        prefs.edit().putString(SETTINGS_KEY, encrypted).apply()
    }

    fun loadSettings(): AssistantSettings {
        val encrypted = prefs.getString(SETTINGS_KEY, null) ?: return AssistantSettings()
        return try {
            val json = decrypt(encrypted)
            gson.fromJson(json, AssistantSettings::class.java) ?: AssistantSettings()
        } catch (e: Exception) {
            AssistantSettings()
        }
    }

    // ===== Default Commands Load karo (First Time) =====
    fun loadDefaultCommandsIfEmpty() {
        if (loadCommands().isEmpty()) {
            val defaults = mutableListOf(
                CustomCommand(trigger = "camera kholo", action = "ACTION_APP", parameter = "camera"),
                CustomCommand(trigger = "camera open", action = "ACTION_APP", parameter = "camera"),
                CustomCommand(trigger = "torch on", action = "ACTION_TORCH", parameter = "on"),
                CustomCommand(trigger = "torch off", action = "ACTION_TORCH", parameter = "off"),
                CustomCommand(trigger = "torch chala", action = "ACTION_TORCH", parameter = "on"),
                CustomCommand(trigger = "torch band", action = "ACTION_TORCH", parameter = "off"),
                CustomCommand(trigger = "wifi on", action = "ACTION_WIFI", parameter = "on"),
                CustomCommand(trigger = "wifi off", action = "ACTION_WIFI", parameter = "off"),
                CustomCommand(trigger = "wifi chala", action = "ACTION_WIFI", parameter = "on"),
                CustomCommand(trigger = "wifi band", action = "ACTION_WIFI", parameter = "off"),
                CustomCommand(trigger = "bluetooth on", action = "ACTION_BLUETOOTH", parameter = "on"),
                CustomCommand(trigger = "bluetooth off", action = "ACTION_BLUETOOTH", parameter = "off"),
                CustomCommand(trigger = "alarm lagao", action = "ACTION_ALARM", parameter = ""),
                CustomCommand(trigger = "set alarm", action = "ACTION_ALARM", parameter = ""),
                CustomCommand(trigger = "calculator", action = "ACTION_APP", parameter = "calculator"),
                CustomCommand(trigger = "calculator kholo", action = "ACTION_APP", parameter = "calculator"),
                CustomCommand(trigger = "settings kholo", action = "ACTION_APP", parameter = "settings"),
                CustomCommand(trigger = "open settings", action = "ACTION_APP", parameter = "settings"),
                CustomCommand(trigger = "gallery kholo", action = "ACTION_APP", parameter = "gallery"),
                CustomCommand(trigger = "open gallery", action = "ACTION_APP", parameter = "gallery"),
                CustomCommand(trigger = "volume barha", action = "ACTION_VOLUME", parameter = "up"),
                CustomCommand(trigger = "volume ghata", action = "ACTION_VOLUME", parameter = "down"),
                CustomCommand(trigger = "mute karo", action = "ACTION_VOLUME", parameter = "mute"),
                CustomCommand(trigger = "mute", action = "ACTION_VOLUME", parameter = "mute"),
                CustomCommand(trigger = "time batao", action = "ACTION_TIME", parameter = ""),
                CustomCommand(trigger = "what time", action = "ACTION_TIME", parameter = ""),
                CustomCommand(trigger = "kitna baja", action = "ACTION_TIME", parameter = ""),
                CustomCommand(trigger = "battery", action = "ACTION_BATTERY", parameter = ""),
                CustomCommand(trigger = "battery kitni", action = "ACTION_BATTERY", parameter = "")
            )
            saveCommands(defaults)
        }
    }
}
