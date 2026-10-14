package com.smscleaner

import android.Manifest
import android.app.RoleManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: RuleStore
    private lateinit var tvStatus: TextView
    private lateinit var btnDefault: Button
    private lateinit var btnTime: Button
    private lateinit var lvRules: ListView
    private var rules: List<Rule> = emptyList()

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = RuleStore(this)

        tvStatus = findViewById(R.id.tvStatus)
        btnDefault = findViewById(R.id.btnDefault)
        btnTime = findViewById(R.id.btnTime)
        lvRules = findViewById(R.id.lvRules)
        val swSchedule = findViewById<SwitchCompat>(R.id.swSchedule)

        swSchedule.isChecked = store.scheduleEnabled
        swSchedule.setOnCheckedChangeListener { _, on ->
            store.scheduleEnabled = on
            Scheduler.apply(this)
        }

        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                store.runHour = h
                store.runMinute = m
                refresh()
                Scheduler.apply(this)
            }, store.runHour, store.runMinute, true).show()
        }

        btnDefault.setOnClickListener {
            if (SmsRepository.isDefault(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } else {
                requestDefault()
            }
        }

        findViewById<Button>(R.id.btnInbox).setOnClickListener {
            startActivity(Intent(this, InboxActivity::class.java))
        }
        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, RuleEditorActivity::class.java))
        }
        findViewById<Button>(R.id.btnRun).setOnClickListener { confirmRun() }
        findViewById<Button>(R.id.btnBackup).setOnClickListener { shareBackup() }

        lvRules.setOnItemClickListener { _, _, position, _ ->
            startActivity(
                Intent(this, RuleEditorActivity::class.java).putExtra("id", rules[position].id)
            )
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val def = SmsRepository.isDefault(this)
        tvStatus.text = "Default SMS app: " + (if (def) "Yes" else "No") +
            "\nLast run: " + store.lastRun.ifEmpty { "never" }
        btnDefault.text = if (def) "Change default SMS app" else "Set as default SMS app"
        btnTime.text = String.format(Locale.US, "%02d:%02d", store.runHour, store.runMinute)
        rules = store.load()
        lvRules.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            rules.map { (if (it.enabled) "[on]  " else "[off] ") + it.name + "\n" + it.summary() }
        )
    }

    private fun requestDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        } else {
            val i = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            roleLauncher.launch(i)
        }
    }

    private fun confirmRun() {
        if (!SmsRepository.isDefault(this)) {
            toast("Set this app as the default SMS app first.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Run all enabled rules now?")
            .setMessage("Matching messages will be permanently deleted. A CSV backup is kept in the app.")
            .setPositiveButton("Run") { _, _ -> runNow() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runNow() {
        Thread {
            val r = CleanupRunner.runAll(applicationContext)
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Done")
                    .setMessage(r.summary())
                    .setPositiveButton("OK", null)
                    .show()
                refresh()
            }
        }.start()
    }

    private fun shareBackup() {
        val f = File(filesDir, "deleted_backup.csv")
        if (!f.exists()) {
            toast("No backup yet - nothing has been deleted.")
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share backup"))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
