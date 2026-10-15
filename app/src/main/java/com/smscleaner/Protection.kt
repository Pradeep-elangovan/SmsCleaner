package com.smscleaner

import android.content.Context

/**
 * Senders whose messages must never be deleted by rules or quick clean.
 * If "protect contacts" is on but contacts cannot be read, every phone-number sender is
 * treated as protected (the safe choice), and only company senders can be cleaned.
 */
class Protection(
    contacts: Map<String, String>,
    protectedSenders: List<String>,
    private val protectContacts: Boolean,
    private val contactsReadable: Boolean,
) {
    private val contactKeys: Set<String> = contacts.keys
    private val senderKeys: Set<String> = protectedSenders.map { Sender.key(it) }.toHashSet()

    fun isProtected(address: String): Boolean {
        val key = Sender.key(address)
        if (senderKeys.contains(key)) return true
        if (protectContacts && Sender.isNumeric(address)) {
            if (!contactsReadable) return true
            if (contactKeys.contains(key)) return true
        }
        return false
    }

    companion object {
        val EMPTY = Protection(emptyMap(), emptyList(), false, true)

        fun load(ctx: Context, contacts: Map<String, String> = Contacts.load(ctx)): Protection {
            val store = RuleStore(ctx)
            return Protection(
                contacts, store.protectedSenders, store.protectContacts, Contacts.hasPermission(ctx)
            )
        }
    }
}
