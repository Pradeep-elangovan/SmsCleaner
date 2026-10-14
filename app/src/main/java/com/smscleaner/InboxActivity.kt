package com.smscleaner

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal inbox so messages stay visible while this app is the default SMS app.
 * Long-press a message to start selecting; tap to add or remove; use Delete in the top bar.
 */
class InboxActivity : AppCompatActivity() {
    private val items = ArrayList<Sms>()
    private val selected = HashSet<Long>()
    private lateinit var adapter: ArrayAdapter<Sms>
    private val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Inbox"
        val list = ListView(this)
        setContentView(list)

        if (!SmsRepository.isDefault(this)) {
            toast("Set this app as the default SMS app to read messages here.")
            finish()
            return
        }

        adapter = object : ArrayAdapter<Sms>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val m = getItem(position)!!
                v.findViewById<TextView>(android.R.id.text1).text =
                    (if (m.type == 2) "To " else "") + m.address + "  -  " + fmt.format(Date(m.date))
                v.findViewById<TextView>(android.R.id.text2).text = m.body
                v.setBackgroundColor(if (selected.contains(m.id)) 0x4D2196F3 else Color.TRANSPARENT)
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemLongClickListener { _, _, pos, _ ->
            toggle(adapter.getItem(pos)!!)
            true
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            if (selected.isNotEmpty()) toggle(adapter.getItem(pos)!!)
        }
        load()
    }

    private fun load() {
        Thread {
            val result = try {
                SmsRepository.recent(applicationContext, 300)
            } catch (e: SecurityException) {
                emptyList()
            }
            runOnUiThread {
                items.clear()
                items.addAll(result)
                selected.clear()
                adapter.notifyDataSetChanged()
                updateTitle()
                if (items.isEmpty()) {
                    toast("No messages found. Check the SMS permission line on the main screen.")
                }
            }
        }.start()
    }

    private fun toggle(m: Sms) {
        if (!selected.remove(m.id)) selected.add(m.id)
        adapter.notifyDataSetChanged()
        updateTitle()
    }

    private fun updateTitle() {
        title = if (selected.isEmpty()) "Inbox (long-press to select)" else "${selected.size} selected"
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Delete").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Select all shown").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 3, 2, "Clear selection").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val any = selected.isNotEmpty()
        menu.findItem(1)?.isVisible = any
        menu.findItem(3)?.isVisible = any
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        1 -> {
            confirmDelete()
            true
        }
        2 -> {
            items.forEach { selected.add(it.id) }
            adapter.notifyDataSetChanged()
            updateTitle()
            true
        }
        3 -> {
            selected.clear()
            adapter.notifyDataSetChanged()
            updateTitle()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmDelete() {
        val chosen = items.filter { selected.contains(it.id) }
        if (chosen.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete ${chosen.size} message(s)?")
            .setMessage(
                "They will be removed from your phone. A backup copy is kept in the app " +
                    "(Share backup on the main screen)."
            )
            .setPositiveButton("Delete") { _, _ ->
                Thread {
                    val n = try {
                        SmsRepository.backup(applicationContext, "Manual delete", chosen)
                        SmsRepository.delete(applicationContext, chosen)
                    } catch (e: Exception) {
                        -1
                    }
                    runOnUiThread {
                        toast(if (n < 0) "Delete failed" else "Deleted $n message(s)")
                        load()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
