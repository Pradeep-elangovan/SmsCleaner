package com.smscleaner

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.util.UUID

class RuleEditorActivity : AppCompatActivity() {
    private lateinit var store: RuleStore
    private var existing: Rule? = null

    private lateinit var etName: EditText
    private lateinit var swEnabled: SwitchCompat
    private lateinit var spSenderMode: Spinner
    private lateinit var etSender: EditText
    private lateinit var etKeywords: EditText
    private lateinit var etDays: EditText
    private lateinit var spRead: Spinner
    private lateinit var spBox: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_editor)
        store = RuleStore(this)

        etName = findViewById(R.id.etName)
        swEnabled = findViewById(R.id.swEnabled)
        spSenderMode = findViewById(R.id.spSenderMode)
        etSender = findViewById(R.id.etSender)
        etKeywords = findViewById(R.id.etKeywords)
        etDays = findViewById(R.id.etDays)
        spRead = findViewById(R.id.spRead)
        spBox = findViewById(R.id.spBox)
        val btnPreview = findViewById<Button>(R.id.btnPreview)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnDelete = findViewById<Button>(R.id.btnDelete)

        spSenderMode.adapter = enumAdapter(SenderMode.values().map { it.name })
        spRead.adapter = enumAdapter(ReadState.values().map { it.name })
        spBox.adapter = enumAdapter(Box.values().map { it.name })

        val id = intent.getStringExtra("id")
        existing = if (id == null) null else store.load().firstOrNull { it.id == id }
        existing?.let { fill(it) }
        title = if (existing == null) "New rule" else "Edit rule"
        btnDelete.visibility = if (existing == null) View.GONE else View.VISIBLE

        btnPreview.setOnClickListener { preview() }
        btnSave.setOnClickListener {
            val r = build()
            if (!r.isSafe()) {
                toast(BROAD_RULE_MSG)
            } else {
                store.upsert(r)
                finish()
            }
        }
        btnDelete.setOnClickListener {
            existing?.let { store.delete(it.id) }
            finish()
        }
    }

    private fun enumAdapter(items: List<String>): ArrayAdapter<String> {
        val a = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return a
    }

    private fun fill(r: Rule) {
        etName.setText(r.name)
        swEnabled.isChecked = r.enabled
        spSenderMode.setSelection(r.senderMode.ordinal)
        etSender.setText(r.sender)
        etKeywords.setText(r.keywords)
        etDays.setText(if (r.olderThanDays > 0) r.olderThanDays.toString() else "")
        spRead.setSelection(r.readState.ordinal)
        spBox.setSelection(r.box.ordinal)
    }

    private fun build(): Rule = Rule(
        id = existing?.id ?: UUID.randomUUID().toString(),
        name = etName.text.toString().trim().ifEmpty { "Untitled rule" },
        enabled = swEnabled.isChecked,
        senderMode = SenderMode.values()[spSenderMode.selectedItemPosition],
        sender = etSender.text.toString().trim(),
        keywords = etKeywords.text.toString().trim(),
        olderThanDays = etDays.text.toString().trim().toIntOrNull() ?: 0,
        readState = ReadState.values()[spRead.selectedItemPosition],
        box = Box.values()[spBox.selectedItemPosition],
    )

    private fun preview() {
        val r = build()
        if (!r.isSafe()) {
            toast(BROAD_RULE_MSG)
            return
        }
        if (!SmsRepository.isDefault(this)) {
            toast("Set this app as the default SMS app first (main screen).")
            return
        }
        Thread {
            val result = try {
                SmsRepository.findMatches(applicationContext, r)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (result == null) {
                    toast("Could not read messages.")
                } else {
                    val sample = result.take(5)
                        .joinToString("\n\n") { it.address + ": " + it.body.take(60) }
                        .ifEmpty { "No matches." }
                    AlertDialog.Builder(this)
                        .setTitle("${result.size} message(s) would be deleted")
                        .setMessage(sample)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
