package com.smscleaner

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.smscleaner.ui.AppActions
import com.smscleaner.ui.AppRoot
import com.smscleaner.ui.SmsCleanerTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var askedPermissions = false

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshStatus() }
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshStatus() }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actions = AppActions(
            requestDefault = { requestDefault() },
            requestPermissions = { requestPermissions() },
            requestContacts = { requestContacts() },
            shareBackup = { shareBackup() },
            openDefaultSettings = { openDefaultSettings() },
            pickTime = { h, m, onPicked ->
                TimePickerDialog(this, { _, hh, mm -> onPicked(hh, mm) }, h, m, true).show()
            },
            toast = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() },
        )

        setContent {
            SmsCleanerTheme {
                AppRoot(vm, actions)
            }
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
        vm.refreshStatus()
        // Ask for SMS permissions once, as soon as we are the default app.
        if (vm.isDefault && !vm.hasSmsPermission && !askedPermissions) {
            askedPermissions = true
            requestPermissions()
        }
    }

    private fun requestDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS) &&
                !rm.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
            ) {
                roleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS))
                return
            }
        }
        val i = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        roleLauncher.launch(i)
    }

    private fun requestPermissions() {
        permLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS,
            )
        )
    }

    private fun requestContacts() {
        permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
    }

    private fun openDefaultSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun shareBackup() {
        val f = Backup.exportCsv(this)
        if (f == null) {
            Toast.makeText(this, "No backup to share.", Toast.LENGTH_LONG).show()
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
}
