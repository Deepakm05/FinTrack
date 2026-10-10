package com.fintrack.core

import java.time.Instant
import java.time.ZoneId

/**
 * Rule-based parser for payment-app notifications (GPay, PhonePe, Paytm, BHIM, CRED).
 * Covers payments that never produce a bank SMS (small UPI payments, UPI Lite, wallets).
 */
object NotificationParser {

    data class App(val name: String, val packageName: String, val senderKey: String)

    val APPS = listOf(
        App("Google Pay", "com.google.android.apps.nbu.paisa.user", "GPAY"),
        App("PhonePe", "com.phonepe.app", "PHONEPE"),
        App("Paytm", "net.one97.paytm", "PAYTM"),
        App("BHIM", "in.org.npci.upiapp", "BHIM"),
        App("CRED", "com.dreamplug.androidapp", "CRED"),
    )

    fun appFor(packageName: String): App? = APPS.firstOrNull { it.packageName == packageName }

    /** Never a completed payment of yours: requests, failures, wallet / UPI Lite top-ups, mandates. */
    private val SKIP = Regex(
        """(?<![a-z])(requested|requesting|has requested|payment request|collect request|""" +
            """request(?:s|ed)? (?:money|₹|rs)|failed|declined|unsuccessful|pending|processing|""" +
            """added to|add money|top-?up|autopay set ?up|mandate)(?![a-z])"""
    )

    /** Promotions are skipped unless the same notification also reports a real payment. */
    private val PROMO = Regex(
        """(?<![a-z])(cashback|rewards?|scratch ?card|offers?|coupons?|vouchers?|you won|win|congratulations|""" +
            """recharge now|pay now)(?![a-z])"""
    )
    private val PAYMENT = Regex(
        """(?<![a-z])(paid|sent|debited|payment of|payment successful|transferred|received from)(?![a-z])"""
    )

    /** Reminders, unless the text also says something was actually paid. */
    private val REMINDER = Regex("""(?<![a-z])(is due|due on|due date|due by|due today|due tomorrow|reminder|expires|expiring)(?![a-z])""")
    private val DONE = Regex("""(?<![a-z])(paid|successful|successfully|debited|credited|received)(?![a-z])""")

    private val CREDIT = Regex("""(?<![a-z])(paid you|sent you|credited|refund|refunded|received(?!\s+by))(?![a-z])""")
    private val DEBIT = Regex(
        """(?<![a-z])(paid|sent|debited|payment of|payment successful|payment done|transferred|spent|""" +
            """money sent|received by)(?![a-z])"""
    )

    fun parse(
        packageName: String,
        title: String,
        text: String,
        postedAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ParsedSms? {
        val app = appFor(packageName) ?: return null
        val combined = listOf(title, text).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .joinToString(" — ").replace(Regex("""\s+"""), " ")
        if (combined.isEmpty()) return null
        val lower = combined.lowercase()

        if (SKIP.containsMatchIn(lower)) return null
        if (PROMO.containsMatchIn(lower) && !PAYMENT.containsMatchIn(lower)) return null
        if (REMINDER.containsMatchIn(lower) && !DONE.containsMatchIn(lower)) return null

        val direction = direction(lower) ?: return null
        val amountPaise = SmsParser.extractAmountPaise(combined) ?: return null
        val postedDate = Instant.ofEpochMilli(postedAtMillis).atZone(zone).toLocalDate()
        val bodyDate = SmsParser.extractDate(combined, postedDate)

        return ParsedSms(
            senderKey = app.senderKey,
            sourceType = SourceType.UPI_APP,
            direction = direction,
            amountPaise = amountPaise,
            txnDate = bodyDate ?: postedDate,
            dateFromBody = bodyDate != null,
            maskedBody = Masker.mask(combined),
        )
    }

    /** "Rahul paid you ₹500" is money in even though it contains "paid": the earlier, longer match wins. */
    private fun direction(lower: String): Direction? {
        val c = CREDIT.find(lower)?.range?.first
        val d = DEBIT.find(lower)?.range?.first
        return when {
            c == null && d == null -> null
            c == null -> Direction.DEBIT
            d == null -> Direction.CREDIT
            d < c -> Direction.DEBIT
            else -> Direction.CREDIT
        }
    }
}
