package com.assistant.personal.actions

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import com.assistant.personal.storage.CommandStorage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Ye class mobile ko control karti hai
 * Torch, WiFi, Bluetooth, Calls, SMS, Volume, Apps sab kuch
 */
class ActionExecutor(
    private val context: Context,
    private val tts: TextToSpeech,
    private val storage: CommandStorage
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var torchEnabled = false

    // ===== Main Execute Function =====
    fun execute(command: CommandStorage.CustomCommand, spokenText: String): Boolean {
        return when (command.action) {
            "ACTION_CALL" -> makeCall(command.parameter)
            "ACTION_SMS" -> sendSms(command.parameter, extractMessage(spokenText))
            "ACTION_APP" -> openApp(command.parameter)
            "ACTION_TORCH" -> controlTorch(command.parameter == "on")
            "ACTION_WIFI" -> controlWifi(command.parameter == "on")
            "ACTION_BLUETOOTH" -> controlBluetooth(command.parameter == "on")
            "ACTION_VOLUME" -> controlVolume(command.parameter)
            "ACTION_ALARM" -> setAlarm(spokenText)
            "ACTION_TIME" -> tellTime()
            "ACTION_BATTERY" -> tellBattery()
            "ACTION_SPEAK" -> speak(command.parameter)
            else -> false
        }
    }

    // ===== Smart Built-in Commands =====
    fun executeBuiltIn(text: String): Boolean {
        val t = text.lowercase()

        // Phone Call
        if (t.contains("call") || t.contains("phone karo") || t.contains("call karo")) {
            val contact = extractContactName(t)
            speak("$contact ko call kar raha hun")
            return true
        }

        // Time
        if (t.contains("time") || t.contains("baja") || t.contains("waqt")) {
            tellTime(); return true
        }

        // Date
        if (t.contains("date") || t.contains("aaj") || t.contains("tarikh")) {
            tellDate(); return true
        }

        // Battery
        if (t.contains("battery")) {
            tellBattery(); return true
        }

        // Torch
        if (t.contains("torch") || t.contains("flashlight") || t.contains("roshni")) {
            val on = t.contains("on") || t.contains("chala") || t.contains("karo")
            controlTorch(on); return true
        }

        // WiFi
        if (t.contains("wifi")) {
            val on = t.contains("on") || t.contains("chala")
            controlWifi(on); return true
        }

        // Volume
        if (t.contains("volume") || t.contains("awaaz")) {
            when {
                t.contains("barha") || t.contains("up") || t.contains("zyada") ->
                    controlVolume("up")
                t.contains("ghata") || t.contains("down") || t.contains("kam") ->
                    controlVolume("down")
                t.contains("mute") || t.contains("band") ->
                    controlVolume("mute")
            }
            return true
        }

        return false
    }

    // ===== Make Phone Call =====
    private fun makeCall(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            speak("Call nahi ho saka")
            false
        }
    }

    // ===== Send SMS =====
    private fun sendSms(number: String, message: String): Boolean {
        return try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, message, null, null)
            speak("Message bhej diya")
            true
        } catch (e: Exception) {
            speak("Message nahi gaya")
            false
        }
    }

    // ===== Open App =====
    fun openApp(appName: String): Boolean {
        val packageName = getPackageName(appName) ?: return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: when (appName.lowercase()) {
                    "camera" -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    "settings" -> Intent(Settings.ACTION_SETTINGS)
                    "gallery" -> Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                    }
                    else -> null
                }

            intent?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(it)
                true
            } ?: false
        } catch (e: Exception) {
            speak("App nahi khul saka")
            false
        }
    }

    // ===== Torch Control =====
    fun controlTorch(enable: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
            torchEnabled = enable
            if (enable) speak("Torch on kar diya") else speak("Torch band kar diya")
            true
        } catch (e: Exception) {
            speak("Torch nahi chali")
            false
        }
    }

    // ===== WiFi Control =====
    @Suppress("DEPRECATION")
    private fun controlWifi(enable: Boolean): Boolean {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = enable
            if (enable) speak("WiFi on kar diya") else speak("WiFi band kar diya")
            true
        } catch (e: Exception) {
            // Android 10+ par direct control nahi
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            speak("WiFi settings khol di hain")
            true
        }
    }

    // ===== Bluetooth Control =====
    private fun controlBluetooth(enable: Boolean): Boolean {
        return try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (enable) {
                bluetoothAdapter?.enable()
                speak("Bluetooth on kar diya")
            } else {
                bluetoothAdapter?.disable()
                speak("Bluetooth band kar diya")
            }
            true
        } catch (e: Exception) {
            speak("Bluetooth control nahi hua")
            false
        }
    }

    // ===== Volume Control =====
    private fun controlVolume(direction: String): Boolean {
        return try {
            when (direction) {
                "up" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Volume barha diya")
                }
                "down" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Volume ghata diya")
                }
                "mute" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Mute kar diya")
                }
            }
            true
        } catch (e: Exception) { false }
    }

    // ===== Set Alarm =====
    private fun setAlarm(spokenText: String): Boolean {
        return try {
            // Extract time from spoken text
            val hour = extractHour(spokenText)
            val minute = extractMinute(spokenText)

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Assistant Alarm")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            speak("Alarm set kar diya")
            true
        } catch (e: Exception) { false }
    }

    // ===== Tell Time =====
    private fun tellTime(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val amPm = if (hour < 12) "subah" else if (hour < 17) "dopehar" else if (hour < 20) "shaam" else "raat"
        val hour12 = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        speak("$amPm ke $hour12 baj ke $minute minute hain")
        return true
    }

    // ===== Tell Date =====
    private fun tellDate(): Boolean {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val date = sdf.format(Date())
        speak("Aaj ki tarikh $date hai")
        return true
    }

    // ===== Tell Battery =====
    private fun tellBattery(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val status = if (charging) "charge ho rahi hai" else "charge nahi ho rahi"
        speak("Battery $level percent hai aur $status")
        return true
    }

    // ===== Speak =====
    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_utterance")
    }

    // ===== Helper Functions =====
    private fun extractMessage(text: String): String {
        val patterns = listOf("likho", "bhejo", "message", "send", "kaho")
        for (p in patterns) {
            val idx = text.lowercase().indexOf(p)
            if (idx >= 0) return text.substring(idx + p.length).trim()
        }
        return text
    }

    private fun extractContactName(text: String): String {
        return text
            .replace("call karo", "")
            .replace("call karna", "")
            .replace("call", "")
            .replace("phone", "")
            .replace("ko", "")
            .trim()
    }

    private fun extractHour(text: String): Int {
        val regex = Regex("(\\d+)\\s*(baj|o'clock|:)")
        return regex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 7
    }

    private fun extractMinute(text: String): Int {
        val regex = Regex(":(\\d+)")
        return regex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun getPackageName(appName: String): String? {
        return when (appName.lowercase()) {
            "camera" -> "com.sec.android.app.camera"
            "gallery" -> "com.sec.android.gallery3d"
            "calculator" -> "com.sec.android.app.popupcalculator"
            "settings" -> "com.android.settings"
            "contacts" -> "com.samsung.android.contacts"
            "messages", "sms" -> "com.samsung.android.messaging"
            "phone" -> "com.samsung.android.dialer"
            "whatsapp" -> "com.whatsapp"
            "facebook" -> "com.facebook.katana"
            "youtube" -> "com.google.android.youtube"
            "maps" -> "com.google.android.apps.maps"
            "chrome" -> "com.android.chrome"
            "play store" -> "com.android.vending"
            "clock" -> "com.sec.android.app.clockpackage"
            "calendar" -> "com.samsung.android.calendar"
            "notes" -> "com.samsung.android.app.notes"
            "music" -> "com.sec.android.app.music"
            "file manager", "files" -> "com.sec.android.app.myfiles"
            else -> null
        }
    }
}
