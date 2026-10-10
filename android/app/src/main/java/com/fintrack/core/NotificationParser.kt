package com.fintrack.core

import java.time.Instant
import java.time.ZoneId

/**
 * Rule-based parser for payment notifications from UPI apps (GPay, PhonePe, Paytm, BHIM, CRED) and from
 * merchant apps' own "you paid ₹X" notifications (Swiggy, Zomato, Uber, Rapido, ...).
 * Covers payments that never produce a bank SMS (small UPI payments, UPI Lite, wallets, in-app UPI).
 */
object NotificationParser {

    /** [senderKey] matches [SenderClassifier] keys so duplicate matching can recognize the brand. */
    data class App(val name: String, val packageName: String, val senderKey: String, val type: SourceType)

    val APPS = listOf(
        App("Google Pay", "com.google.android.apps.nbu.paisa.user", "GPAY", SourceType.UPI_APP),
        App("PhonePe", "com.phonepe.app", "PHONEPE", SourceType.UPI_APP),
        App("Paytm", "net.one97.paytm", "PAYTM", SourceType.UPI_APP),
        App("BHIM", "in.org.npci.upiapp", "BHIM", SourceType.UPI_APP),
        App("CRED", "com.dreamplug.androidapp", "CRED", SourceType.UPI_APP),
        App("Swiggy", "in.swiggy.android", "SWIGGY", SourceType.MERCHANT_APP),
        App("Zomato", "com.application.zomato", "ZOMATO", SourceType.MERCHANT_APP),
        App("Blinkit", "com.grofers.customerapp", "BLINKT", SourceType.MERCHANT_APP),
        App("Zepto", "com.zeptoconsumerapp", "ZEPTO", SourceType.MERCHANT_APP),
        App("Amazon", "in.amazon.mShop.android.shopping", "AMAZON", SourceType.MERCHANT_APP),
        App("Flipkart", "com.flipkart.android", "FLPKRT", SourceType.MERCHANT_APP),
        App("BigBasket", "com.bigbasket.mobileapp", "BIGBSK", SourceType.MERCHANT_APP),
        App("Myntra", "com.myntra.android", "MYNTRA", SourceType.MERCHANT_APP),
        App("Uber", "com.ubercab", "UBER", SourceType.MERCHANT_APP),
        App("Rapido", "com.rapido.passenger", "RAPIDO", SourceType.MERCHANT_APP),
        App("Ola", "com.olacabs.customer", "OLACAB", SourceType.MERCHANT_APP),
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

    /**
     * Merchant apps send many notifications (order updates, offers); only ones that say money moved count.
     * "Payment received" from Swiggy means *you* paid, so merchant apps use their own direction rules.
     */
    private val MERCHANT_PAID = Regex(
        """(?<![a-z])(paid|you paid|payment of|payment (?:successful|done|received|completed|confirmed)|charged|""" +
            """debited|amount paid|total paid|trip fare|ride fare|fare|order total|bill total)(?![a-z])"""
    )
    /** Ride apps also report the amount at the end of a trip, often before you pay the captain. */
    private val RIDE_DONE = Regex(
        """(?<![a-z])(ride completed|trip completed|ride ended|trip ended|thanks for riding|thank you for riding|""" +
            """reached your destination|you have reached|dropped|pay (?:₹|rs)|amount to (?:be )?paid|amount payable|""" +
            """total fare|trip total|ride total)(?![a-z])"""
    )
    private val RIDE_APPS = setOf("UBER", "RAPIDO", "OLACAB")

    private val MERCHANT_SKIP = Regex("""(?<![a-z])(cancelled|canceled|estimate|estimated|fare estimate)(?![a-z])""")
    private val MERCHANT_REFUND = Regex("""(?<![a-z])(refund|refunded|credited back|reversed)(?![a-z])""")

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

        val direction = when (app.type) {
            SourceType.MERCHANT_APP -> merchantDirection(lower)
                ?: if (app.senderKey in RIDE_APPS && RIDE_DONE.containsMatchIn(lower) &&
                    !MERCHANT_SKIP.containsMatchIn(lower)) Direction.DEBIT else null
            else -> direction(lower)
        } ?: return null
        val amountPaise = SmsParser.extractAmountPaise(combined) ?: return null
        val postedDate = Instant.ofEpochMilli(postedAtMillis).atZone(zone).toLocalDate()
        val bodyDate = SmsParser.extractDate(combined, postedDate)

        return ParsedSms(
            senderKey = app.senderKey,
            sourceType = app.type,
            direction = direction,
            amountPaise = amountPaise,
            txnDate = bodyDate ?: postedDate,
            dateFromBody = bodyDate != null,
            maskedBody = Masker.mask(combined),
        )
    }

    private fun merchantDirection(lower: String): Direction? = when {
        MERCHANT_REFUND.containsMatchIn(lower) && MERCHANT_SKIP.containsMatchIn(lower) -> Direction.CREDIT
        MERCHANT_SKIP.containsMatchIn(lower) -> null
        MERCHANT_REFUND.containsMatchIn(lower) -> Direction.CREDIT
        MERCHANT_PAID.containsMatchIn(lower) -> Direction.DEBIT
        else -> null
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
