package com.smscleaner

import org.json.JSONObject
import java.util.UUID

enum class SenderMode { ANY, EXACT, PREFIX, CONTAINS, REGEX }
enum class ReadState { ANY, READ, UNREAD }
enum class Box { INBOX, SENT, ALL }

const val BROAD_RULE_MSG =
    "Add at least one of: a sender, message keywords, or an age in days. " +
        "A rule that matches every message is not allowed."

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New rule",
    val enabled: Boolean = true,
    val senderMode: SenderMode = SenderMode.ANY,
    val sender: String = "",
    val keywords: String = "",        // comma separated; any one may match
    val olderThanDays: Int = 0,       // 0 = ignore age
    val readState: ReadState = ReadState.ANY,
    val box: Box = Box.INBOX,
) {
    /** A rule must narrow things down somehow, otherwise it would delete everything. */
    fun isSafe(): Boolean =
        (senderMode != SenderMode.ANY && sender.isNotBlank()) ||
            keywords.isNotBlank() ||
            olderThanDays > 0

    fun keywordList(): List<String> =
        keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun summary(): String {
        val parts = mutableListOf<String>()
        if (senderMode != SenderMode.ANY && sender.isNotBlank()) {
            parts.add("sender ${senderMode.name.lowercase()} \"$sender\"")
        }
        if (keywords.isNotBlank()) parts.add("text has: $keywords")
        if (olderThanDays > 0) parts.add("older than $olderThanDays days")
        if (readState != ReadState.ANY) parts.add(readState.name.lowercase())
        parts.add(box.name.lowercase())
        return parts.joinToString(", ")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("senderMode", senderMode.name)
        put("sender", sender)
        put("keywords", keywords)
        put("olderThanDays", olderThanDays)
        put("readState", readState.name)
        put("box", box.name)
    }

    companion object {
        fun fromJson(o: JSONObject): Rule = Rule(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Rule"),
            enabled = o.optBoolean("enabled", true),
            senderMode = enumOr(o.optString("senderMode"), SenderMode.ANY),
            sender = o.optString("sender", ""),
            keywords = o.optString("keywords", ""),
            olderThanDays = o.optInt("olderThanDays", 0),
            readState = enumOr(o.optString("readState"), ReadState.ANY),
            box = enumOr(o.optString("box"), Box.INBOX),
        )

        private inline fun <reified T : Enum<T>> enumOr(s: String, default: T): T =
            try {
                enumValueOf<T>(s)
            } catch (e: Exception) {
                default
            }
    }
}
