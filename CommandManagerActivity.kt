package com.assistant.personal.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.assistant.personal.R
import com.assistant.personal.storage.CommandStorage

/**
 * Yahan aap apni unlimited commands add, edit, delete kar sakte hain
 * Sab kuch encrypted hai
 */
class CommandManagerActivity : AppCompatActivity() {

    private lateinit var commandStorage: CommandStorage
    private lateinit var adapter: CommandAdapter
    private var commands = mutableListOf<CommandStorage.CustomCommand>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_commands)

        commandStorage = CommandStorage(this)
        setupRecyclerView()
        loadCommands()

        // Add new command button
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fab_add_command
        ).setOnClickListener {
            showAddCommandDialog()
        }

        // Search
        findViewById<EditText>(R.id.et_search).addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = filterCommands(s.toString())
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = CommandAdapter(commands,
            onEdit = { showEditCommandDialog(it) },
            onDelete = { deleteCommand(it) },
            onToggle = { cmd, enabled ->
                val updated = cmd.copy(isEnabled = enabled)
                commandStorage.updateCommand(updated)
            }
        )
        findViewById<RecyclerView>(R.id.rv_commands).apply {
            layoutManager = LinearLayoutManager(this@CommandManagerActivity)
            adapter = this@CommandManagerActivity.adapter
        }
    }

    private fun loadCommands() {
        commands = commandStorage.loadCommands()
        adapter.updateList(commands)
        updateCount()
    }

    private fun filterCommands(query: String) {
        val all = commandStorage.loadCommands()
        val filtered = if (query.isEmpty()) all
        else all.filter {
            it.trigger.contains(query, ignoreCase = true) ||
            it.action.contains(query, ignoreCase = true) ||
            it.parameter.contains(query, ignoreCase = true)
        }
        adapter.updateList(filtered.toMutableList())
    }

    private fun updateCount() {
        val count = commandStorage.loadCommands().size
        title = "Commands ($count)"
    }

    private fun showAddCommandDialog(existing: CommandStorage.CustomCommand? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_command, null)
        val etTrigger = dialogView.findViewById<EditText>(R.id.et_trigger)
        val spinnerAction = dialogView.findViewById<Spinner>(R.id.spinner_action)
        val etParameter = dialogView.findViewById<EditText>(R.id.et_parameter)
        val etHint = dialogView.findViewById<TextView>(R.id.tv_parameter_hint)

        // Actions list
        val actions = listOf(
            "ACTION_CALL" to "📞 Phone Call Karo",
            "ACTION_SMS" to "💬 SMS Bhejo",
            "ACTION_APP" to "📱 App Kholo",
            "ACTION_TORCH" to "🔦 Torch Control",
            "ACTION_WIFI" to "📶 WiFi Control",
            "ACTION_BLUETOOTH" to "🔵 Bluetooth Control",
            "ACTION_VOLUME" to "🔊 Volume Control",
            "ACTION_ALARM" to "⏰ Alarm Set Karo",
            "ACTION_TIME" to "🕐 Time Batao",
            "ACTION_BATTERY" to "🔋 Battery Batao",
            "ACTION_SPEAK" to "🗣️ Kuch Bolo"
        )

        val hints = mapOf(
            "ACTION_CALL" to "Phone number likhein: 03001234567",
            "ACTION_SMS" to "Number:message  ya sirf number",
            "ACTION_APP" to "App naam: camera, whatsapp, youtube...",
            "ACTION_TORCH" to "on ya off likhein",
            "ACTION_WIFI" to "on ya off likhein",
            "ACTION_BLUETOOTH" to "on ya off likhein",
            "ACTION_VOLUME" to "up, down, ya mute likhein",
            "ACTION_ALARM" to "Time likhein: 7:30",
            "ACTION_TIME" to "Khali chhod sakte hain",
            "ACTION_BATTERY" to "Khali chhod sakte hain",
            "ACTION_SPEAK" to "Jo bolwana ho wo likhein"
        )

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            actions.map { it.second })
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAction.adapter = spinnerAdapter

        spinnerAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val hint = hints[actions[pos].first] ?: ""
                etHint.text = "💡 $hint"
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Fill existing data if editing
        existing?.let {
            etTrigger.setText(it.trigger)
            etParameter.setText(it.parameter)
            val actionIndex = actions.indexOfFirst { a -> a.first == it.action }
            if (actionIndex >= 0) spinnerAction.setSelection(actionIndex)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "نئی Command" else "Command Edit")
            .setView(dialogView)
            .setPositiveButton("محفوظ کریں") { _, _ ->
                val trigger = etTrigger.text.toString().trim()
                val action = actions[spinnerAction.selectedItemPosition].first
                val parameter = etParameter.text.toString().trim()

                if (trigger.isEmpty()) {
                    Toast.makeText(this, "Command phrase likhein", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val command = existing?.copy(trigger = trigger, action = action, parameter = parameter)
                    ?: CommandStorage.CustomCommand(
                        trigger = trigger,
                        action = action,
                        parameter = parameter
                    )

                if (existing == null) commandStorage.addCommand(command)
                else commandStorage.updateCommand(command)

                loadCommands()
                Toast.makeText(this, "Command محفوظ ہو گئی", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("منسوخ", null)
            .show()
    }

    private fun showEditCommandDialog(command: CommandStorage.CustomCommand) {
        showAddCommandDialog(command)
    }

    private fun deleteCommand(command: CommandStorage.CustomCommand) {
        AlertDialog.Builder(this)
            .setTitle("Command Delete")
            .setMessage("\"${command.trigger}\" delete karna chahte hain?")
            .setPositiveButton("Delete") { _, _ ->
                commandStorage.deleteCommand(command.id)
                loadCommands()
            }
            .setNegativeButton("Nahi", null)
            .show()
    }
}

// ===== RecyclerView Adapter =====
class CommandAdapter(
    private var commands: MutableList<CommandStorage.CustomCommand>,
    private val onEdit: (CommandStorage.CustomCommand) -> Unit,
    private val onDelete: (CommandStorage.CustomCommand) -> Unit,
    private val onToggle: (CommandStorage.CustomCommand, Boolean) -> Unit
) : RecyclerView.Adapter<CommandAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTrigger: TextView = view.findViewById(R.id.tv_trigger)
        val tvAction: TextView = view.findViewById(R.id.tv_action)
        val tvParameter: TextView = view.findViewById(R.id.tv_parameter)
        val switchEnabled: Switch = view.findViewById(R.id.switch_enabled)
        val btnEdit: ImageButton = view.findViewById(R.id.btn_edit)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_command, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cmd = commands[position]
        holder.tvTrigger.text = "\"${cmd.trigger}\""
        holder.tvAction.text = getActionEmoji(cmd.action)
        holder.tvParameter.text = if (cmd.parameter.isNotEmpty()) cmd.parameter else "-"
        holder.switchEnabled.isChecked = cmd.isEnabled

        holder.switchEnabled.setOnCheckedChangeListener { _, checked ->
            onToggle(cmd, checked)
        }
        holder.btnEdit.setOnClickListener { onEdit(cmd) }
        holder.btnDelete.setOnClickListener { onDelete(cmd) }
    }

    override fun getItemCount() = commands.size

    fun updateList(newList: MutableList<CommandStorage.CustomCommand>) {
        commands = newList
        notifyDataSetChanged()
    }

    private fun getActionEmoji(action: String) = when (action) {
        "ACTION_CALL" -> "📞 Call"
        "ACTION_SMS" -> "💬 SMS"
        "ACTION_APP" -> "📱 App"
        "ACTION_TORCH" -> "🔦 Torch"
        "ACTION_WIFI" -> "📶 WiFi"
        "ACTION_BLUETOOTH" -> "🔵 Bluetooth"
        "ACTION_VOLUME" -> "🔊 Volume"
        "ACTION_ALARM" -> "⏰ Alarm"
        "ACTION_TIME" -> "🕐 Time"
        "ACTION_BATTERY" -> "🔋 Battery"
        "ACTION_SPEAK" -> "🗣️ Speak"
        else -> action
    }
}
