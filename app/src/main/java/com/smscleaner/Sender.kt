package com.smscleaner

/**
 * Helpers for sender addresses. Indian business senders look like "AX-HDFCBK-S":
 * a two-letter operator/circle prefix, the brand, and an optional one-letter type suffix.
 * We treat all variants of one brand as the same sender.
 */
object Sender {
    private val CORE = Regex("^[A-Za-z]{2}-(.+?)(?:-[A-Za-z])?$")
    private val SUFFIX = Regex("^[A-Za-z]{2}-.+-([A-Za-z])$")

    fun isNumeric(address: String): Boolean = address.none { it.isLetter() }

    fun core(address: String): String {
        val a = address.trim()
        val m = CORE.find(a)
        return (m?.groupValues?.get(1) ?: a).uppercase()
    }

    /** P = promotional, T = transactional, S = service, G = government (TRAI conventions). */
    fun suffix(address: String): Char? =
        SUFFIX.find(address.trim())?.groupValues?.get(1)?.uppercase()?.firstOrNull()

    /** Stable grouping key: last 10 digits for phone numbers, brand name for business senders. */
    fun key(address: String): String {
        val a = address.trim()
        if (a.isEmpty()) return "unknown"
        return if (isNumeric(a)) a.filter { it.isDigit() }.takeLast(10).ifEmpty { a } else core(a)
    }

    fun displayName(address: String, contacts: Map<String, String>): String {
        val a = address.trim()
        if (a.isEmpty()) return "Unknown"
        return if (isNumeric(a)) contacts[key(a)] ?: a else core(a)
    }
}

object Classifier {
    private val OTP_WORDS = Regex(
        "(?i)\\b(otp|one[- ]?time (password|pin)|verification code|passcode|security code|login code|auth(entication)? code)\\b"
    )
    private val CODE = Regex("\\b\\d{4,8}\\b")
    private val BANK = Regex(
        "(?i)\\b(a/c|acct|debited|credited|upi|txn|neft|imps|rtgs|avl bal|available bal|balance|card ending|withdrawn|spent)\\b|\\b(rs\\.?|inr)\\s?\\d"
    )
    private val DELIVERY = Regex(
        "(?i)\\b(delivered|out for delivery|shipped|dispatched|your order|tracking|awb|courier|arriving|parcel|package)\\b"
    )
    private val OFFERS = Regex(
        "(?i)(\\d+%\\s?off|\\boffers?\\b|\\bdiscount|\\bsale\\b|cashback|coupon|voucher|\\bdeals?\\b|limited time|exclusive|free gift|apply now|pre-?approved|\\bloan\\b|\\bt&c|unsubscribe|stop to)"
    )

    fun classify(address: String, body: String): Category {
        if (OTP_WORDS.containsMatchIn(body) && CODE.containsMatchIn(body)) return Category.OTP
        if (Sender.isNumeric(address)) return Category.PERSONAL
        if (Sender.suffix(address) == 'P') return Category.OFFERS
        if (BANK.containsMatchIn(body)) return Category.BANK
        if (DELIVERY.containsMatchIn(body)) return Category.DELIVERY
        if (OFFERS.containsMatchIn(body)) return Category.OFFERS
        return Category.OTHER
    }
}
