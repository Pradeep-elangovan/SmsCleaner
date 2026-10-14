package com.smscleaner

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Minimal read-only inbox so messages stay visible while this app is the default SMS app. */
class InboxActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Inbox"
        val list = ListView(this)
        setContentView(list)

        if (!SmsRepository.isDefault(this)) {
            Toast.makeText(this, "Set this app as the default SMS app to read messages here.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val items = try {
            SmsRepository.recent(this, 300)
        } catch (e: SecurityException) {
            emptyList()
        }
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

        list.adapter = object : ArrayAdapter<Sms>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val m = getItem(position)!!
                v.findViewById<TextView>(android.R.id.text1).text =
                    (if (m.type == 2) "To " else "") + m.address + "  -  " + fmt.format(Date(m.date))
                v.findViewById<TextView>(android.R.id.text2).text = m.body
                return v
            }
        }
    }
}
