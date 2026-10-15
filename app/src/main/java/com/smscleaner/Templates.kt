package com.smscleaner

data class Template(val title: String, val description: String, val rule: Rule)

object Templates {
    val all: List<Template> = listOf(
        Template(
            "Old OTPs", "OTPs older than 1 day",
            Rule(name = "Old OTPs", category = Category.OTP, olderThanDays = 1)
        ),
        Template(
            "Stale offers", "Offers and promos older than 7 days",
            Rule(name = "Old offers", category = Category.OFFERS, olderThanDays = 7)
        ),
        Template(
            "Delivery updates", "Order and delivery messages older than 30 days",
            Rule(name = "Old delivery updates", category = Category.DELIVERY, olderThanDays = 30)
        ),
        Template(
            "Old bank alerts", "Bank and payment alerts older than 180 days",
            Rule(name = "Old bank alerts", category = Category.BANK, olderThanDays = 180)
        ),
        Template(
            "Read company messages", "Already-read company messages older than 60 days",
            Rule(
                name = "Read company messages", category = Category.OTHER,
                readState = ReadState.READ, olderThanDays = 60
            )
        ),
    )
}
