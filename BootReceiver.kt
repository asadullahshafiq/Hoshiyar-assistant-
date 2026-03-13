package com.assistant.personal.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Phone restart hone par bhi kaam karta hai
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Assistant ready hai
            com.assistant.personal.storage.CommandStorage(context).loadDefaultCommandsIfEmpty()
        }
    }
}
