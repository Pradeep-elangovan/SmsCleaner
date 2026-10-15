package com.smscleaner

object Conversations {
    /** [all] must be newest first. Returns conversations sorted by latest message. */
    fun build(all: List<Sms>, contacts: Map<String, String>): List<Conversation> {
        val groups = LinkedHashMap<String, MutableList<Sms>>()
        for (m in all) groups.getOrPut(Sender.key(m.address)) { ArrayList() }.add(m)
        return groups.map { (key, msgs) ->
            val addr = msgs.first().address
            val lastIn = msgs.firstOrNull { it.type == 1 } ?: msgs.first()
            Conversation(
                key = key,
                title = Sender.displayName(addr, contacts),
                address = addr,
                messages = msgs,
                unread = msgs.count { it.type == 1 && !it.read },
                category = lastIn.category,
                isContact = Sender.isNumeric(addr) && contacts.containsKey(key),
            )
        }.sortedByDescending { it.last.date }
    }
}
