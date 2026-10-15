package com.smscleaner

/** Best-guess type of a message. Heuristic, so shown as "auto" categories in the UI. */
enum class Category(val label: String, val blurb: String) {
    OTP("OTPs", "One-time passwords and verification codes"),
    BANK("Bank & payments", "Debits, credits, balances and UPI alerts"),
    OFFERS("Offers & promos", "Sales, coupons and marketing"),
    DELIVERY("Orders & delivery", "Shipping and order updates"),
    PERSONAL("People", "Messages from phone numbers"),
    OTHER("Other", "Everything else from companies"),
}

data class ContactEntry(val name: String, val number: String)

data class Sms(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int, // 1 received, 2 sent, 4 sending, 5 failed, 6 queued
    val category: Category,
)

/** All messages with one sender, newest first. */
data class Conversation(
    val key: String,
    val title: String,
    val address: String,
    val messages: List<Sms>,
    val unread: Int,
    val category: Category,
    val isContact: Boolean,
) {
    val last: Sms get() = messages.first()
}
